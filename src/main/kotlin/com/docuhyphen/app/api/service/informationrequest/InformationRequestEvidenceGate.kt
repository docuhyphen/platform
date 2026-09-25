package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestEvidenceGate @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val exchangeRepository: ExchangeRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val authorizationService: AuthorizationService,
    private val entitlementGuard: InformationRequestEntitlementGuard,
    private val executionGrantService: InformationRequestExecutionGrantService,
    private val lockService: InformationRequestSubmissionLockService,
)
{
    fun lock(requestId: UUID): LockedInformationRequest
    {
        val exchange = lockParentExchangeOf(requestId, requestRepository, exchangeRepository)
        val request = requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        return LockedInformationRequest(exchange, request)
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

    fun requireContinuationEntitlement(locked: LockedInformationRequest)
    {
        val grant = executionGrantService.findForRequest(locked.request.id)
        if (grant == null)
        {
            entitlementGuard.requireRequestMutation(locked.exchange)
            return
        }
        entitlementGuard.requireNotOperationallySuspended(locked.exchange)
        if (grant.revokedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED,
                "This request's execution grant has been revoked",
            )
        }
    }

    fun authorize(access: RequestAccessContext, action: Action, requirementId: UUID) =
        authorizeAny(access, listOf(action), requirementId)

    fun authorizeAny(access: RequestAccessContext, actions: List<Action>, requirementId: UUID)
    {
        if (actions.none { permits(access, it, requirementId) })
        {
            throw ForbiddenException("Access denied to Information Request evidence")
        }
    }

    fun permits(access: RequestAccessContext, action: Action, requirementId: UUID): Boolean =
        authorizationService.authorize(
            access.principal,
            action,
            ResourceRef.informationRequestRequirement(requirementId),
            access.authorization,
        ) !is Decision.Deny
}
