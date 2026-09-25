package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionMode
import com.docuhyphen.app.api.model.entity.InformationRequestSubmissionStageOrdering
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReadableSubmissionPackage
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionContent
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionPackageView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionPreview
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSubmissionStageStanding
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestSubmissionQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val exchangeRepository: ExchangeRepository,
    private val packageReader: InformationRequestSubmissionPackageReader,
    private val contentCollector: InformationRequestSubmissionContentCollector,
    private val stages: InformationRequestSubmissionStages,
    private val readinessEvaluator: InformationRequestSubmissionReadinessEvaluator,
    private val lockService: InformationRequestSubmissionLockService,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
    private val gate: InformationRequestMutationGate,
)
{
    fun packages(requestId: UUID, access: RequestAccessContext): List<InformationRequestReadableSubmissionPackage>
    {
        queryService.findById(requestId, access)
        return readable(packageReader.views(requestId), access)
    }

    fun packageDetail(requestId: UUID, packageId: UUID, access: RequestAccessContext): InformationRequestReadableSubmissionPackage
    {
        queryService.findById(requestId, access)
        return readable(listOf(packageReader.view(requestId, packageId)), access).single()
    }

    fun readable(view: InformationRequestSubmissionPackageView, access: RequestAccessContext): InformationRequestReadableSubmissionPackage =
        readable(listOf(view), access).single()

    fun preview(requestId: UUID, stageKey: String?, access: RequestAccessContext): InformationRequestSubmissionPreview
    {
        val request = queryService.findById(requestId, access)
        val version = stages.versionOf(request)
        val stageOrder = stages.stageOrder(request)
        val activePackages = lockService.activePackages(request.id)
        val submittedStages = activePackages.map { it.stageKey }.toSet()
        val scope = stageKey?.trim()?.ifBlank { null }
            ?: stageOrder.firstOrNull { it !in submittedStages }
            ?: stageOrder.firstOrNull()
        val content = contentCollector.collect(request, scope)
        val assessment = readinessEvaluator.assess(content, access)
        val scopeOpen = scope !in submittedStages && (scope == null || null !in submittedStages)
        val orderMet = version.submissionStageOrdering != InformationRequestSubmissionStageOrdering.SEQUENTIAL ||
            scope == null ||
            stageOrder.takeWhile { it != scope }.all { it in submittedStages }
        val accepting = acceptsSubmission(request)

        return InformationRequestSubmissionPreview(
            requestId = request.id,
            stageKey = scope,
            submissionMode = version.submissionMode,
            submissionStageOrdering = version.submissionStageOrdering,
            submissionETag = InformationRequestETag.submissionOf(scope, content.contentHash),
            readiness = assessment.readiness,
            attestations = attestationStandings(request, content, assessment.attestations, access, scopeOpen && accepting),
            stages = if (version.submissionMode == InformationRequestSubmissionMode.STAGED)
                stageOrder.map { stage -> InformationRequestSubmissionStageStanding(stage, activePackages.lastOrNull { it.stageKey == stage }) }
            else emptyList(),
            canSubmit = assessment.readiness.ready && scopeOpen && orderMet && accepting &&
                gate.permitsRequest(access, Action.INFORMATION_REQUEST_SUBMIT, request.id),
            packages = readable(packageReader.views(request.id), access),
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
                InformationRequestAttestationStanding(
                    requirementId = item.requirement.id,
                    requirementKey = item.requirementKey,
                    prompt = item.binding.prompt,
                    evaluated = evaluated,
                    callerCanAttest = attestable &&
                        requirementContext.actingPartiesFor(request, roles, access.principal, item.requirement.id).isNotEmpty() &&
                        gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, item.requirement.id),
                    attestations = (evaluated.evaluation.counted + evaluated.evaluation.refusals)
                        .distinctBy { it.id }
                        .sortedBy { it.sequenceNumber },
                )
            }

    private fun acceptsSubmission(request: InformationRequest): Boolean
    {
        val exchange = exchangeRepository.findById(request.exchangeId) ?: return false
        return !exchange.isDeleted &&
            exchange.status == ExchangeStatus.ACCEPTED_STARTED &&
            request.state in ACTIVE_RESPONSE_STATES
    }

    private fun readable(
        views: List<InformationRequestSubmissionPackageView>,
        access: RequestAccessContext,
    ): List<InformationRequestReadableSubmissionPackage>
    {
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
        return views.map { InformationRequestReadableSubmissionPackage(it, visible, fieldValues) }
    }

    private companion object
    {
        val ACTIVE_RESPONSE_STATES = setOf(
            InformationRequestState.ISSUED,
            InformationRequestState.IN_PROGRESS,
            InformationRequestState.CHANGES_REQUESTED,
        )
    }
}
