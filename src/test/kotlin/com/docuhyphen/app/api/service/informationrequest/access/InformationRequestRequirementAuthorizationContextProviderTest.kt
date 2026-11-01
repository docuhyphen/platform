package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementCorrectionScope
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementDelegatedAuthorityFact
import com.docuhyphen.app.api.model.informationrequest.access.InformationRequestRequirementPolicyFacts
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestActingParty
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.noauth.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.ExchangeLifecycleStateService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationPolicyLoader
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestDelegatedAuthorityFactSource
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import com.docuhyphen.app.api.service.organization.PrincipalGroupService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestRequirementAuthorizationContextProviderTest
{
    @Test
    fun `resolved Requirement context includes current policy and nominated active party facts`()
    {
        val fixture = Fixture()

        val context = fixture.provider.resolve(fixture.requirement.id)

        assertNotNull(context)
        assertEquals(OwnerContext.Organization(fixture.organizationId), context!!.ownerContext)
        assertEquals(ResourceRef.informationRequest(fixture.request.id), context.parentRef)
        val facts = context.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(fixture.request.id, facts.requestId)
        assertEquals(fixture.requirement.id, facts.requirementId)
        assertEquals(fixture.currentRevision.id, facts.currentRevisionId)
        assertEquals(fixture.currentRevision.revisionNumber, facts.currentRevisionNumber)
        assertEquals(fixture.binding.id, facts.sourceTemplateBindingId)
        assertEquals(fixture.currentRevision.occurrencePath, facts.occurrencePath)
        assertEquals(InformationRequestResponseMode.PROVIDE_ONCE, facts.responseMode)
        assertEquals(InformationRequestShareRoleKey.PREPARER, facts.assignedRoleKey)
        assertEquals("restricted-response", facts.confidentialityCompartmentKey)
        assertEquals(InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE, facts.correctionScope)
        assertEquals(emptyList<InformationRequestRequirementDelegatedAuthorityFact>(), facts.delegatedAuthorityFacts)
        assertEquals(1, facts.assignedParties.size)
        val assignedParty = facts.assignedParties.single()
        assertEquals(fixture.assignedParty.id, assignedParty.partyId)
        assertEquals(InformationRequestShareRoleKey.PREPARER, assignedParty.roleKey)
        assertSame(PrincipalKind.USER, assignedParty.principal!!.kind)
        assertEquals(fixture.assignedParty.principalId, assignedParty.principal.id)
        assertEquals(fixture.assignedParty.exchangeRecipientId, assignedParty.exchangeRecipientId)
        assertEquals(fixture.assignedParty.shareId, assignedParty.shareId)
    }

    @Test
    fun `a requirement's correction scope is the one its open correction states`()
    {
        val fixture = Fixture()
        whenever(fixture.lockService.correctionScopeOf(fixture.request.id, fixture.requirement.id))
            .thenReturn(InformationRequestRequirementCorrectionScope.CORRECTION_EXCLUDED)

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(InformationRequestRequirementCorrectionScope.CORRECTION_EXCLUDED, facts.correctionScope)
    }

    @Test
    fun `resolved Requirement context includes scoped delegated authority facts from active assigned parties`()
    {
        val fixture = Fixture()
        val authorityFact = InformationRequestRequirementDelegatedAuthorityFact(
            authorityId = UUID.randomUUID(),
            assignedPartyId = fixture.assignedParty.id,
            delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
            requestId = fixture.request.id,
            requirementId = fixture.requirement.id,
            active = true,
        )
        whenever(
            fixture.delegatedAuthorityFactSource.factsFor(
                eq(fixture.request.id),
                eq(fixture.requirement.id),
                eq(setOf(fixture.assignedParty.id)),
            ),
        ).thenReturn(listOf(authorityFact))

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(listOf(authorityFact), facts.delegatedAuthorityFacts)
        verify(fixture.delegatedAuthorityFactSource).factsFor(
            eq(fixture.request.id),
            eq(fixture.requirement.id),
            eq(setOf(fixture.assignedParty.id)),
        )
    }

    @Test
    fun `a party acts for itself and a delegate acts for it only under active authority over that Requirement`()
    {
        val fixture = Fixture()
        val delegate = PrincipalRef.user(UUID.randomUUID())
        val elsewhere = PrincipalRef.user(UUID.randomUUID())
        val authorityId = UUID.randomUUID()
        whenever(
            fixture.delegatedAuthorityFactSource.factsFor(
                eq(fixture.request.id),
                eq(fixture.requirement.id),
                eq(setOf(fixture.assignedParty.id)),
            ),
        ).thenReturn(
            listOf(
                InformationRequestRequirementDelegatedAuthorityFact(
                    authorityId,
                    fixture.assignedParty.id,
                    delegate,
                    fixture.request.id,
                    fixture.requirement.id,
                    active = true,
                ),
                InformationRequestRequirementDelegatedAuthorityFact(
                    UUID.randomUUID(),
                    fixture.assignedParty.id,
                    elsewhere,
                    fixture.request.id,
                    UUID.randomUUID(),
                    active = true,
                ),
            ),
        )
        val roles = setOf(com.docuhyphen.app.api.model.entity.InformationRequestContributorRole.PREPARER)
        val party = PrincipalRef(PrincipalKind.USER, fixture.assignedParty.principalId!!)

        val itself = fixture.provider.actingPartiesFor(fixture.request, roles, party, fixture.requirement.id).single()
        val delegated =
            fixture.provider.actingPartiesFor(fixture.request, roles, delegate, fixture.requirement.id).single()

        assertEquals(null, itself.delegatedAuthorityId)
        assertEquals(authorityId, delegated.delegatedAuthorityId)
        assertEquals(fixture.assignedParty.id, delegated.party.id)
        assertEquals(
            emptyList<InformationRequestActingParty>(),
            fixture.provider.actingPartiesFor(fixture.request, roles, elsewhere, fixture.requirement.id)
        )
    }

    @Test
    fun `participant assigned party facts include verified registered account principal without changing provenance`()
    {
        val fixture = Fixture()
        val participantId = UUID.randomUUID()
        val linkedUserId = UUID.randomUUID()
        fixture.assignedParty.principalKind = PrincipalKind.PARTICIPANT
        fixture.assignedParty.principalId = participantId
        whenever(fixture.participantAccountLinkRepository.findByParticipantId(participantId)).thenReturn(
            ParticipantAccountLink().apply {
                this.participantId = participantId
                appUserId = linkedUserId
                linkedViaInformationRequestId = fixture.request.id
                linkedViaShareLinkId = UUID.randomUUID()
            },
        )

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        val assignedParty = facts.assignedParties.single()
        assertEquals(PrincipalRef.participant(participantId), assignedParty.principal)
        assertEquals(setOf(PrincipalRef.user(linkedUserId)), assignedParty.equivalentPrincipals)
    }

    @Test
    fun `linked identities join a participant, its registered account, and every participant linked to that account`() {
        val fixture = Fixture()
        val accountId = UUID.randomUUID()
        val links = List(2) {
            ParticipantAccountLink().apply {
                participantId = UUID.randomUUID()
                appUserId = accountId
                linkedViaInformationRequestId = UUID.randomUUID()
                linkedViaShareLinkId = UUID.randomUUID()
            }
        }
        val participant = PrincipalRef.participant(links.first().participantId)
        whenever(fixture.participantAccountLinkRepository.findByParticipantId(participant.id)).thenReturn(links.first())
        whenever(fixture.participantAccountLinkRepository.findForAppUser(accountId)).thenReturn(links)
        val identities = setOf(PrincipalRef.user(accountId)) + links.map { PrincipalRef.participant(it.participantId) }

        assertEquals(identities, fixture.provider.linkedIdentitiesOf(participant))
        assertEquals(identities, fixture.provider.linkedIdentitiesOf(PrincipalRef.user(accountId)))
    }

    @Test
    fun `an identity without an account link stands alone`() {
        val fixture = Fixture()
        val participant = PrincipalRef.participant(UUID.randomUUID())
        val account = PrincipalRef.user(UUID.randomUUID())
        val group = PrincipalRef.group(UUID.randomUUID())

        assertEquals(setOf(participant), fixture.provider.linkedIdentitiesOf(participant))
        assertEquals(setOf(account), fixture.provider.linkedIdentitiesOf(account))
        assertEquals(setOf(group), fixture.provider.linkedIdentitiesOf(group))
    }

    @Test
    fun `assigned group party facts include current active User members`()
    {
        val fixture = Fixture()
        val groupId = UUID.randomUUID()
        val memberUserId = UUID.randomUUID()
        fixture.assignedParty.principalKind = PrincipalKind.PRINCIPAL_GROUP
        fixture.assignedParty.principalId = groupId
        whenever(fixture.principalGroups.activeMembers(groupId)).thenReturn(
            listOf(
                PrincipalGroupMember().apply {
                    principalGroupId = groupId
                    principalKind = PrincipalKind.USER
                    principalId = memberUserId
                    isActive = true
                },
            ),
        )

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(setOf(PrincipalRef.user(memberUserId)), facts.assignedParties.single().equivalentPrincipals)
    }

    @Test
    fun `assigned group party facts include linked Users for active participant members`()
    {
        val fixture = Fixture()
        val groupId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val linkedUserId = UUID.randomUUID()
        fixture.assignedParty.principalKind = PrincipalKind.PRINCIPAL_GROUP
        fixture.assignedParty.principalId = groupId
        whenever(fixture.principalGroups.activeMembers(groupId)).thenReturn(
            listOf(
                PrincipalGroupMember().apply {
                    principalGroupId = groupId
                    principalKind = PrincipalKind.PARTICIPANT
                    principalId = participantId
                    isActive = true
                },
            ),
        )
        whenever(fixture.participantAccountLinkRepository.findByParticipantId(participantId)).thenReturn(
            ParticipantAccountLink().apply {
                this.participantId = participantId
                appUserId = linkedUserId
                linkedViaInformationRequestId = fixture.request.id
                linkedViaShareLinkId = UUID.randomUUID()
            },
        )

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(
            setOf(PrincipalRef.participant(participantId), PrincipalRef.user(linkedUserId)),
            facts.assignedParties.single().equivalentPrincipals,
        )
    }

    @Test
    fun `assigned group party facts ignore unsupported nested group members`()
    {
        val fixture = Fixture()
        val groupId = UUID.randomUUID()
        fixture.assignedParty.principalKind = PrincipalKind.PRINCIPAL_GROUP
        fixture.assignedParty.principalId = groupId
        whenever(fixture.principalGroups.activeMembers(groupId)).thenReturn(
            listOf(
                PrincipalGroupMember().apply {
                    principalGroupId = groupId
                    principalKind = PrincipalKind.PRINCIPAL_GROUP
                    principalId = UUID.randomUUID()
                    isActive = true
                },
            ),
        )

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(emptySet<PrincipalRef>(), facts.assignedParties.single().equivalentPrincipals)
    }

    @Test
    fun `assigned group party facts leave out the members its group Share holds back`() {
        val fixture = Fixture()
        val groupId = UUID.randomUUID()
        val admittedUserId = UUID.randomUUID()
        val heldBackUserId = UUID.randomUUID()
        val heldBackParticipantId = UUID.randomUUID()
        val heldBackLinkedUserId = UUID.randomUUID()
        fixture.assignedParty.principalKind = PrincipalKind.PRINCIPAL_GROUP
        fixture.assignedParty.principalId = groupId
        whenever(fixture.principalGroups.activeMembers(groupId)).thenReturn(
            listOf(
                groupMember(groupId, PrincipalKind.USER, admittedUserId),
                groupMember(groupId, PrincipalKind.USER, heldBackUserId),
                groupMember(groupId, PrincipalKind.PARTICIPANT, heldBackParticipantId),
            ),
        )
        whenever(fixture.participantAccountLinkRepository.findByParticipantId(heldBackParticipantId)).thenReturn(
            ParticipantAccountLink().apply {
                participantId = heldBackParticipantId
                appUserId = heldBackLinkedUserId
                linkedViaInformationRequestId = fixture.request.id
                linkedViaShareLinkId = UUID.randomUUID()
            },
        )
        whenever(
            fixture.shareService.membersHeldBackFrom(
                eq(requireNotNull(fixture.assignedParty.shareId)),
                org.mockito.kotlin.any(),
            ),
        ).thenReturn(setOf(PrincipalRef.user(heldBackUserId), PrincipalRef.participant(heldBackParticipantId)))

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(setOf(PrincipalRef.user(admittedUserId)), facts.assignedParties.single().equivalentPrincipals)
        assertEquals(
            setOf(PrincipalRef.group(groupId), PrincipalRef.user(admittedUserId)),
            fixture.provider.principalsActingFor(fixture.assignedParty),
        )
        assertEquals(
            emptyList<InformationRequestActingParty>(),
            fixture.provider.actingPartiesFor(
                fixture.request,
                setOf(InformationRequestContributorRole.PREPARER),
                PrincipalRef.user(heldBackLinkedUserId),
                fixture.requirement.id,
            ),
        )
        assertEquals(
            listOf(fixture.assignedParty.id),
            fixture.provider.actingPartiesFor(
                fixture.request,
                setOf(InformationRequestContributorRole.PREPARER),
                PrincipalRef.user(admittedUserId),
                fixture.requirement.id,
            ).map { it.party.id },
        )
    }

    private fun groupMember(groupId: UUID, kind: PrincipalKind, id: UUID): PrincipalGroupMember =
        PrincipalGroupMember().apply {
            principalGroupId = groupId
            principalKind = kind
            principalId = id
            isActive = true
        }

    @Test
    fun `assigned group party facts omit removed members when current membership is empty`()
    {
        val fixture = Fixture()
        val groupId = UUID.randomUUID()
        fixture.assignedParty.principalKind = PrincipalKind.PRINCIPAL_GROUP
        fixture.assignedParty.principalId = groupId
        whenever(fixture.principalGroups.activeMembers(groupId)).thenReturn(emptyList())

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(emptySet<PrincipalRef>(), facts.assignedParties.single().equivalentPrincipals)
    }

    @Test
    fun `a Requirement resolves through its current pointer even after that revision's interval closed`() {
        val fixture = Fixture()
        fixture.currentRevision.effectiveTo = java.sql.Timestamp.from(java.time.Instant.now())

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(fixture.currentRevision.id, facts.currentRevisionId)
        assertEquals(fixture.currentRevision.revisionNumber, facts.currentRevisionNumber)
    }

    @Test
    fun `a removed occurrence's Requirement context marks occurrenceRemoved true`()
    {
        val fixture = Fixture()
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(true, facts.occurrenceRemoved)
    }

    @Test
    fun `an active occurrence's Requirement context marks occurrenceRemoved false`()
    {
        val fixture = Fixture()

        val context = fixture.provider.resolve(fixture.requirement.id)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(false, facts.occurrenceRemoved)
    }

    @Test
    fun `an authored binding with no materialized occurrence yet is never marked occurrenceRemoved`()
    {
        val fixture = Fixture()
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())

        val context = fixture.provider.authoredContextFor(fixture.request, fixture.binding)

        val facts = context!!.policyFacts as InformationRequestRequirementPolicyFacts
        assertEquals(false, facts.occurrenceRemoved)
    }

    private class Fixture
    {
        val organizationId: UUID = UUID.randomUUID()
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = organizationId
            state = InformationRequestState.IN_PROGRESS
        }
        val requirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "root"
        }
        val currentRevision = InformationRequestRequirementRevision().apply {
            id = UUID.randomUUID()
            informationRequestRequirementId = requirement.id
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = requirement.sourceTemplateRequirementId
            sourceTemplateBindingId = UUID.randomUUID()
            revisionNumber = 3
            occurrencePath = "root.sections[2]"
            configurationHashSha256 = "0".repeat(64)
            optimisticVersion = 5
        }
        val binding = InformationRequestTemplateRequirementBinding().apply {
            id = currentRevision.sourceTemplateBindingId
            templateVersionId = request.templateVersionId
            templateDefinitionId = UUID.randomUUID()
            templateRequirementId = requirement.sourceTemplateRequirementId
            templateSectionId = UUID.randomUUID()
            prompt = "Provide the requested response"
            responseMode = InformationRequestResponseMode.PROVIDE_ONCE
            contributorRole = com.docuhyphen.app.api.model.entity.InformationRequestContributorRole.PREPARER
            confidentialityCompartmentKey = "restricted-response"
        }
        val assignedParty = InformationRequestParty().apply {
            id = UUID.randomUUID()
            informationRequestId = request.id
            roleKey = InformationRequestShareRoleKey.PREPARER
            principalKind = PrincipalKind.USER
            principalId = UUID.randomUUID()
            exchangeRecipientId = UUID.randomUUID()
            shareId = UUID.randomUUID()
        }

        private val requirementRepository = mock<InformationRequestRequirementRepository>()
        private val requestRepository = mock<InformationRequestRepository>()
        private val revisionRepository = mock<InformationRequestRequirementRevisionRepository>()
        private val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        private val partyRepository = mock<InformationRequestPartyRepository>()
        val participantAccountLinkRepository = mock<ParticipantAccountLinkRepository>()
        val principalGroups = mock<PrincipalGroupService>()
        val shareService = mock<ShareService>()
        val delegatedAuthorityFactSource = mock<InformationRequestDelegatedAuthorityFactSource>()
        val parentState = mock<ExchangeLifecycleStateService> {
            on { snapshot(org.mockito.kotlin.any()) }.thenReturn(InformationRequestParentSnapshot(com.docuhyphen.app.api.model.entity.ExchangeStatus.ACCEPTED_STARTED))
        }
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val lockService = mock<InformationRequestSubmissionLockService> {
            on { correctionScopeOf(org.mockito.kotlin.any(), org.mockito.kotlin.any()) }
                .thenReturn(InformationRequestRequirementCorrectionScope.NORMAL_RESPONSE)
        }
        val provider = InformationRequestRequirementAuthorizationContextProvider(
            requirementRepository,
            requestRepository,
            revisionRepository,
            bindingRepository,
            partyRepository,
            delegatedAuthorityFactSource,
            parentState,
            occurrenceRepository,
            participantAccountLinkRepository,
            principalGroups,
            mock<InformationRequestAttestationPolicyLoader>(),
            lockService,
            shareService,
        )

        init
        {
            whenever(requirementRepository.findById(requirement.id)).thenReturn(requirement)
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(revisionRepository.findCurrentForRequirement(requirement.id)).thenReturn(currentRevision)
            whenever(bindingRepository.findById(currentRevision.sourceTemplateBindingId)).thenReturn(binding)
            whenever(
                partyRepository.findActiveForRequestRole(
                    eq(request.id),
                    eq(InformationRequestShareRoleKey.PREPARER)
                )
            )
                .thenReturn(listOf(assignedParty))
            whenever(
                delegatedAuthorityFactSource.factsFor(
                    eq(request.id),
                    eq(requirement.id),
                    eq(setOf(assignedParty.id)),
                ),
            ).thenReturn(emptyList())
            whenever(occurrenceRepository.findForRequest(request.id)).thenReturn(
                listOf(
                    com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence().apply {
                        informationRequestId = request.id
                        occurrencePath = currentRevision.occurrencePath
                    },
                ),
            )
            whenever(participantAccountLinkRepository.findByParticipantId(org.mockito.kotlin.any())).thenReturn(null)
            whenever(principalGroups.activeMembers(org.mockito.kotlin.any())).thenReturn(emptyList())
        }
    }
}
