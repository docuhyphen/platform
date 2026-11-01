package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestCredentialCutoff
import com.docuhyphen.app.api.model.informationrequest.party.GrantInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.model.informationrequest.party.RevokeInformationRequestDelegatedAuthorityCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestDelegatedAuthorityRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptScope
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditPayloadPolicy
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.*

class InformationRequestDelegatedAuthorityServiceTest
{
    @Test
    fun `granting delegated authority is refused once the request has finished`() {
        val fixture = Fixture()
        fixture.request.state = InformationRequestState.CLOSED
        val party = fixture.activeActingParty()

        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.grant(fixture.grantCommand(party, "grant-finished"))
        }

        verify(fixture.authorityRepository, never()).save(any())
        assertEquals(1, fixture.request.partyRevision)
        assertTrue(fixture.receiptStore.receipts.isEmpty())
    }

    @Test
    fun `granting and revoking delegated authority are refused once the parent Exchange has ended`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val authority = fixture.activeAuthority(party)
        fixture.exchange.status = ExchangeStatus.ENDED

        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.grant(fixture.grantCommand(party, "grant-ended-parent"))
        }
        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.revoke(fixture.revokeCommand(authority, "revoke-ended-parent"))
        }

        assertTrue(authority.active)
        assertEquals(1, fixture.request.partyRevision)
    }

    @Test
    fun `a revoked execution grant refuses new delegated authority but still lets a manager revoke it`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val authority = fixture.activeAuthority(party)
        whenever(fixture.executionGrantService.findForRequest(fixture.request.id)).thenReturn(
            RequestExecutionGrant().apply { revokedAt = Timestamp.from(Instant.now()) },
        )

        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.grant(fixture.grantCommand(party, "grant-revoked-grant"))
        }
        val revoked = fixture.service.revoke(fixture.revokeCommand(authority, "revoke-revoked-grant"))

        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, refused.reasonCode)
        assertFalse(revoked.authority.active)
    }

    @Test
    fun `delegated authority commands lock the parent Exchange before the request row`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()

        fixture.service.grant(fixture.grantCommand(party, "grant-lock-order"))

        val locks = inOrder(fixture.exchangeLookup, fixture.requestRepository)
        locks.verify(fixture.exchangeLookup).lockExchangeForUpdate(fixture.exchange.id)
        locks.verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
    }

    @Test
    fun `revoking the authorities a party holds records each with its cause and leaves other parties alone`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val other = fixture.activeActingParty()
        val first = fixture.activeAuthority(party)
        val second = fixture.activeAuthority(party)
        val kept = fixture.activeAuthority(other)
        whenever(fixture.authorityRepository.findActiveForPartyForUpdate(party.id)).thenReturn(listOf(first, second))
        val actor = PrincipalRef.user(UUID.randomUUID())

        fixture.service.revokeAllForParty(
            fixture.request,
            party,
            InformationRequestCredentialCutoff(
                InformationRequestAuditActor.of(
                    RequestAccessContext(
                        actor,
                        fixture.authorizationContext.copy(sessionRef = "manager-session")
                    )
                ),
                InformationRequestCredentialCutoff.PARTY_REASSIGNED,
            ),
        )

        listOf(first, second).forEach { authority ->
            assertFalse(authority.active)
            assertEquals(actor.kind, authority.revokedByPrincipalKind)
            assertEquals(actor.id, authority.revokedByPrincipalId)
            assertEquals(InformationRequestCredentialCutoff.PARTY_REASSIGNED, authority.revocationReason)
        }
        assertTrue(kept.active)
        val history =
            fixture.savedTransitions.filter { it.mutation == InformationRequestMutation.REVOKE_DELEGATED_AUTHORITY }
        assertEquals(2, history.size)
        assertTrue(history.all { it.reasonCode == InformationRequestCredentialCutoff.PARTY_REASSIGNED && it.partyId == party.id })
    }

    @Test
    fun `request administrators list the delegated authorities of a request with the parties ETag and others are refused`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val authority = fixture.activeAuthority(party)
        whenever(fixture.authorityRepository.findForRequest(fixture.request.id)).thenReturn(listOf(authority))
        val manager = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)

        val listed = fixture.service.list(fixture.request.id, manager)

        assertEquals(listOf(authority.id), listed.authorities.map { it.id })
        assertEquals(InformationRequestETag.partiesOf(fixture.request), listed.authoritiesETag)
        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_VIEW_OPERATIONS),
                any<ResourceRef>(),
                any(),
            ),
        ).thenReturn(Decision.Deny("DENIED", "denied"))
        assertThrows(io.quarkus.security.ForbiddenException::class.java) {
            fixture.service.list(fixture.request.id, manager)
        }
    }

    @Test
    fun `listing delegated authorities reads the request's administration rather than managing its parties`() {
        val fixture = Fixture()
        val authority = fixture.activeAuthority(fixture.activeActingParty())
        whenever(fixture.authorityRepository.findForRequest(fixture.request.id)).thenReturn(listOf(authority))
        whenever(
            fixture.authorizationService.authorize(
                any(),
                eq(Action.INFORMATION_REQUEST_MANAGE_PARTIES),
                any<ResourceRef>(),
                any(),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_EXCHANGE_ARCHIVED, "archived"))
        val administrator = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext)

        val listed = fixture.service.list(fixture.request.id, administrator)

        assertEquals(listOf(authority.id), listed.authorities.map { it.id })
    }

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
            authorityInstrumentRef = "authority-record:doc-42",
            effectiveAt = effectiveAt,
            expiresAt = expiresAt,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-with-instrument",
        )

        val result = fixture.service.grant(command)

        assertEquals("authority-record:doc-42", result.authority.authorityInstrumentRef)
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

        val refusal = assertThrows(InformationRequestCommandRequestException::class.java) {
            fixture.service.grant(command)
        }
        assertEquals(InformationRequestErrorCatalog.DELEGATED_AUTHORITY_WINDOW_INVALID, refusal.reasonCode)
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

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.grant(command)
        }
        assertEquals(InformationRequestErrorCatalog.DELEGATED_AUTHORITY_PARTY_INELIGIBLE, refusal.reasonCode)
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

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.grant(command)
        }
        assertEquals(InformationRequestErrorCatalog.DELEGATED_AUTHORITY_PARTY_INELIGIBLE, refusal.reasonCode)
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

        val refusal = assertThrows(InformationRequestCommandRequestException::class.java) {
            fixture.service.grant(command)
        }
        assertEquals(InformationRequestErrorCatalog.PARTY_ASSIGNMENT_INVALID, refusal.reasonCode)
        verify(fixture.authorityRepository, never()).save(any())
    }

    @Test
    fun `granting delegated authority resolves the delegate under the request's sharing policy before writing`() {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val delegate = PrincipalRef.user(UUID.randomUUID())
        doThrow(IllegalArgumentException("User not found"))
            .whenever(fixture.eligibility).requireEligible(fixture.request, actor, delegate)
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = delegate,
            access = RequestAccessContext(actor, fixture.authorizationContext),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-unknown-delegate",
        )

        val refusal = assertThrows(IllegalArgumentException::class.java) { fixture.service.grant(command) }

        assertEquals("User not found", refusal.message)
        verify(fixture.authorityRepository, never()).save(any())
        assertEquals(1, fixture.request.partyRevision)
        assertTrue(fixture.receiptStore.receipts.isEmpty())
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

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.revoke(command)
        }
        assertEquals(InformationRequestErrorCatalog.DELEGATED_AUTHORITY_ALREADY_REVOKED, refusal.reasonCode)
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

    @Test
    fun `a request wide grant records correlated history audit and event once without the instrument`() {
        val fixture = Fixture()
        val actor = PrincipalRef.user(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val command = GrantInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            assignedPartyId = party.id,
            delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
            authorityInstrumentRef = "authority-record:private-reference",
            access = RequestAccessContext(actor, fixture.authorizationContext.copy(sessionRef = "manager-session")),
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "grant-with-history",
        )

        val result = fixture.service.grant(command)
        fixture.service.grant(command)

        assertEquals(1, fixture.savedTransitions.size)
        val transition = fixture.savedTransitions.single()
        assertEquals("GRANT_DELEGATED_AUTHORITY", transition.mutation.name)
        assertEquals(party.id, transition.partyId)
        assertEquals(actor.id, transition.actorId)
        assertEquals(transition.fromState, transition.toState)
        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder).record(audit.capture())
        assertEquals("information_request.delegated_authority.grant", audit.firstValue.eventTypeKey)
        assertEquals(AuditOwnerScope.Organization(fixture.request.ownerOrganizationId!!), audit.firstValue.owner)
        assertEquals("manager-session", audit.firstValue.sessionId)
        assertEquals(result.authority.id.toString(), audit.firstValue.payload["authorityId"])
        assertFalse(audit.firstValue.payload.values.contains(command.authorityInstrumentRef))
        assertEquals(
            audit.firstValue.payload,
            InformationRequestAuditPayloadPolicy.projectionOf(audit.firstValue.payload) { _ -> true }.payload
        )
        val event = fixture.events.single()
        assertEquals(audit.firstValue.eventId.toString(), event.id)
        assertEquals(audit.firstValue.payload, event.payload)
        assertEquals(transition.id.toString(), audit.firstValue.businessTransactionId)
        assertEquals(InformationRequestTransitionHistoryService.orderingKeyOf(fixture.request.id), event.orderingKey)
    }

    @Test
    fun `a scoped grant and revocation preserve personal owner and no auth provenance without free text`() {
        val fixture = Fixture()
        fixture.request.ownerType = InformationRequestOwnerType.USER
        fixture.request.ownerUserId = UUID.randomUUID()
        fixture.request.ownerOrganizationId = null
        val actor = PrincipalRef.participant(UUID.randomUUID())
        val party = fixture.activeActingParty()
        val requirement = fixture.requirement(fixture.request.id)
        val access = RequestAccessContext(actor, fixture.authorizationContext.copy(sessionRef = "verified-session"))
        val grant = fixture.service.grant(
            GrantInformationRequestDelegatedAuthorityCommand(
                requestId = fixture.request.id,
                assignedPartyId = party.id,
                delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
                requirementId = requirement.id,
                access = access,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                idempotencyKey = "scoped-grant",
            ),
        )
        val revoke = RevokeInformationRequestDelegatedAuthorityCommand(
            requestId = fixture.request.id,
            authorityId = grant.authority.id,
            reason = "Private reason for ending this delegation",
            access = access,
            precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
            idempotencyKey = "scoped-revoke",
        )
        fixture.service.revoke(revoke)
        fixture.service.revoke(revoke)

        assertEquals(
            listOf("GRANT_DELEGATED_AUTHORITY", "REVOKE_DELEGATED_AUTHORITY"),
            fixture.savedTransitions.map { it.mutation.name })
        assertTrue(fixture.savedTransitions.all { it.partyId == party.id && it.actorId == actor.id })
        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder, times(2)).record(audit.capture())
        assertEquals("information_request.delegated_authority.revoke", audit.lastValue.eventTypeKey)
        assertTrue(audit.allValues.all { it.owner == AuditOwnerScope.Personal(fixture.request.ownerUserId!!) })
        assertTrue(audit.allValues.all { it.actorKind == AuditActorKind.PUBLIC_LINK && it.actorId == actor.id })
        assertTrue(audit.allValues.all { it.sessionId == "verified-session" && it.actorLabel == "participant:${actor.id}" })
        assertTrue(audit.allValues.all { it.payload["requirementId"] == requirement.id.toString() })
        assertFalse(audit.lastValue.payload.values.contains(revoke.reason))
        assertNull(audit.lastValue.reason)
        assertEquals(2, fixture.events.size)
    }

    @Test
    fun `a degraded authority audit refuses the command without recording a receipt or publishing an event`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        whenever(fixture.auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Degraded(
                UUID.randomUUID(),
                "Capture unavailable"
            )
        )

        assertThrows(AuditCaptureFailedException::class.java) {
            fixture.service.grant(
                GrantInformationRequestDelegatedAuthorityCommand(
                    requestId = fixture.request.id,
                    assignedPartyId = party.id,
                    delegatePrincipal = PrincipalRef.user(UUID.randomUUID()),
                    access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(fixture.request)),
                    idempotencyKey = "grant-failed-audit",
                ),
            )
        }
        assertTrue(fixture.receiptStore.receipts.isEmpty())
        assertTrue(fixture.events.isEmpty())
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
        val exchange = Exchange().apply {
            id = request.exchangeId
            ownerOrganizationId = request.ownerOrganizationId
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val authorizationContext = AuthorizationContext(activeOrgId = request.ownerOrganizationId)
        val requestRepository = mock<InformationRequestRepository>()
        val exchangeLookup = mock<ExchangeLookupService>()
        val entitlementGuard = mock<InformationRequestEntitlementGuard>()
        val executionGrantService = mock<InformationRequestExecutionGrantService>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val authorityRepository = mock<InformationRequestDelegatedAuthorityRepository>()
        val authorizationService = mock<AuthorizationService>()
        val receiptStore = InMemoryDelegatedAuthorityReceiptStore()
        val commandReceiptService = CommandReceiptService(receiptStore, CommandReceiptScope())
        val savedTransitions = mutableListOf<InformationRequestTransition>()
        val events = mutableListOf<DomainEvent>()
        val auditRecorder = mock<AuditRecorder>()
        val eligibility = mock<InformationRequestPartyEligibilityService>()
        val requirementRoles = mock<InformationRequestRequirementAuthorizationContextProvider>()
        private val transitionRepository = mock<InformationRequestTransitionRepository>()
        private val publisher = mock<DomainEventPublisher>()
        val transitionHistory = InformationRequestTransitionHistoryService(
            transitionRepository,
            auditRecorder,
            publisher,
            InformationRequestResponseStart(requestRepository, Clock.systemUTC()),
            InformationRequestAuditActorLabelResolver(mock<PrincipalDisplayService>()),
            CommandReceiptScope(),
        )
        val service = InformationRequestDelegatedAuthorityService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            requirementRepository = requirementRepository,
            authorityRepository = authorityRepository,
            authorizationService = authorizationService,
            commandReceiptService = commandReceiptService,
            gate = InformationRequestMutationGate(
                requestRepository,
                exchangeLookup,
                authorizationService,
                entitlementGuard,
                InformationRequestContinuationGuard(executionGrantService, entitlementGuard),
            ),
            authorityRecorder = InformationRequestDelegatedAuthorityRecorder(transitionHistory),
            eligibility = eligibility,
            requirementRoles = requirementRoles,
        )

        init
        {
            whenever(requirementRoles.answeringRoleKeysOf(any())).thenReturn(setOf(InformationRequestShareRoleKey.CONTRIBUTOR))
            whenever(auditRecorder.record(any())).thenReturn(
                AuditCaptureResult.Captured(
                    UUID.randomUUID(),
                    UUID.randomUUID()
                )
            )
            whenever(transitionRepository.nextSequenceNumber(any())).thenAnswer { savedTransitions.size + 1 }
            whenever(transitionRepository.save(any())).thenAnswer {
                it.getArgument<InformationRequestTransition>(0).also(savedTransitions::add)
            }
            doAnswer {
                events += it.getArgument<DomainEvent>(0)
                null
            }.whenever(publisher).publish(any(), any())
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(exchangeLookup.lockExchangeForUpdate(exchange.id)).thenReturn(exchange)
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
            listOf(
                Action.INFORMATION_REQUEST_MANAGE_PARTIES,
                Action.INFORMATION_REQUEST_VIEW_OPERATIONS
            ).forEach { action ->
                whenever(
                    authorizationService.authorize(
                        any(),
                        eq(action),
                        any<ResourceRef>(),
                        any(),
                    ),
                ).thenReturn(Decision.Allow())
            }
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

        fun grantCommand(party: InformationRequestParty, key: String) =
            GrantInformationRequestDelegatedAuthorityCommand(
                requestId = request.id,
                assignedPartyId = party.id,
                delegatePrincipal = PrincipalRef.participant(UUID.randomUUID()),
                access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(request)),
                idempotencyKey = key,
            )

        fun revokeCommand(authority: InformationRequestDelegatedAuthority, key: String) =
            RevokeInformationRequestDelegatedAuthorityCommand(
                requestId = request.id,
                authorityId = authority.id,
                reason = "access-ended",
                access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partiesOf(request)),
                idempotencyKey = key,
            )

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

    override fun lockCommand(request: CommandReceiptRequest) = Unit

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
