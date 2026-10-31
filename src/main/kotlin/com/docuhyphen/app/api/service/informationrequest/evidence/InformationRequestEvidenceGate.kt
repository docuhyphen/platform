package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceArtifactView
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestReadAuthorization
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionVisibility
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.parent.lockParentExchangeOf
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestEvidenceGate @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeLookup: ExchangeLookupService,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val authorizationService: AuthorizationService,
    private val continuationGuard: InformationRequestContinuationGuard,
    private val lockService: InformationRequestSubmissionLockService,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val conditionVisibility: InformationRequestConditionVisibility,
)
{
    fun lock(requestId: UUID): LockedInformationRequest
    {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeLookup)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        return LockedInformationRequest(exchange, request)
    }

    fun requireVisibleTarget(access: RequestAccessContext, requestId: UUID) {
        InformationRequestReadAuthorization.requireVisibleTarget(authorizationService, requestId, access)
    }

    fun requireEvidenceOccurrence(request: InformationRequest, requirementId: UUID): InformationRequestRequirement
    {
        val requirement = requirementRepository.findById(requirementId)
            ?.takeIf { it.informationRequestId == request.id && it.sourceTemplateVersionId == request.templateVersionId }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request Requirement not found",
            )
        val requirementType = templateRequirementRepository.findById(requirement.sourceTemplateRequirementId)
            ?.requirementType
        if (requirementType != InformationRequestRequirementType.DOCUMENT)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_NOT_COLLECTED,
                "This Information Request Requirement does not collect evidence",
            )
        }
        val activeOccurrencePaths = occurrenceRepository.findForRequest(request.id).map { it.occurrencePath }.toSet()
        if (!InformationRequestOccurrencePath.isActiveOccurrence(requirement.occurrencePath, activeOccurrencePaths))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.GROUP_OCCURRENCE_REMOVED,
                "This Information Request group occurrence has been removed",
            )
        }
        return requirement
    }

    fun requireEvidenceOpen(locked: LockedInformationRequest, requirement: InformationRequestRequirement)
    {
        lockService.requireUnlocked(locked.request.id, listOf(requirement.id))
    }

    fun requireArtifactOpen(
        locked: LockedInformationRequest,
        requirement: InformationRequestRequirement,
        artifactId: UUID
    )
    {
        requireArtifactOpen(locked.request, requirement, artifactId)
    }

    fun requireArtifactOpen(request: InformationRequest, requirement: InformationRequestRequirement, artifactId: UUID) {
        lockService.requireEvidenceArtifactOpen(request.id, requirement.id, artifactId)
    }

    fun requireShown(request: InformationRequest, requirement: InformationRequestRequirement) {
        conditionVisibility.requireShown(request, listOf(requirement))
    }

    fun shows(request: InformationRequest, requirement: InformationRequestRequirement): Boolean =
        conditionVisibility.shows(request, requirement)

    fun requireMutationAllowed(locked: LockedInformationRequest)
    {
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = locked.exchange.status,
                deleted = locked.exchange.isDeleted,
                lockedForUpdate = true,
            ),
            locked.request.state,
            InformationRequestMutation.ADMINISTER_EVIDENCE,
        )
        if (decision is InformationRequestPolicyDecision.Deny)
        {
            throw InformationRequestLifecycleException(
                decision.reasonCode,
                "Information Request evidence cannot change in the request's current state",
            )
        }
    }

    fun requireContinuation(locked: LockedInformationRequest, action: InformationRequestContinuationAction) {
        continuationGuard.requireContinuation(locked, action)
    }

    fun requireRequestView(access: RequestAccessContext, requestId: UUID) {
        InformationRequestReadAuthorization.requireView(authorizationService, requestId, access)
    }

    fun requireEvidenceTarget(access: RequestAccessContext, requirementId: UUID, actions: List<Action>) =
        authorizeAny(
            access,
            listOf(Action.INFORMATION_REQUEST_EVIDENCE_MANAGE, Action.INFORMATION_REQUEST_EVIDENCE_VIEW) + actions,
            requirementId,
        )

    fun authorize(access: RequestAccessContext, action: Action, requirementId: UUID) =
        authorizeAny(access, listOf(action), requirementId)

    fun authorizeAny(access: RequestAccessContext, actions: List<Action>, requirementId: UUID)
    {
        if (actions.none { permits(access, it, requirementId) })
        {
            throw ForbiddenException("Access denied to Information Request evidence")
        }
    }

    fun changeableBy(
        access: RequestAccessContext,
        request: InformationRequest,
        artifact: InformationRequestEvidenceArtifact,
    ): Boolean {
        val provider = PrincipalRef(artifact.createdByPrincipalKind, artifact.createdByPrincipalId)
        if (provider == access.principal) return true
        val requirementId = artifact.informationRequestRequirementId
        val providerParties = requirementContext.contributingPartyIds(request, requirementId, provider)
        return providerParties.isEmpty() ||
                requirementContext.contributingPartyIds(request, requirementId, access.principal)
                    .any { it in providerParties }
    }

    fun requireChangeableBy(
        access: RequestAccessContext,
        request: InformationRequest,
        artifact: InformationRequestEvidenceArtifact,
    ) {
        if (!changeableBy(access, request, artifact)) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EVIDENCE_PROVIDED_BY_ANOTHER_PARTY,
                "Only the party that provided this file can replace, withdraw, or remove it",
            )
        }
    }

    fun forCaller(
        access: RequestAccessContext,
        request: InformationRequest,
        view: InformationRequestEvidenceArtifactView,
    ): InformationRequestEvidenceArtifactView =
        view.copy(
            changeableByCaller = permits(
                access,
                Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW,
                view.artifact.informationRequestRequirementId
            ) &&
                    changeableBy(access, request, view.artifact),
        )

    fun sees(access: RequestAccessContext, artifact: InformationRequestEvidenceArtifact): Boolean =
        artifact.collectionState != InformationRequestEvidenceCollectionState.REMOVED ||
                permits(access, Action.INFORMATION_REQUEST_EVIDENCE_MANAGE, artifact.informationRequestRequirementId)

    fun permits(access: RequestAccessContext, action: Action, requirementId: UUID): Boolean =
        authorizationService.authorize(
            access.principal,
            action,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        ) !is Decision.Deny
}
