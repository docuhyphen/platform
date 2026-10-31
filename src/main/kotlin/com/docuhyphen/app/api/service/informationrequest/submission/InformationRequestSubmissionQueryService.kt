package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestScheduledContinuation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.submission.*
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationPolicyEvaluator
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewOpeningService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestSubmissionQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val exchangeLookup: ExchangeLookupService,
    private val packageReader: InformationRequestSubmissionPackageReader,
    private val contentCollector: InformationRequestSubmissionContentCollector,
    private val stages: InformationRequestSubmissionStages,
    private val readinessEvaluator: InformationRequestSubmissionReadinessEvaluator,
    private val lockService: InformationRequestSubmissionLockService,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
    private val gate: InformationRequestMutationGate,
    private val evidenceQueries: InformationRequestEvidenceQueryService,
    private val reviewOpening: InformationRequestReviewOpeningService,
)
{
    fun packages(requestId: UUID, access: RequestAccessContext): List<InformationRequestReadableSubmissionPackage>
    {
        val request = queryService.findById(requestId, access)
        return readable(request, packageReader.views(requestId), access)
    }

    fun packageDetail(
        requestId: UUID,
        packageId: UUID,
        access: RequestAccessContext
    ): InformationRequestReadableSubmissionPackage
    {
        val request = queryService.findById(requestId, access)
        return readable(request, listOf(packageReader.view(requestId, packageId)), access).single()
    }

    fun readable(
        view: InformationRequestSubmissionPackageView,
        access: RequestAccessContext
    ): InformationRequestReadableSubmissionPackage =
        readable(
            queryService.findById(view.submissionPackage.informationRequestId, access),
            listOf(view),
            access
        ).single()

    fun preview(requestId: UUID, stageKey: String?, access: RequestAccessContext): InformationRequestSubmissionPreview
    {
        val request = queryService.findById(requestId, access)
        val version = stages.versionOf(request)
        val stageOrder = stages.stageOrder(request)
        val activePackages = lockService.activePackages(request.id)
        val submittedStages = activePackages.map { it.stageKey }.toSet()
        val correctedPackageIds = lockService.openCorrections(request.id).map { it.packageId }.toSet()
        val correctedStages = activePackages.filter { it.id in correctedPackageIds }.map { it.stageKey }.toSet()
        val scope = stageKey?.trim()?.ifBlank { null }
            ?: stageOrder.firstOrNull { it in correctedStages }
            ?: stageOrder.firstOrNull { it !in submittedStages }
            ?: stageOrder.firstOrNull()
        val content = contentCollector.collect(request, scope)
        val assessment = readinessEvaluator.assess(content, access)
        val correctionOpen = scope in correctedStages
        val scopeOpen = correctionOpen || (scope !in submittedStages && (scope == null || null !in submittedStages))
        val orderMet = version.submissionStageOrdering != InformationRequestSubmissionStageOrdering.SEQUENTIAL ||
                scope == null ||
                stageOrder.takeWhile { it != scope }.all { it in submittedStages }
        val scopeItems = content.items.filter {
            it.requirementType != InformationRequestRequirementType.RESPONSE_ATTESTATION &&
                    assessment.stateByRequirement[it.requirement.id] != InformationRequestCompletenessItemState.HIDDEN
        }
        val visibleScopeItems = scopeItems.filter {
            gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it.requirement.id)
        }
        val accepting = acceptsSubmission(request)

        return InformationRequestSubmissionPreview(
            requestId = request.id,
            stageKey = scope,
            submissionMode = version.submissionMode,
            submissionStageOrdering = version.submissionStageOrdering,
            submissionETag = InformationRequestETag.submissionOf(scope, content.contentHash),
            readiness = assessment.readiness,
            attestations = attestationStandings(
                request,
                content,
                assessment.attestations.filterKeys { assessment.stateByRequirement[it] != InformationRequestCompletenessItemState.HIDDEN },
                access,
                scopeOpen && acceptsSubmission(request, InformationRequestContinuationAction.ATTEST)
            ),
            stages = if (version.submissionMode == InformationRequestSubmissionMode.STAGED)
                stageOrder.map { stage ->
                    InformationRequestSubmissionStageStanding(
                        stage,
                        activePackages.lastOrNull { it.stageKey == stage },
                        correctionOpen = stage in correctedStages,
                    )
                }
            else emptyList(),
            canSubmit = assessment.readiness.ready && scopeOpen && orderMet && accepting &&
                    gate.permitsRequest(access, Action.INFORMATION_REQUEST_SUBMIT, request.id),
            correctionOpen = correctionOpen,
            packages = readable(request, packageReader.views(request.id), access),
            scopeItems = visibleScopeItems.map { item ->
                InformationRequestSubmissionScopeItem(
                    requirementId = item.requirement.id,
                    templateBindingId = item.binding.id,
                    requirementKey = item.requirementKey,
                    requirementType = item.requirementType,
                    prompt = item.binding.prompt,
                    occurrencePath = item.requirement.occurrencePath,
                    disposition = item.response?.disposition ?: InformationRequestResponseDisposition.NOT_ANSWERED,
                    narrative = item.response?.narrative,
                    fieldValue = item.fieldValueRevisionId?.let(fieldValueRevisions::valueOf),
                    evidenceFileCount = item.evidence?.members?.size ?: 0,
                )
            },
            undisclosedScopeItemCount = scopeItems.size - visibleScopeItems.size,
        )
    }

    private fun attestationStandings(
        request: InformationRequest,
        content: InformationRequestSubmissionContent,
        evaluations: Map<UUID, InformationRequestAttestationRequirementEvaluation>,
        access: RequestAccessContext,
        attestable: Boolean,
    ): List<InformationRequestAttestationStanding> =
        content.items
            .filter { it.requirementType == InformationRequestRequirementType.RESPONSE_ATTESTATION }
            .filter { gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it.requirement.id) }
            .mapNotNull { item ->
                val evaluated = evaluations[item.requirement.id] ?: return@mapNotNull null
                val roles = evaluated.policy.requiredRoles.toSet()
                val acting = requirementContext.actingPartiesFor(request, roles, access.principal, item.requirement.id)
                val unmetByParty = acting.map {
                    InformationRequestAttestationPolicyEvaluator.unmetRolesBefore(
                        evaluated.policy,
                        it.role,
                        evaluated.evaluation
                    )
                }
                val callerCanAttest = attestable && acting.isNotEmpty() &&
                        gate.permitsRequirement(
                            access,
                            Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST,
                            item.requirement.id
                        )
                InformationRequestAttestationStanding(
                    requirementId = item.requirement.id,
                    requirementKey = item.requirementKey,
                    prompt = item.binding.prompt,
                    evaluated = evaluated,
                    callerCanAttest = callerCanAttest,
                    waitingForRoles = if (callerCanAttest && unmetByParty.none { it.isEmpty() })
                        unmetByParty.flatten().distinct()
                    else
                        emptyList(),
                    attestations = (evaluated.evaluation.counted + evaluated.evaluation.refusals)
                        .distinctBy { it.id }
                        .sortedBy { it.sequenceNumber },
                    attestingParties = if (callerCanAttest) acting else emptyList(),
                )
            }

    private fun acceptsSubmission(
        request: InformationRequest,
        action: InformationRequestContinuationAction = InformationRequestContinuationAction.SUBMIT,
    ): Boolean
    {
        val exchange = exchangeLookup.findExchange(request.exchangeId) ?: return false
        return !exchange.isDeleted &&
                exchange.status == ExchangeStatus.ACCEPTED_STARTED &&
                request.state in ACTIVE_RESPONSE_STATES &&
                gate.scheduledContinuationOf(LockedInformationRequest(exchange, request), action) ==
                InformationRequestScheduledContinuation.PROCEEDS
    }

    private fun readable(
        request: InformationRequest,
        views: List<InformationRequestSubmissionPackageView>,
        access: RequestAccessContext,
    ): List<InformationRequestReadableSubmissionPackage>
    {
        val currentIds = lockService.activePackages(request.id).map { it.id }.toSet()
        val accepting = acceptsSubmission(request)
        val visible = views.flatMap { view -> view.items.map { it.informationRequestRequirementId } }
            .toSet()
            .filter { gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it) }
            .toSet()
        val fieldValues = views
            .flatMap { view -> view.items.filter { it.informationRequestRequirementId in visible } }
            .mapNotNull { it.fieldValueRevisionId }
            .distinct()
            .mapNotNull { revisionId -> fieldValueRevisions.valueOf(revisionId)?.let { revisionId to it } }
            .toMap()
        val visibleItemIds = views
            .flatMap { view -> view.items.filter { it.informationRequestRequirementId in visible } }
            .map { it.id }
            .toSet()
        val evidenceVersions = evidenceQueries.versionRecords(
            views.flatMap { it.evidence }.filter { it.itemId in visibleItemIds }.map { it.evidenceVersionId },
        )
        return views.map { view ->
            val submission = view.submissionPackage
            val current = submission.id in currentIds
            InformationRequestReadableSubmissionPackage(
                view = view,
                visibleRequirementIds = visible,
                fieldValues = fieldValues,
                evidenceVersions = evidenceVersions,
                current = current,
                withdrawable = current && accepting &&
                        (gate.permitsRequest(access, Action.INFORMATION_REQUEST_SUBMIT, request.id) ||
                                gate.permitsRequest(access, Action.INFORMATION_REQUEST_MANAGE_PARTIES, request.id)) &&
                        lockService.laterSubmittedStages(request, submission.stageKey).isEmpty() &&
                        reviewOpening.withdrawalRefusal(submission) == null,
            )
        }
    }

    private companion object
    {
        val ACTIVE_RESPONSE_STATES = setOf(
            InformationRequestState.ISSUED,
            InformationRequestState.IN_PROGRESS,
        )
    }
}
