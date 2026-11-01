package com.docuhyphen.app.api.service.informationrequest.amendment

import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConfigurationRequest
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.fields.FieldsAccessContext
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.amendment.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.amendment.InformationRequestAmendmentResult
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.capability.InformationRequestTemplateCapabilityGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestReasonCodes
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestFieldAudienceGuard
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestHiddenResponsePolicyEnforcer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateMaterializer
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateProjectionLoader
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestAmendmentService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val requestRepository: InformationRequestRepository,
    private val targetResolver: InformationRequestAmendmentTargetResolver,
    private val projectionLoader: InformationRequestTemplateProjectionLoader,
    private val capabilityGate: InformationRequestTemplateCapabilityGate,
    private val guard: InformationRequestAmendmentGuard,
    private val materializer: InformationRequestTemplateMaterializer,
    private val recorder: InformationRequestAmendmentRecorder,
    private val reader: InformationRequestAmendmentReader,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val hiddenResponses: InformationRequestHiddenResponsePolicyEnforcer,
    private val fieldAudience: InformationRequestFieldAudienceGuard,
)
{
    @Transactional
    fun amend(command: AmendInformationRequestCommand): InformationRequestAmendmentResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = AMEND_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(
                AMEND_OPERATION,
                command.requestId,
                command.targetTemplateVersionId,
                CommandRequestFingerprint.serialized(
                    InformationRequestTemplateConfigurationRequest.serializer(),
                    command.configuration,
                ),
                command.reasonCode?.trim().orEmpty(),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = amendLocked(locked, command)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        resourceType = ResourceType.INFORMATION_REQUEST_AMENDMENT,
                        resourceId = result.amendment.amendment.id,
                        revision = result.request.aggregateRevision,
                        etag = result.requestETag,
                    ),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
            {
                require(decision.result.resourceType == ResourceType.INFORMATION_REQUEST_AMENDMENT) {
                    "Command receipt does not reference an Information Request amendment"
                }
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_VIEW), locked.request.id)
                InformationRequestAmendmentResult(
                    request = locked.request,
                    amendment = reader.view(locked.request.id, decision.result.resourceId),
                    requestETag = InformationRequestETag.aggregateOf(locked.request),
                )
            }
        }
    }

    private fun amendLocked(
        locked: LockedInformationRequest,
        command: AmendInformationRequestCommand
    ): InformationRequestAmendmentResult
    {
        val request = locked.request
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_AMEND), request.id)
        gate.requireMutation(locked, InformationRequestMutation.AMEND)
        gate.requireContinuation(locked, InformationRequestContinuationAction.NON_EXPANDING_AMENDMENT)
        command.precondition.requireSatisfiedBy(InformationRequestETag.aggregateOf(request))

        InformationRequestReasonCodes.requireMachineCode(command.reasonCode)
        val configured = command.configuration != null
        if (configured) gate.requireExpansionEntitlement(locked)
        val from = targetResolver.current(request)
        val target = targetResolver.resolve(request, command)
        capabilityGate.requireInstalledCapabilities(target.id)
        val fromProjection = projectionLoader.loadVersion(from)
        val plan = InformationRequestAmendmentClassifier.classify(fromProjection, projectionLoader.loadVersion(target))
        if (plan.expanding && !configured) gate.requireExpansionEntitlement(locked)
        guard.requireAmendable(request, fromProjection, plan)
        fieldAudience.requireAnswerableByParties(request, target.id)

        val now = Timestamp.from(clock.instant())
        request.templateVersionId = target.id
        request.aggregateRevision += 1
        request.responseRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        requestRepository.flushPendingChanges()
        materializer.advance(
            request,
            from.id,
            FieldsAccessContext(command.access.principal, command.access.authorization)
        )
        hiddenResponses.reconcileAmendedConditions(
            request,
            requirementRepository.findForRequest(request.id),
            request.responseRevision,
            now,
        )
        val amendment = recorder.record(request, from.id, plan, command.reasonCode, command.access.principal, now)

        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.AMEND,
                actor = InformationRequestAuditActor.of(command.access),
                reasonCode = command.reasonCode,
                auditIdempotencyKey = "information_request.amend|${request.id}|${command.idempotencyKey}",
                details = mapOf(
                    "amendmentId" to amendment.amendment.id.toString(),
                    "amendmentNumber" to amendment.amendment.amendmentNumber.toString(),
                    "fromTemplateVersionId" to from.id.toString(),
                    "toTemplateVersionId" to target.id.toString(),
                    "changeCount" to amendment.changes.size.toString(),
                    "pendingNoticeCount" to amendment.notices.size.toString(),
                ) + (if (plan.reviewPlanChanged) mapOf("reviewPlanChanged" to "true") else emptyMap()),
            ),
        )
        return InformationRequestAmendmentResult(
            request = request,
            amendment = amendment,
            requestETag = InformationRequestETag.aggregateOf(request),
        )
    }

    private companion object
    {
        const val AMEND_OPERATION = "amend-information-request"
    }
}
