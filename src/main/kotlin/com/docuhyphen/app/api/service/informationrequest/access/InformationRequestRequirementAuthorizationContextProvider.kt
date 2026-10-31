package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementAssignedPartyFact
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementCorrectionScope
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementDelegatedAuthorityFact
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementPolicyFacts
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestActingParty
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.noauth.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationPolicyLoader
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentPolicy
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestDelegatedAuthorityFactSource
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

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
    private val principalGroups: PrincipalGroupService,
    private val attestationPolicies: InformationRequestAttestationPolicyLoader,
    private val lockService: InformationRequestSubmissionLockService,
    private val shareService: ShareService,
) : ResourceAuthorizationContextProvider
{
    override val supportedKind: ResourceKind = ResourceKind.INFORMATION_REQUEST_REQUIREMENT

    override val actionsWhileArchived: Set<Action> = InformationRequestParentPolicy.readActions

    override fun resolve(resourceId: UUID): ResourceAuthorizationContext?
    {
        val requirement = requirementRepository.findById(resourceId) ?: return null
        val request = requestRepository.findById(requirement.informationRequestId) ?: return null
        val facts = requirementPolicyFacts(requirement, request) ?: return null
        return resourceContext(request, facts)
    }

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
        val currentRevision = revisionRepository.findCurrentForRequirement(requirement.id) ?: return null
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
            correctionScope = lockService.correctionScopeOf(request.id, requirement.id),
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
        correctionScope: InformationRequestRequirementCorrectionScope = InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE,
    ): InformationRequestRequirementPolicyFacts?
    {
        val assignedRoleKey = binding.contributorRole.toShareRoleKey()
        val attestingRoleKeys = attestationPolicies.forBinding(binding.id)
            ?.requiredRoles
            ?.map { it.toShareRoleKey() }
            ?.toSet()
            .orEmpty()
        val assignedParties = (setOf(assignedRoleKey) + attestingRoleKeys)
            .flatMap { role -> partyRepository.findActiveForRequestRole(request.id, role) }
            .distinctBy { it.id }
            .map { it.toAssignedPartyFact() }
        val delegatedAuthorityFacts = delegatedAuthorityFactsFor(
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
            correctionScope = correctionScope,
            requestState = request.state,
            parent = parentState.snapshot(request.exchangeId) ?: return null,
            delegatedAuthorityFacts = delegatedAuthorityFacts,
            occurrenceRemoved = occurrenceRemoved,
            attestingRoleKeys = attestingRoleKeys,
        )
    }

    fun actingPartiesFor(
        request: InformationRequest,
        roles: Set<InformationRequestContributorRole>,
        principal: PrincipalRef,
        requirementId: UUID,
    ): List<InformationRequestActingParty>
    {
        val parties = roles.map { it.toShareRoleKey() }
            .flatMap { role -> partyRepository.findActiveForRequestRole(request.id, role) }
            .distinctBy { it.id }
        val authorities = delegatedAuthorityFactsFor(
            requestId = request.id,
            requirementId = requirementId,
            assignedPartyIds = parties.map { it.id }.toSet(),
        )
        return parties.mapNotNull { party ->
            val partyPrincipal = party.principalRef()
            val role = party.roleKey.toContributorRole() ?: return@mapNotNull null
            when
            {
                (partyPrincipal == principal && party.linkedUserShareId == null) ||
                        principal in equivalentPrincipalsOf(party) -> InformationRequestActingParty(
                    party,
                    role,
                    null
                )

                else -> authorities
                    .firstOrNull {
                        it.active && it.assignedPartyId == party.id && it.admits(principal) &&
                                (it.requirementId == null || it.requirementId == requirementId)
                    }
                    ?.let { InformationRequestActingParty(party, role, it.authorityId) }
            }
        }
    }

    fun contributingPartyIds(request: InformationRequest, requirementId: UUID, principal: PrincipalRef): Set<UUID> {
        val revision = revisionRepository.findCurrentForRequirement(requirementId) ?: return emptySet()
        val binding = bindingRepository.findById(revision.sourceTemplateBindingId) ?: return emptySet()
        return actingPartiesFor(request, setOf(binding.contributorRole), principal, requirementId)
            .map { it.party.id }
            .toSet()
    }

    private fun delegatedAuthorityFactsFor(
        requestId: UUID,
        requirementId: UUID,
        assignedPartyIds: Set<UUID>,
    ): List<InformationRequestRequirementDelegatedAuthorityFact> =
        delegatedAuthorityFactSource.factsFor(
            requestId = requestId,
            requirementId = requirementId,
            assignedPartyIds = assignedPartyIds,
        ).map { it.copy(equivalentDelegatePrincipals = equivalentPrincipalsFor(it.delegatePrincipal)) }

    fun principalsActingAs(principal: PrincipalRef): Set<PrincipalRef> =
        setOf(principal) + equivalentPrincipalsFor(principal)

    fun answeringRoleKeysOf(requirementId: UUID): Set<InformationRequestShareRoleKey> {
        val revision = revisionRepository.findCurrentForRequirement(requirementId) ?: return emptySet()
        val binding = bindingRepository.findById(revision.sourceTemplateBindingId) ?: return emptySet()
        val attesting = attestationPolicies.forBinding(binding.id)?.requiredRoles.orEmpty().map { it.toShareRoleKey() }
        return setOf(binding.contributorRole.toShareRoleKey()) + attesting
    }

    fun principalsActingFor(party: InformationRequestParty): Set<PrincipalRef>
    {
        val principal = party.principalRef() ?: return emptySet()
        return setOf(principal) + equivalentPrincipalsOf(party)
    }

    fun linkedAccountPrincipalsOf(principal: PrincipalRef): Set<PrincipalRef> =
        if (principal.kind == PrincipalKind.PARTICIPANT) setOfNotNull(linkedUserPrincipal(principal.id)) else emptySet()

    fun linkedIdentitiesOf(principal: PrincipalRef): Set<PrincipalRef> {
        val account = when (principal.kind) {
            PrincipalKind.USER -> principal
            PrincipalKind.PARTICIPANT -> linkedUserPrincipal(principal.id)
            else -> null
        } ?: return setOf(principal)
        return setOf(principal, account) +
                participantAccountLinkRepository.findForAppUser(account.id)
                    .map { PrincipalRef.participant(it.participantId) }
    }

    private fun InformationRequestShareRoleKey.toContributorRole(): InformationRequestContributorRole? =
        when (this)
        {
            InformationRequestShareRoleKey.SUBJECT -> InformationRequestContributorRole.SUBJECT
            InformationRequestShareRoleKey.CONTRIBUTOR -> InformationRequestContributorRole.CONTRIBUTOR
            InformationRequestShareRoleKey.PREPARER -> InformationRequestContributorRole.PREPARER
            InformationRequestShareRoleKey.ATTESTOR -> InformationRequestContributorRole.ATTESTOR
            InformationRequestShareRoleKey.REVIEWER,
            InformationRequestShareRoleKey.DECISION_MAKER,
            InformationRequestShareRoleKey.RECORD_OWNER,
                -> null
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
            equivalentPrincipals = equivalentPrincipalsOf(this),
            subjectIdentityRefId = subjectIdentityRefId,
            exchangeRecipientId = exchangeRecipientId,
            shareId = shareId,
            accountLinked = linkedUserShareId != null,
        )
    }

    private fun InformationRequestParty.principalRef(): PrincipalRef?
    {
        val kind: PrincipalKind = principalKind ?: return null
        val id = principalId ?: return null
        return PrincipalRef(kind, id)
    }

    private fun equivalentPrincipalsOf(party: InformationRequestParty): Set<PrincipalRef> =
        equivalentPrincipalsFor(party.principalRef(), party.shareId)

    private fun equivalentPrincipalsFor(principal: PrincipalRef?, groupShareId: UUID? = null): Set<PrincipalRef>
    {
        if (principal == null) return emptySet()
        return when (principal.kind)
        {
            PrincipalKind.PARTICIPANT -> linkedUserPrincipal(principal.id)?.let(::setOf) ?: emptySet()
            PrincipalKind.PRINCIPAL_GROUP -> groupMemberPrincipals(principal.id, groupShareId)
            else -> emptySet()
        }
    }

    private fun groupMemberPrincipals(groupId: UUID, groupShareId: UUID?): Set<PrincipalRef> {
        val members = principalGroups.activeMembers(groupId)
            .map { member -> PrincipalRef(member.principalKind, member.principalId) }
            .filter { it.kind == PrincipalKind.USER || it.kind == PrincipalKind.PARTICIPANT }
        val heldBack = groupShareId?.let { shareService.membersHeldBackFrom(it, members) }.orEmpty()
        return members
            .filterNot { it in heldBack }
            .flatMap { member ->
                if (member.kind == PrincipalKind.PARTICIPANT) listOfNotNull(member, linkedUserPrincipal(member.id))
                else listOf(member)
            }
            .toSet()
    }

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
