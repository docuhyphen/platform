package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestContributorRole
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService

@ApplicationScoped
class InformationRequestRequirementAuthorizationContextProvider @Inject constructor(
    private val requirementRepository: InformationRequestRequirementRepository,
    private val requestRepository: InformationRequestRepository,
    private val revisionRepository: InformationRequestRequirementRevisionRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val delegatedAuthorityFactSource: InformationRequestDelegatedAuthorityFactSource,
    private val parentState: ExchangeLifecycleStateService,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val participantAccountLinkRepository: ParticipantAccountLinkRepository,
    private val principalGroupMemberRepository: PrincipalGroupMemberRepository,
) : ResourceAuthorizationContextProvider
{
    override val supportedKind: ResourceKind = ResourceKind.INFORMATION_REQUEST_REQUIREMENT

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val requirement = requirementRepository.findById(resourceId) ?: return null
        val request = requestRepository.findById(requirement.informationRequestId) ?: return null
        val facts = requirementPolicyFacts(requirement, request) ?: return null
        return resourceContext(request, facts)
    }

    /**
     * Builds the same Requirement policy facts a materialized Requirement would carry, for a
     * Template binding that has not yet been materialized as a runtime Requirement. Callers that
     * must authorize against the authored policy before a first occurrence exists (so there is no
     * persisted Requirement id to resolve) use this instead of [resolve].
     */
    fun authoredContextFor(
        request: InformationRequest,
        binding: InformationRequestTemplateRequirementBinding,
    ): ResourceAuthorizationContext?
    {
        val facts = authoredPolicyFacts(request, binding) ?: return null
        return resourceContext(request, facts)
    }

    private fun resourceContext(
        request: InformationRequest,
        facts: InformationRequestRequirementPolicyFacts,
    ): ResourceAuthorizationContext?
    {
        val owner = request.ownerContext() ?: return null
        return ResourceAuthorizationContext(
            ownerContext = owner,
            isArchived = request.state in terminalStates,
            parentRef = ResourceRef.informationRequest(request.id),
            policyFacts = facts,
        )
    }

    private fun requirementPolicyFacts(
        requirement: InformationRequestRequirement,
        request: InformationRequest,
    ): InformationRequestRequirementPolicyFacts?
    {
        val currentRevision = revisionRepository.findForRequirement(requirement.id)
            .filter { it.effectiveTo == null }
            .maxByOrNull { it.revisionNumber }
            ?: return null
        val binding = bindingRepository.findById(currentRevision.sourceTemplateBindingId)
            ?: return null
        val activeOccurrencePaths = occurrenceRepository.findForRequest(request.id).map { it.occurrencePath }.toSet()
        return buildFacts(
            request = request,
            binding = binding,
            requirementId = requirement.id,
            currentRevisionId = currentRevision.id,
            currentRevisionNumber = currentRevision.revisionNumber,
            occurrencePath = currentRevision.occurrencePath,
            occurrenceRemoved = !InformationRequestOccurrencePath.isActiveOccurrence(
                currentRevision.occurrencePath,
                activeOccurrencePaths,
            ),
        )
    }

    private fun authoredPolicyFacts(
        request: InformationRequest,
        binding: InformationRequestTemplateRequirementBinding,
    ): InformationRequestRequirementPolicyFacts?
    {
        val authoredRequirementId = UUID.randomUUID()
        return buildFacts(
            request = request,
            binding = binding,
            requirementId = authoredRequirementId,
            currentRevisionId = authoredRequirementId,
            currentRevisionNumber = 0,
            occurrencePath = "",
            occurrenceRemoved = false,
        )
    }

    private fun buildFacts(
        request: InformationRequest,
        binding: InformationRequestTemplateRequirementBinding,
        requirementId: UUID,
        currentRevisionId: UUID,
        currentRevisionNumber: Int,
        occurrencePath: String,
        occurrenceRemoved: Boolean,
    ): InformationRequestRequirementPolicyFacts?
    {
        val assignedRoleKey = binding.contributorRole.toShareRoleKey()
        val assignedParties = partyRepository
            .findActiveForRequestRole(request.id, assignedRoleKey)
            .map { it.toAssignedPartyFact() }
        val delegatedAuthorityFacts = delegatedAuthorityFactSource.factsFor(
            requestId = request.id,
            requirementId = requirementId,
            assignedPartyIds = assignedParties.map { it.partyId }.toSet(),
        )

        return InformationRequestRequirementPolicyFacts(
            requestId = request.id,
            requirementId = requirementId,
            currentRevisionId = currentRevisionId,
            currentRevisionNumber = currentRevisionNumber,
            sourceTemplateBindingId = binding.id,
            occurrencePath = occurrencePath,
            responseMode = binding.responseMode,
            assignedRoleKey = assignedRoleKey,
            assignedParties = assignedParties,
            confidentialityCompartmentKey = binding.confidentialityCompartmentKey,
            correctionScope = if (request.state == InformationRequestState.CHANGES_REQUESTED)
                InformationRequestRequirementCorrectionScope.OPEN_CORRECTION
            else
                InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE,
            requestState = request.state,
            parent = parentState.snapshot(request.exchangeId) ?: return null,
            delegatedAuthorityFacts = delegatedAuthorityFacts,
            occurrenceRemoved = occurrenceRemoved,
        )
    }

    private fun InformationRequestContributorRole.toShareRoleKey() =
        when (this)
        {
            InformationRequestContributorRole.SUBJECT -> InformationRequestShareRoleKey.SUBJECT
            InformationRequestContributorRole.CONTRIBUTOR -> InformationRequestShareRoleKey.CONTRIBUTOR
            InformationRequestContributorRole.PREPARER -> InformationRequestShareRoleKey.PREPARER
            InformationRequestContributorRole.ATTESTOR -> InformationRequestShareRoleKey.ATTESTOR
        }

    private fun InformationRequestParty.toAssignedPartyFact(): InformationRequestRequirementAssignedPartyFact
    {
        val principal = principalRef()
        return InformationRequestRequirementAssignedPartyFact(
            partyId = id,
            roleKey = roleKey,
            principal = principal,
            equivalentPrincipals = equivalentPrincipalsFor(principal),
            subjectIdentityRefId = subjectIdentityRefId,
            exchangeRecipientId = exchangeRecipientId,
            shareId = shareId,
        )
    }

    private fun InformationRequestParty.principalRef(): PrincipalRef?
    {
        val kind: PrincipalKind = principalKind ?: return null
        val id = principalId ?: return null
        return PrincipalRef(kind, id)
    }

    private fun equivalentPrincipalsFor(principal: PrincipalRef?): Set<PrincipalRef>
    {
        if (principal == null) return emptySet()
        return when (principal.kind)
        {
            PrincipalKind.PARTICIPANT -> linkedUserPrincipal(principal.id)?.let(::setOf) ?: emptySet()
            PrincipalKind.PRINCIPAL_GROUP -> groupMemberPrincipals(principal.id)
            else -> emptySet()
        }
    }

    private fun groupMemberPrincipals(groupId: UUID): Set<PrincipalRef> =
        principalGroupMemberRepository.findActiveMembers(groupId)
            .flatMap { member ->
                val memberPrincipal = PrincipalRef(member.principalKind, member.principalId)
                when (memberPrincipal.kind)
                {
                    PrincipalKind.USER -> listOf(memberPrincipal)
                    PrincipalKind.PARTICIPANT -> listOfNotNull(memberPrincipal, linkedUserPrincipal(memberPrincipal.id))
                    else -> emptyList()
                }
            }
            .toSet()

    private fun linkedUserPrincipal(participantId: UUID): PrincipalRef? =
        participantAccountLinkRepository.findByParticipantId(participantId)
            ?.let { PrincipalRef.user(it.appUserId) }

    companion object
    {
        private val terminalStates = setOf(
            InformationRequestState.CLOSED,
            InformationRequestState.CANCELLED,
            InformationRequestState.SUPERSEDED,
            InformationRequestState.EXPIRED,
        )
    }
}
