package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseLimits
import com.docuhyphen.app.api.model.informationrequest.noauth.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.noauth.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.*
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestEntitlementGuard
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestRespondentKey
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

class InformationRequestParticipantAccountUpgradeServiceTest
{
    @Test
    fun `upgrading a verified participant grants an equivalent User Share without touching the original party`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        val upgrade = fixture.service.upgrade(fixture.command(party, session))

        assertEquals(fixture.appUser.id, upgrade.grantedShare.principalId)
        assertEquals(PrincipalKind.USER, upgrade.grantedShare.principalKind)
        assertEquals(ResourceType.INFORMATION_REQUEST, upgrade.grantedShare.resourceType)
        assertEquals(fixture.request.id, upgrade.grantedShare.resourceId)
        assertEquals(party.roleKey.name, upgrade.grantedShare.roleName)
        assertEquals(PrincipalKind.PARTICIPANT, party.principalKind)
        assertEquals(party.principalId, party.principalId)
    }

    @Test
    fun `upgrading persists a ParticipantAccountLink binding the participant to the App User`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        val upgrade = fixture.service.upgrade(fixture.command(party, session))

        assertEquals(fixture.participant.id, upgrade.participantAccountLink.participantId)
        assertEquals(fixture.appUser.id, upgrade.participantAccountLink.appUserId)
        assertEquals(fixture.request.id, upgrade.participantAccountLink.linkedViaInformationRequestId)
        assertEquals(fixture.shareLink.id, upgrade.participantAccountLink.linkedViaShareLinkId)
        assertNotNull(fixture.savedLinks[upgrade.participantAccountLink.id])
    }

    @Test
    fun `an upgrade records its link cutoff and then the account link under the registering account and session`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        val upgrade = fixture.service.upgrade(fixture.command(party, session))

        assertEquals(listOf("REVOKE_ACCESS_LINK", "LINK_PARTICIPANT_ACCOUNT"), fixture.history.map { it.mutation.name })
        assertEquals(listOf("PARTICIPANT_ACCOUNT_LINKED", null), fixture.history.map { it.reasonCode })
        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder, times(2)).record(audit.capture())
        audit.allValues.forEach { record ->
            assertEquals(fixture.appUser.id, record.actorId)
            assertEquals(AuditActorKind.HUMAN, record.actorKind)
            assertEquals("user:${fixture.appUser.id}", record.actorLabel)
            assertEquals(session.id.toString(), record.sessionId)
            assertEquals(party.id.toString(), record.payload["partyId"])
        }
        assertEquals("information_request.participant_account.link", audit.secondValue.eventTypeKey)
        assertEquals(
            upgrade.participantAccountLink.id.toString(),
            audit.secondValue.payload["participantAccountLinkId"]
        )
        assertEquals(
            "information_request.link_participant_account|${fixture.request.id}|${party.id}|${session.id}",
            audit.secondValue.idempotencyKey,
        )
        verify(fixture.requestRepository).findRequestByIdForUpdate(fixture.request.id)
    }

    @Test
    fun `an upgrade links the granted account Share to the party and advances the party revision`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        val upgrade = fixture.service.upgrade(fixture.command(party, session))

        assertEquals(upgrade.grantedShare.id, party.linkedUserShareId)
        assertEquals(2, party.partyRevision)
        assertEquals(2, fixture.request.partyRevision)
        verify(fixture.partyRepository).update(party)
        verify(fixture.requestRepository).update(fixture.request)
    }

    @Test
    fun `an upgrade is refused while the account already holds live access to the request`() {
        listOf(ShareStatus.ACTIVE, ShareStatus.PENDING_APPROVAL).forEach { liveStatus ->
            val fixture = Fixture()
            val party = fixture.activeParticipantParty()
            val session = fixture.activeSession(party)
            fixture.grantedShares += Share().apply {
                resourceType = ResourceType.INFORMATION_REQUEST
                resourceId = fixture.request.id
                principalKind = PrincipalKind.USER
                principalId = fixture.appUser.id
                roleName = InformationRequestShareRoleKey.REVIEWER.name
                status = liveStatus
            }

            val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.service.upgrade(fixture.command(party, session))
            }

            assertEquals(InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_ACCESS_CONFLICT, refusal.reasonCode)
            assertEquals(1, fixture.grantedShares.size)
            assertTrue(fixture.savedLinks.isEmpty())
            assertNull(party.linkedUserShareId)
            assertEquals(1, party.partyRevision)
            assertEquals(ShareLinkStatus.ACTIVE, fixture.shareLink.status)
        }
    }

    @Test
    fun `an upgrade may reuse the account's ended access to the request`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        fixture.grantedShares += Share().apply {
            resourceType = ResourceType.INFORMATION_REQUEST
            resourceId = fixture.request.id
            principalKind = PrincipalKind.USER
            principalId = fixture.appUser.id
            roleName = InformationRequestShareRoleKey.REVIEWER.name
            status = ShareStatus.REVOKED
        }

        val upgrade = fixture.service.upgrade(fixture.command(party, session))

        assertEquals(upgrade.grantedShare.id, party.linkedUserShareId)
        assertEquals(2, fixture.grantedShares.size)
    }

    @Test
    fun `an upgrade moves every party the participant holds on the request to the account and ends each one's access links`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val (attestor, attestorLink) = fixture.otherParticipantParty(InformationRequestShareRoleKey.ATTESTOR)
        val session = fixture.activeSession(party)

        val upgrade = fixture.service.upgrade(fixture.command(party, session))

        assertEquals(listOf("CONTRIBUTOR", "ATTESTOR"), fixture.grantedShares.map { it.roleName })
        assertEquals(upgrade.grantedShare.id, party.linkedUserShareId)
        assertEquals(fixture.grantedShares.last().id, attestor.linkedUserShareId)
        assertEquals(ShareLinkStatus.REVOKED, attestorLink.status)
        assertEquals(
            listOf("REVOKE_ACCESS_LINK", "LINK_PARTICIPANT_ACCOUNT", "REVOKE_ACCESS_LINK", "LINK_PARTICIPANT_ACCOUNT"),
            fixture.history.map { it.mutation.name },
        )
    }

    @Test
    fun `an upgrade is refused while an earlier Share of the account on the request is live, even when its newest one has ended`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        fixture.grantedShares += fixture.accountShare(InformationRequestShareRoleKey.REVIEWER, ShareStatus.ACTIVE)
        fixture.grantedShares += fixture.accountShare(InformationRequestShareRoleKey.ATTESTOR, ShareStatus.REVOKED)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(fixture.command(party, session))
        }

        assertEquals(InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_ACCESS_CONFLICT, refusal.reasonCode)
        assertNull(party.linkedUserShareId)
        assertTrue(fixture.savedLinks.isEmpty())
    }

    @Test
    fun `a participant who linked one party links a party assigned later without the account's own Share counting as a conflict`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val (earlier, earlierLink) = fixture.otherParticipantParty(InformationRequestShareRoleKey.ATTESTOR)
        val earlierShare = fixture.accountShare(InformationRequestShareRoleKey.ATTESTOR, ShareStatus.ACTIVE)
        fixture.grantedShares += earlierShare
        earlier.linkedUserShareId = earlierShare.id
        earlierLink.status = ShareLinkStatus.REVOKED
        ParticipantAccountLink().apply {
            participantId = fixture.participant.id
            appUserId = fixture.appUser.id
            linkedViaInformationRequestId = fixture.request.id
            linkedViaShareLinkId = earlierLink.id
        }.also { fixture.savedLinks[it.id] = it }
        val session = fixture.activeSession(party)

        val upgrade = fixture.service.upgrade(fixture.command(party, session))

        assertEquals(upgrade.grantedShare.id, party.linkedUserShareId)
        assertEquals(earlierShare.id, earlier.linkedUserShareId)
        assertEquals(listOf(earlierShare.id, upgrade.grantedShare.id), fixture.grantedShares.map { it.id })
    }

    @Test
    fun `a replay answers the account Share granted for the session's own party`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        fixture.otherParticipantParty(InformationRequestShareRoleKey.ATTESTOR)
        val session = fixture.activeSession(party)
        val command = fixture.command(party, session)
        val first = fixture.service.upgrade(command)
        session.revokedAt = Timestamp.from(Instant.now())

        val replayed = fixture.service.upgrade(command)

        assertEquals(first.grantedShare.id, replayed.grantedShare.id)
        assertEquals(party.linkedUserShareId, replayed.grantedShare.id)
    }

    @Test
    fun `upgrading revokes every active bootstrap ShareLink and session bound to the party's Share`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        fixture.service.upgrade(fixture.command(party, session))

        assertEquals(ShareLinkStatus.REVOKED, fixture.shareLink.status)
        verify(fixture.requestAccessSessionService).revokeAllForShareLink(fixture.shareLink.id)
    }

    @Test
    fun `upgrading never creates a temporary App User -- it only reads the one already registered`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        fixture.service.upgrade(fixture.command(party, session))

        verify(fixture.appUserService, only()).getById(fixture.appUser.id)
    }

    @Test
    fun `upgrading never rewrites or revokes the participant's original Share`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        fixture.service.upgrade(fixture.command(party, session))

        verify(fixture.shareService, never()).revoke(any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `upgrading is refused when the registering App User's email does not match the participant's contact address`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        fixture.appUser.email = "someone-else@example.com"

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(fixture.command(party, session))
        }
        assertEquals(InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_EMAIL_MISMATCH, ex.reasonCode)
    }

    @Test
    fun `upgrading is refused when the participant is already linked to a different App User account`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        fixture.savedLinks[UUID.randomUUID()] = ParticipantAccountLink().apply {
            participantId = fixture.participant.id
            appUserId = UUID.randomUUID()
            linkedViaInformationRequestId = UUID.randomUUID()
            linkedViaShareLinkId = UUID.randomUUID()
        }

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(fixture.command(party, session))
        }
        assertEquals(InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_ALREADY_LINKED, ex.reasonCode)
    }

    @Test
    fun `upgrading a second request for an already-linked participant reuses the existing link`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val existingLink = ParticipantAccountLink().apply {
            id = UUID.randomUUID()
            participantId = fixture.participant.id
            appUserId = fixture.appUser.id
            linkedViaInformationRequestId = UUID.randomUUID()
            linkedViaShareLinkId = UUID.randomUUID()
        }
        fixture.savedLinks[existingLink.id] = existingLink

        val upgrade = fixture.service.upgrade(fixture.command(party, session))

        assertEquals(existingLink.id, upgrade.participantAccountLink.id)
        assertEquals(fixture.appUser.id, upgrade.grantedShare.principalId)
    }

    @Test
    fun `upgrading is refused when the session has been revoked`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party).apply { revokedAt = Timestamp.from(Instant.now()) }

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(fixture.command(party, session))
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED, ex.reasonCode)
    }

    @Test
    fun `a retry after the upgrade cut off its own session replays the recorded link`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val command = fixture.command(party, session)
        val first = fixture.service.upgrade(command)
        session.revokedAt = Timestamp.from(Instant.now())

        val replayed = fixture.service.upgrade(command)

        assertEquals(first.participantAccountLink.id, replayed.participantAccountLink.id)
        assertEquals(first.partyETag, replayed.partyETag)
        assertEquals(1, fixture.savedLinks.size)
        assertEquals(1, fixture.grantedShares.size)
    }

    @Test
    fun `an upgrade and its replay name the linked account as the respondent its unsaved answers move to`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val command = fixture.command(party, session)
        val accountKey = InformationRequestRespondentKey.of(PrincipalRef.user(fixture.appUser.id))

        val first = fixture.service.upgrade(command)
        session.revokedAt = Timestamp.from(Instant.now())
        val replayed = fixture.service.upgrade(command)

        assertTrue(accountKey.isNotBlank())
        assertEquals(accountKey, first.accountRespondentKey)
        assertEquals(accountKey, replayed.accountRespondentKey)
        assertNotEquals(
            InformationRequestRespondentKey.of(PrincipalRef.participant(fixture.participant.id)),
            accountKey
        )
    }

    @Test
    fun `a session credential that names another session is refused before any receipt is read`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val other = fixture.activeSession(party)

        assertThrows(ForbiddenException::class.java) {
            fixture.service.upgrade(fixture.command(party, session).copy(sessionCredential = "${other.id}.secret"))
        }

        assertTrue(fixture.receiptStore.receipts.isEmpty())
        assertTrue(fixture.savedLinks.isEmpty())
    }

    @Test
    fun `a session for another request is refused like an unknown request before that request is locked`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val other = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
        }
        whenever(fixture.requestRepository.findById(other.id)).thenReturn(other)

        val elsewhere = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(fixture.command(party, session).copy(requestId = other.id))
        }
        val unknown = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(
                fixture.command(party, session).copy(requestId = UUID.randomUUID(), idempotencyKey = "upgrade-unknown"),
            )
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, elsewhere.reasonCode)
        assertEquals(unknown.message, elsewhere.message)
        verify(fixture.exchangeLookup, never()).lockExchangeForUpdate(other.exchangeId)
        assertTrue(fixture.receiptStore.receipts.isEmpty())
    }

    @Test
    fun `a replay after the party was removed is refused instead of answering access that has ended`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val command = fixture.command(party, session)
        fixture.service.upgrade(command)
        party.active = false

        val refusal =
            assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.upgrade(command) }

        assertEquals(InformationRequestErrorCatalog.PARTY_ALREADY_REVOKED, refusal.reasonCode)
    }

    @Test
    fun `upgrading is refused for a party that is not Participant-held`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty().apply { principalKind = PrincipalKind.USER }
        val session = fixture.activeSession(party)

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.upgrade(fixture.command(party, session))
        }
    }

    @Test
    fun `upgrading requires the current party ETag`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        assertThrows(CommandPreconditionException::class.java) {
            fixture.service.upgrade(fixture.command(party, session, precondition = CommandPrecondition.Absent))
        }
    }

    @Test
    fun `only a signed-in account holder can complete an upgrade`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val application = RequestAccessContext(PrincipalRef.application(UUID.randomUUID()), AuthorizationContext())

        assertThrows(ForbiddenException::class.java) {
            fixture.service.upgrade(fixture.command(party, session).copy(access = application))
        }

        assertTrue(fixture.savedLinks.isEmpty())
        assertTrue(fixture.grantedShares.isEmpty())
    }

    @Test
    fun `an upgrade is refused once the parent Exchange has ended or the request has finished`() {
        val ended = Fixture().apply { exchange.status = ExchangeStatus.ENDED }
        val endedParty = ended.activeParticipantParty()
        assertThrows(InformationRequestLifecycleException::class.java) {
            ended.service.upgrade(ended.command(endedParty, ended.activeSession(endedParty)))
        }
        val finished = Fixture().apply { request.state = InformationRequestState.CLOSED }
        val finishedParty = finished.activeParticipantParty()
        assertThrows(InformationRequestLifecycleException::class.java) {
            finished.service.upgrade(finished.command(finishedParty, finished.activeSession(finishedParty)))
        }

        assertTrue(ended.grantedShares.isEmpty())
        assertTrue(finished.grantedShares.isEmpty())
    }

    @Test
    fun `an upgrade is refused while the account organization has a suspended trust relationship with the owner`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val accountOrganizationId = UUID.randomUUID()
        val relationshipId = UUID.randomUUID()
        whenever(fixture.membershipService.activeOrganizationIds(fixture.appUser.id)).thenReturn(
            setOf(
                accountOrganizationId
            )
        )
        whenever(
            fixture.trustValidation.relationshipIdForLookup(
                requireNotNull(fixture.request.ownerOrganizationId),
                accountOrganizationId
            )
        )
            .thenReturn(relationshipId)
        whenever(fixture.trustValidation.isRelationshipSuspended(relationshipId)).thenReturn(true)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(fixture.command(party, session))
        }

        assertEquals(InformationRequestErrorCatalog.TRUST_SUSPENDED, refusal.reasonCode)
        assertTrue(fixture.grantedShares.isEmpty())
    }

    @Test
    fun `an upgrade is refused when the owner's sharing rules do not admit the account`() {
        val fixture = Fixture()
        val assigner = UUID.randomUUID()
        val party = fixture.activeParticipantParty().apply {
            assignedByPrincipalKind = PrincipalKind.USER
            assignedByPrincipalId = assigner
        }
        val session = fixture.activeSession(party)
        doThrow(IllegalArgumentException("Sharing with this organization is not permitted"))
            .whenever(fixture.sharingPolicy)
            .assertCanShareWithUser(fixture.request.ownerOrganizationId, assigner, fixture.appUser.id)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(fixture.command(party, session))
        }

        assertEquals(InformationRequestErrorCatalog.PARTY_SHARING_NOT_PERMITTED, refusal.reasonCode)
        assertTrue(fixture.grantedShares.isEmpty())
        assertTrue(fixture.savedLinks.isEmpty())
        assertNull(party.linkedUserShareId)
    }

    @Test
    fun `an upgrade needs a participant whose contact address was verified`() {
        val fixture = Fixture()
        fixture.participant.emailVerifiedAt = null
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(fixture.command(party, session))
        }

        assertEquals(InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED, refusal.reasonCode)
    }

    @Test
    fun `an upgrade returns the party ETag it produced and its replay returns the same one`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val command = fixture.command(party, session)

        val first = fixture.service.upgrade(command)
        val replayed = fixture.service.upgrade(command)

        assertEquals(InformationRequestETag.partyOf(party), first.partyETag)
        assertEquals(first.partyETag, replayed.partyETag)
    }

    @Test
    fun `a retried upgrade answers the party ETag as it stands after a later change`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val command = fixture.command(party, session)
        val first = fixture.service.upgrade(command)
        party.partyRevision += 1

        val replayed = fixture.service.upgrade(command)

        assertNotEquals(first.partyETag, replayed.partyETag)
        assertEquals(InformationRequestETag.partyOf(party), replayed.partyETag)
    }

    @Test
    fun `a repeated upgrade under the same idempotency key replays the same result`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val command = fixture.command(party, session)

        val first = fixture.service.upgrade(command)
        val second = fixture.service.upgrade(command)

        assertEquals(first.participantAccountLink.id, second.participantAccountLink.id)
        assertEquals(1, fixture.savedLinks.size)
    }

    @Test
    fun `reusing an upgrade command key from another signed-in account is refused as a receipt conflict`() {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        fixture.service.upgrade(fixture.command(party, session, idempotencyKey = "shared-upgrade-key"))

        assertThrows(CommandReceiptConflictException::class.java) {
            fixture.service.upgrade(
                fixture.command(party, session, idempotencyKey = "shared-upgrade-key")
                    .copy(access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())),
            )
        }
    }

    private class Fixture
    {
        val savedLinks = mutableMapOf<UUID, ParticipantAccountLink>()
        val grantedShares = mutableListOf<Share>()

        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
        }

        val participant = ExternalParticipant().apply {
            id = UUID.randomUUID()
            ownerOrganizationId = request.ownerOrganizationId
            email = "respondent@example.com"
            emailLower = "respondent@example.com"
            emailVerifiedAt = Timestamp.from(Instant.now())
        }

        val appUser = AppUser().apply {
            id = UUID.randomUUID()
            email = "respondent@example.com"
        }

        val shareLink = ShareLink().apply {
            id = UUID.randomUUID()
            shareId = UUID.randomUUID()
            tokenHash = "seed-hash"
            linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
            status = ShareLinkStatus.ACTIVE
        }

        val exchange = Exchange().apply {
            id = request.exchangeId
            ownerOrganizationId = request.ownerOrganizationId
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
        }
        val bootstrapLinks = mutableListOf(shareLink)
        val parties = mutableListOf<InformationRequestParty>()
        val requestRepository = mock<InformationRequestRepository>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val shareLinks = mock<ShareLinkService>()
        val externalParticipantService = mock<ExternalParticipantService>()
        val exchangeLookup = mock<ExchangeLookupService>()
        val trustValidation = mock<TrustedRecipientValidationService>()
        val membershipService = mock<OrganizationMembershipService>()
        val sharingPolicy = mock<OrganizationExchangePolicyService>()
        val appUserService = mock<AppUserService>()
        val shareService = mock<ShareService>()
        val participantAccountLinkRepository = mock<ParticipantAccountLinkRepository>()
        val requestAccessSessionService = mock<RequestAccessSessionService>()
        val receiptStore = InMemoryUpgradeCommandReceiptStore()
        val commandReceiptService = CommandReceiptService(receiptStore, CommandReceiptScope())
        val transitions = mock<InformationRequestTransitionRepository>()
        val history = mutableListOf<InformationRequestTransition>()
        val auditRecorder = mock<AuditRecorder>()
        val transitionHistory = InformationRequestTransitionHistoryService(
            transitions,
            auditRecorder,
            mock<DomainEventPublisher>(),
            mock<InformationRequestResponseStart>(),
            InformationRequestAuditActorLabelResolver(mock<PrincipalDisplayService>()),
            CommandReceiptScope(),
        )
        val bootstrapShareLinkService = InformationRequestBootstrapShareLinkService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            exchangeLookup = mock<ExchangeLookupService>(),
            shareLinks = shareLinks,
            authorizationService = mock<AuthorizationService>(),
            commandReceiptService = commandReceiptService,
            requestAccessSessionService = requestAccessSessionService,
            abuseLimits = InformationRequestAbuseLimits(accessLinkLifetime = Duration.ofDays(30), accessLinkUses = 25),
            accessLinkRecorder = InformationRequestAccessLinkRecorder(transitionHistory),
            continuationGuard = mock(),
        )

        val service = InformationRequestParticipantAccountUpgradeService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            shareLinks = shareLinks,
            externalParticipantService = externalParticipantService,
            appUserService = appUserService,
            shareService = shareService,
            participantAccountLinkRepository = participantAccountLinkRepository,
            requestAccessSessionService = requestAccessSessionService,
            commandReceiptService = commandReceiptService,
            bootstrapShareLinkService = bootstrapShareLinkService,
            transitionHistory = transitionHistory,
            gate = InformationRequestMutationGate(
                requestRepository,
                exchangeLookup,
                mock<AuthorizationService>(),
                mock<InformationRequestEntitlementGuard>(),
                mock<InformationRequestContinuationGuard>(),
            ),
            trustValidation = trustValidation,
            membershipService = membershipService,
            sharingPolicy = sharingPolicy,
        )

        init
        {
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(transitions.nextSequenceNumber(request.id)).thenAnswer { history.size + 1 }
            whenever(transitions.save(any())).thenAnswer {
                it.getArgument<InformationRequestTransition>(0).also(history::add)
            }
            whenever(auditRecorder.record(any())).thenReturn(
                AuditCaptureResult.Captured(
                    UUID.randomUUID(),
                    UUID.randomUUID()
                )
            )
            whenever(shareLinks.lockShareLinkForUpdate(shareLink.id)).thenReturn(shareLink)
            whenever(partyRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(requestRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(shareLinks.update(any())).thenAnswer { it.getArgument(0) }
            whenever(shareLinks.activeBootstrapLinksForShare(any())).thenAnswer { invocation ->
                bootstrapLinks.filter {
                    it.shareId == invocation.getArgument<UUID>(0) && it.status != ShareLinkStatus.REVOKED
                }
            }
            whenever(partyRepository.findActiveForRequest(request.id)).thenAnswer { parties.filter { it.active } }
            whenever(externalParticipantService.findOwned(eq(participant.id), any())).thenReturn(participant)
            whenever(exchangeLookup.lockExchangeForUpdate(exchange.id)).thenReturn(exchange)
            whenever(membershipService.activeOrganizationIds(any())).thenReturn(emptySet())
            whenever(appUserService.getById(appUser.id)).thenReturn(appUser)
            whenever(participantAccountLinkRepository.findByParticipantId(any())).thenAnswer { invocation ->
                savedLinks.values.firstOrNull { it.participantId == invocation.getArgument<UUID>(0) }
            }
            whenever(participantAccountLinkRepository.save(any())).thenAnswer {
                it.getArgument<ParticipantAccountLink>(0).also { link -> savedLinks[link.id] = link }
            }
            whenever(participantAccountLinkRepository.findById(any())).thenAnswer {
                savedLinks[it.getArgument(0)]
            }
            whenever(
                shareService.grantAdditionalRoleKeyWithPrincipalProvenance(
                    any(), any(), any(), any(), any(), anyOrNull(), anyOrNull(),
                ),
            ).thenAnswer { invocation ->
                Share().apply {
                    resourceType = invocation.getArgument(0)
                    resourceId = invocation.getArgument(1)
                    principalKind = invocation.getArgument(2)
                    principalId = invocation.getArgument(3)
                    roleName = invocation.getArgument(4)
                }.also { grantedShares += it }
            }
            whenever(shareService.findDirectSharesForPrincipalOnResource(any(), any(), any(), any())).thenAnswer {
                grantedShares.reversed()
            }
            whenever(shareService.getById(any())).thenAnswer { invocation ->
                grantedShares.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
            }
            whenever(shareLinks.findShareLink(any())).thenAnswer { invocation ->
                bootstrapLinks.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
            }
        }

        fun activeParticipantParty(): InformationRequestParty =
            InformationRequestParty().apply {
                informationRequestId = request.id
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
                principalKind = PrincipalKind.PARTICIPANT
                principalId = participant.id
                shareId = shareLink.shareId
                active = true
                partyRevision = 1
            }.also { party ->
                parties += party
                whenever(partyRepository.findByShareId(requireNotNull(party.shareId))).thenReturn(party)
            }

        fun otherParticipantParty(roleKey: InformationRequestShareRoleKey): Pair<InformationRequestParty, ShareLink> {
            val link = ShareLink().apply {
                id = UUID.randomUUID()
                shareId = UUID.randomUUID()
                tokenHash = "other-seed-hash"
                linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
                status = ShareLinkStatus.ACTIVE
            }.also(bootstrapLinks::add)
            val party = InformationRequestParty().apply {
                informationRequestId = request.id
                this.roleKey = roleKey
                principalKind = PrincipalKind.PARTICIPANT
                principalId = participant.id
                shareId = link.shareId
                active = true
                partyRevision = 1
            }.also { parties += it }
            whenever(partyRepository.findByShareId(link.shareId)).thenReturn(party)
            return party to link
        }

        fun accountShare(roleKey: InformationRequestShareRoleKey, shareStatus: ShareStatus) = Share().apply {
            resourceType = ResourceType.INFORMATION_REQUEST
            resourceId = request.id
            principalKind = PrincipalKind.USER
            principalId = appUser.id
            roleName = roleKey.name
            status = shareStatus
        }

        fun activeSession(party: InformationRequestParty): RequestAccessSession =
            RequestAccessSession().apply {
                id = UUID.randomUUID()
                shareLinkId = shareLink.id
                participantPrincipalKind = requireNotNull(party.principalKind)
                participantPrincipalId = requireNotNull(party.principalId)
            }.also { session ->
                whenever(requestAccessSessionService.verifyCredential("${session.id}.secret")).thenReturn(session)
                whenever(requestAccessSessionService.requireActive(session.id)).thenAnswer {
                    if (session.revokedAt != null)
                    {
                        throw InformationRequestLifecycleException(
                            InformationRequestErrorCatalog.ACCESS_SESSION_REVOKED,
                            "revoked",
                        )
                    }
                    session
                }
            }

        fun command(
            party: InformationRequestParty,
            session: RequestAccessSession,
            precondition: CommandPrecondition = CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partyOf(party),
            ),
            idempotencyKey: String = "upgrade-${party.id}",
        ) = UpgradeInformationRequestParticipantAccountCommand(
            requestId = request.id,
            sessionId = session.id,
            sessionCredential = "${session.id}.secret",
            access = RequestAccessContext(PrincipalRef.user(appUser.id), AuthorizationContext()),
            precondition = precondition,
            idempotencyKey = idempotencyKey,
        )
    }
}

private class InMemoryUpgradeCommandReceiptStore : CommandReceiptStore
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
