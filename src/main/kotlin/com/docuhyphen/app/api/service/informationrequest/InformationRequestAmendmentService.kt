package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.AmendInformationRequestCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAmendmentResult
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
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
)
{
    @Transactional
    fun amend(command: AmendInformationRequestCommand): InformationRequestAmendmentResult
    {
        val locked = gate.lock(command.requestId)
        val receipt = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = AMEND_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(
                    AMEND_OPERATION,
                    command.requestId,
                    command.targetTemplateVersionId?.toString().orEmpty(),
                    command.configuration?.toString().orEmpty(),
                    command.reasonCode?.trim().orEmpty(),
                ).joinToString("|"),
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
                    requestETag = requireNotNull(decision.result.etag) { "Amendment receipt did not record a request ETag" },
                )
            }
        }
    }

    private fun amendLocked(locked: LockedInformationRequest, command: AmendInformationRequestCommand): InformationRequestAmendmentResult
    {
        val request = locked.request
        gate.requireMutation(locked, InformationRequestMutation.AMEND)
        gate.requireContinuationEntitlement(locked)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_AMEND), request.id)
        command.precondition.requireSatisfiedBy(InformationRequestETag.aggregateOf(request))

        val from = targetResolver.current(request)
        val target = targetResolver.resolve(request, command)
        capabilityGate.requireInstalledCapabilities(target.id)
        val fromProjection = projectionLoader.loadVersion(from)
        val plan = InformationRequestAmendmentClassifier.classify(fromProjection, projectionLoader.loadVersion(target))
        guard.requireAmendable(request, fromProjection, plan)

        val now = Timestamp.from(clock.instant())
        request.templateVersionId = target.id
        request.aggregateRevision += 1
        request.responseRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        requestRepository.flushPendingChanges()
        materializer.advance(request, from.id, FieldsAccessContext(command.access.principal, command.access.authorization))
        val amendment = recorder.record(request, from.id, plan, command.reasonCode, command.access.principal, now)

        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.AMEND,
                actor = command.access.principal,
                reasonCode = command.reasonCode,
                idempotencyKey = "information_request.amend|${request.id}|${command.idempotencyKey}",
                details = mapOf(
                    "amendmentId" to amendment.amendment.id.toString(),
                    "amendmentNumber" to amendment.amendment.amendmentNumber.toString(),
                    "fromTemplateVersionId" to from.id.toString(),
                    "toTemplateVersionId" to target.id.toString(),
                    "changeCount" to amendment.changes.size.toString(),
                    "pendingNoticeCount" to amendment.notices.size.toString(),
                ),
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
