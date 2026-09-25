package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestDelegatedAuthorityServiceTest
{
    @Test
    fun `granting delegated authority for an assigned party records an active fact and advances the party revision`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val delegate = PrincipalRef.participant(UUID.randomUUID())
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = delegate,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-once",
        )

        val result = fixture.service.grant(command)

        assertEquals(party.id, result.authority.assignedPartyId)
        assertEquals(delegate.kind, result.authority.delegatePrincipalKind)
        assertEquals(delegate.id, result.authority.delegatePrincipalId)
        assertTrue(result.authority.active)
        assertEquals(actor.kind, result.authority.grantorPrincipalKind)
        assertEquals(actor.id, result.authority.grantorPrincipalId)
        assertNull(result.authority.authorityInstrumentRef)
        assertNull(result.authority.expiresAt)
        assertNull(result.authority.revokedAt)
        assertFalse(result.authority.effectiveAt.after(Timestamp.from(Instant.now())))
        assertEquals(2, fixture.request.partyRevision)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.authoritiesETag)
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals(ResourceType.INFORMATION_REQUEST, receipt.resourceType)
        assertEquals("grant-information-request-delegated-authority", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY, receipt.resultResourceType)
        assertEquals(result.authority.id, receipt.resultResourceId)
    }

    @Test
    fun `granting delegated authority records an authority instrument reference and effective window`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val effectiveAt = Timestamp.from(Instant.now().plusSeconds(3600))
        val expiresAt = Timestamp.from(Instant.now().plusSeconds(7200))
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            authorityInstrumentRef = "power-of-attorney:doc-42",
            effectiveAt = effectiveAt,
            expiresAt = expiresAt,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-with-instrument",
        )

        val result = fixture.service.grant(command)

        assertEquals("power-of-attorney:doc-42", result.authority.authorityInstrumentRef)
        assertEquals(effectiveAt, result.authority.effectiveAt)
        assertEquals(expiresAt, result.authority.expiresAt)
    }

    @Test
    fun `granting delegated authority rejects an expiry at or before the effective date`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val effectiveAt = Timestamp.from(Instant.now().plusSeconds(7200))
        val expiresAt = Timestamp.from(Instant.now().plusSeconds(3600))
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            effectiveAt = effectiveAt,
            expiresAt = expiresAt,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-invalid-expiry",
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.grant(command)
        }
        verify(fixture.authorityRepository, never()).save(any())
    }

    @Test
    fun `granting delegated authority requires the current parties ETag`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.Absent,
            idempotencyKey = "grant-missing-precondition",
        )

        assertThrows(CommandPreconditionException::class.java) {
            fixture.service.grant(command)
        }
        verify(fixture.authorityRepository, never()).save(any())
    }

    @Test
    fun `granting delegated authority rejects a revoked assigned party`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty().also { it.active = false }
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-revoked-party",
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.grant(command)
        }
        verify(fixture.authorityRepository, never()).save(any())
    }

    @Test
    fun `granting delegated authority rejects a subject party`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val subject = fixture.subjectParty()
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = subject.id,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-subject-party",
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.grant(command)
        }
        verify(fixture.authorityRepository, never()).save(any())
    }

    @Test
    fun `granting delegated authority rejects an unsupported delegate principal kind`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = PrincipalRef.publicLink(UUID.randomUUID()),
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-public-link-delegate",
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.grant(command)
        }
        verify(fixture.authorityRepository, never()).save(any())
    }

    @Test
    fun `granting delegated authority rejects a Requirement from another Information Request`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val foreignRequirement = fixture.requirement(requestId = UUID.randomUUID())
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            requirementId = foreignRequirement.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-foreign-requirement",
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.grant(command)
        }
        verify(fixture.authorityRepository, never()).save(any())
    }

    @Test
    fun `replaying a grant with the same idempotency key returns the stored authority without a second insert`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-once",
        )

        val first = fixture.service.grant(command)
        val replay = fixture.service.grant(command)

        assertEquals(first.authority.id, replay.authority.id)
        assertEquals(first.authoritiesETag, replay.authoritiesETag)
        verify(fixture.authorityRepository).save(any())
        assertEquals(2, fixture.request.partyRevision)
    }

    @Test
    fun `revoking delegated authority marks it inactive and advances the party revision`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val authority = fixture.activeAuthority(party)
        val command = RevokeInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            authorityId = authority.id,
            reason = "Access revoked",
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "revoke-once",
        )

        val result = fixture.service.revoke(command)

        assertFalse(result.authority.active)
        assertEquals(actor.kind, result.authority.revokedByPrincipalKind)
        assertEquals(actor.id, result.authority.revokedByPrincipalId)
        assertEquals("Access revoked", result.authority.revocationReason)
        assertFalse(result.authority.revokedAt!!.after(Timestamp.from(Instant.now())))
        assertEquals(2, fixture.request.partyRevision)
        assertEquals(InformationRequestETag.partiesOf(fixture.request), result.authoritiesETag)
        val receipt = fixture.receiptStore.receipts.single()
        assertEquals("revoke-information-request-delegated-authority", receipt.operationName)
        assertEquals(ResourceType.INFORMATION_REQUEST_DELEGATED_AUTHORITY, receipt.resultResourceType)
        assertEquals(authority.id, receipt.resultResourceId)
    }

    @Test
    fun `revoking an already revoked delegated authority is rejected`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val authority = fixture.activeAuthority(party).also { it.active = false }
        val command = RevokeInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            authorityId = authority.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "revoke-inactive",
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.revoke(command)
        }
    }

    @Test
    fun `revoking delegated authority rejects an authority from another Information Request`()
    {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val authority = fixture.activeAuthority(party).also { it.informationRequestId = UUID.randomUUID() }
        val command = RevokeInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            authorityId = authority.id,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "revoke-foreign-authority",
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.revoke(command)
        }
    }

    private class Fixture
    {
        private val savedParties = mutableMapOf<UUID, InformationRequestParty>()
        private val savedAuthorities = mutableMapOf<UUID, InformationRequestDelegatedAuthority>()
        private val savedRequirements = mutableMapOf<UUID, InformationRequestRequirement>()

        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            partyRevision = 1
        }
        val authorizationContext = AuthorizationContext(activeOrgId = request.ownerOrganizationId)
        val requestRepository = mock<InformationRequestRepository>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val authorityRepository = mock<InformationRequestDelegatedAuthorityRepository>()
        val authorizationService = mock<AuthorizationService>()
        val receiptStore = InMemoryDelegatedAuthorityReceiptStore()
        val commandReceiptService = CommandReceiptService(receiptStore)
        val service = InformationRequestDelegatedAuthorityService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            requirementRepository = requirementRepository,
            authorityRepository = authorityRepository,
            authorizationService = authorizationService,
            commandReceiptService = commandReceiptService,
        )

        init
        {
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(partyRepository.findByIdForUpdate(any())).thenAnswer { savedParties[it.getArgument(0)] }
            whenever(requirementRepository.findById(any())).thenAnswer { savedRequirements[it.getArgument(0)] }
            whenever(authorityRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestDelegatedAuthority>(0).also { authority ->
                    savedAuthorities[authority.id] = authority
                }
            }
            whenever(authorityRepository.update(any())).thenAnswer {
                it.getArgument<InformationRequestDelegatedAuthority>(0).also { authority ->
                    savedAuthorities[authority.id] = authority
                }
            }
            whenever(authorityRepository.findByIdForUpdate(any())).thenAnswer { savedAuthorities[it.getArgument(0)] }
            whenever(authorityRepository.findById(any())).thenAnswer { savedAuthorities[it.getArgument(0)] }
            whenever(
                authorizationService.authorize(
                    any(),
                    eq(Action.INFORMATION_REQUEST_MANAGE_PARTIES),
                    any<ResourceRef>(),
                    any(),
                ),
            ).thenReturn(Decision.Allow())
        }

        fun activeActingParty(): InformationRequestParty =
            InformationRequestParty().apply {
                id = UUID.randomUUID()
                informationRequestId = request.id
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
                principalKind = PrincipalKind.PARTICIPANT
                principalId = UUID.randomUUID()
                active = true
            }.also { party -> savedParties[party.id] = party }

        fun subjectParty(): InformationRequestParty =
            InformationRequestParty().apply {
                id = UUID.randomUUID()
                informationRequestId = request.id
                roleKey = InformationRequestShareRoleKey.SUBJECT
                subjectIdentityRefId = UUID.randomUUID()
                active = true
            }.also { party -> savedParties[party.id] = party }

        fun requirement(requestId: UUID): InformationRequestRequirement =
            InformationRequestRequirement().apply {
                id = UUID.randomUUID()
                informationRequestId = requestId
                sourceTemplateVersionId = UUID.randomUUID()
                sourceTemplateRequirementId = UUID.randomUUID()
                sourceTemplateBindingId = UUID.randomUUID()
                occurrencePath = "root"
            }.also { requirement -> savedRequirements[requirement.id] = requirement }

        fun activeAuthority(party: InformationRequestParty): InformationRequestDelegatedAuthority =
            InformationRequestDelegatedAuthority().apply {
                informationRequestId = request.id
                assignedPartyId = party.id
                delegatePrincipalKind = PrincipalKind.PARTICIPANT
                delegatePrincipalId = UUID.randomUUID()
                active = true
            }.also { authority -> savedAuthorities[authority.id] = authority }
    }
}

private class InMemoryDelegatedAuthorityReceiptStore : CommandReceiptStore
{
    val receipts = mutableListOf<CommandReceipt>()

    override fun findForCommand(request: CommandReceiptRequest): CommandReceipt? =
        receipts.firstOrNull {
            it.resourceType == request.resource.type &&
                it.resourceId == request.resource.id &&
                it.operationName == request.operation &&
                it.actorKind == request.actor.kind &&
                it.actorId == request.actor.id &&
                it.idempotencyKey == request.idempotencyKey
        }

    override fun insert(receipt: CommandReceipt): CommandReceipt
    {
        receipts += receipt
        return receipt
    }
}
