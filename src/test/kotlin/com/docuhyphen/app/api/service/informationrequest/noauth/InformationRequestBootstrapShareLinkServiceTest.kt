package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.command.CommandReceiptRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.*
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditActorLabelResolver
import com.docuhyphen.app.api.service.informationrequest.audit.InformationRequestAuditPayloadPolicy
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStart
import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

class InformationRequestBootstrapShareLinkServiceTest
{
    @Test
    fun `all bootstrap commands record correlated party history audit and events without credential material`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val issued = fixture.service.issue(fixture.command(party.id))
        val rotated = fixture.service.rotate(fixture.rotateCommand(issued.shareLink, party))
        val replaced = fixture.service.replace(fixture.replaceCommand(rotated.shareLink, party))
        fixture.service.revoke(fixture.revokeCommand(replaced.shareLink, party))

        assertEquals(
            listOf("ISSUE_ACCESS_LINK", "ROTATE_ACCESS_LINK", "REPLACE_ACCESS_LINK", "REVOKE_ACCESS_LINK"),
            fixture.history.map { it.mutation.name })
        val audit = argumentCaptor<AuditEventDraft>()
        val event = argumentCaptor<DomainEvent>()
        val owner = argumentCaptor<AuditOwnerScope>()
        verify(fixture.auditRecorder, times(4)).record(audit.capture())
        verify(fixture.publisher, times(4)).publish(event.capture(), owner.capture())
        assertEquals(
            listOf("issue", "rotate", "replace", "revoke").map { "information_request.access_link.$it" },
            audit.allValues.map { it.eventTypeKey })
        audit.allValues.zip(event.allValues).forEachIndexed { index, (record, published) ->
            assertEquals(fixture.history[index].id.toString(), record.businessTransactionId)
            assertEquals(record.eventId.toString(), published.id)
            assertEquals(record.payload, published.payload)
            assertEquals(party.id.toString(), published.payload["partyId"])
            assertEquals(fixture.actor.id, record.actorId)
            assertEquals(MANAGER_LABEL, record.actorLabel)
            assertEquals("bootstrap-manager-session", record.sessionId)
            assertEquals(
                AuditOwnerScope.Organization(requireNotNull(fixture.request.ownerOrganizationId)),
                record.owner
            )
            assertEquals(record.owner, owner.allValues[index])
            assertEquals(
                InformationRequestTransitionHistoryService.orderingKeyOf(fixture.request.id),
                published.orderingKey
            )
            assertEquals(
                0,
                InformationRequestAuditPayloadPolicy.projectionOf(published.payload) { _ -> true }.withheldKeyCount
            )
            val serialized = published.payload.toString()
            listOf(
                issued.rawToken,
                rotated.rawToken,
                replaced.rawToken,
                issued.shareLink.tokenHash,
                replaced.shareLink.tokenHash
            ).forEach {
                assertFalse(serialized.contains(it))
            }
        }
        assertEquals(issued.shareLink.id.toString(), event.allValues[2].payload["replacesShareLinkId"])
        assertEquals(replaced.shareLink.id.toString(), event.allValues[2].payload["shareLinkId"])
    }

    @Test
    fun `bootstrap origin on a Personal request records participant identity and the non-secret session reference`() {
        val fixture = Fixture()
        fixture.request.ownerType = InformationRequestOwnerType.USER
        fixture.request.ownerUserId = UUID.randomUUID()
        fixture.request.ownerOrganizationId = null
        val party = fixture.activeActingParty()
        val principal = PrincipalRef.participant(requireNotNull(party.principalId))
        val access = RequestAccessContext(principal, AuthorizationContext(sessionRef = "verified-session-reference"))

        fixture.service.issue(fixture.command(party.id).copy(access = access))

        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder).record(audit.capture())
        assertEquals(AuditActorKind.PUBLIC_LINK, audit.firstValue.actorKind)
        assertEquals(principal.id, audit.firstValue.actorId)
        assertEquals("participant:${principal.id}", audit.firstValue.actorLabel)
        assertEquals("verified-session-reference", audit.firstValue.sessionId)
        assertEquals(AuditOwnerScope.Personal(requireNotNull(fixture.request.ownerUserId)), audit.firstValue.owner)
        assertEquals(InformationRequestTransitionActorKind.PARTICIPANT, fixture.history.single().actorKind)
    }

    @Test
    fun `secret command replays and no-op revocations add no recording duplicates`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val command = fixture.command(party.id)
        val issued = fixture.service.issue(command)
        assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.issue(command) }
        val rotate = fixture.rotateCommand(issued.shareLink, party)
        fixture.service.rotate(rotate)
        assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.rotate(rotate) }
        val replace = fixture.replaceCommand(issued.shareLink, party)
        val replacement = fixture.service.replace(replace)
        assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.replace(replace) }
        val revoke = fixture.revokeCommand(replacement.shareLink, party)
        fixture.service.revoke(revoke)
        fixture.service.revoke(revoke)
        fixture.service.revoke(
            revoke.copy(
                idempotencyKey = "second-revocation",
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(party)),
            ),
        )

        assertEquals(4, fixture.history.size)
        verify(fixture.auditRecorder, times(4)).record(any())
        verify(fixture.publisher, times(4)).publish(any(), any())
    }

    @Test
    fun `a degraded audit refuses issuance rather than returning a credential with missing records`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        whenever(fixture.auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Degraded(
                UUID.randomUUID(),
                "unavailable"
            )
        )

        assertThrows(AuditCaptureFailedException::class.java) { fixture.service.issue(fixture.command(party.id)) }
        assertTrue(fixture.receiptStore.receipts.isEmpty())
        verifyNoInteractions(fixture.publisher)
    }

    @Test
    fun `a retried revocation answers the party ETag as it stands after a later change`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val link = fixture.activeBootstrapShareLink(party)
        val command = fixture.revokeCommand(link, party)
        val revoked = fixture.service.revoke(command)
        party.partyRevision += 1

        val replayed = fixture.service.revoke(command)

        assertNotEquals(revoked.partyETag, replayed.partyETag)
        assertEquals(InformationRequestETag.partyOf(party), replayed.partyETag)
        assertEquals(ShareLinkStatus.REVOKED, replayed.shareLink.status)
    }

    @Test
    fun `revocation replay rechecks the manager's current authority`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val link = fixture.activeBootstrapShareLink(party)
        val command = fixture.revokeCommand(link, party)
        fixture.service.revoke(command)
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "denied"))

        assertThrows(io.quarkus.security.ForbiddenException::class.java) { fixture.service.revoke(command) }
    }

    @Test
    fun `reusing an access link command key for a different change is refused as a receipt conflict`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        fixture.service.issue(fixture.command(party.id, idempotencyKey = "shared-issue-key"))
        assertThrows(CommandReceiptConflictException::class.java) {
            fixture.service.issue(
                fixture.command(
                    party.id,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(party)),
                    idempotencyKey = "shared-issue-key",
                ).copy(maxUses = 3),
            )
        }

        val first = fixture.activeBootstrapShareLink(party)
        val second = fixture.activeBootstrapShareLink(party)
        fixture.service.rotate(fixture.rotateCommand(first, party, idempotencyKey = "shared-rotate-key"))
        assertThrows(CommandReceiptConflictException::class.java) {
            fixture.service.rotate(
                fixture.rotateCommand(
                    second,
                    party,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(party)),
                    idempotencyKey = "shared-rotate-key",
                ),
            )
        }
        fixture.service.replace(
            fixture.replaceCommand(
                first,
                party,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(party)),
                idempotencyKey = "shared-replace-key",
            ),
        )
        assertThrows(CommandReceiptConflictException::class.java) {
            fixture.service.replace(
                fixture.replaceCommand(
                    second,
                    party,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(party)),
                    idempotencyKey = "shared-replace-key",
                ),
            )
        }
        val third = fixture.activeBootstrapShareLink(party)
        val fourth = fixture.activeBootstrapShareLink(party)
        fixture.service.revoke(
            fixture.revokeCommand(
                third,
                party,
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(party)),
                idempotencyKey = "shared-revoke-key",
            ),
        )
        assertThrows(CommandReceiptConflictException::class.java) {
            fixture.service.revoke(
                fixture.revokeCommand(
                    fourth,
                    party,
                    precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(party)),
                    idempotencyKey = "shared-revoke-key",
                ),
            )
        }
    }

    @Test
    fun `different managers may reuse a command key without colliding in the audit or event namespace`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val link = fixture.activeBootstrapShareLink(party)
        val command = fixture.rotateCommand(link, party, idempotencyKey = "shared-manager-key")
        fixture.service.rotate(command)
        fixture.service.rotate(
            command.copy(
                access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), fixture.authorizationContext),
                precondition = CommandPrecondition.ExpectedRevision(InformationRequestETag.partyOf(party)),
            ),
        )

        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder, times(2)).record(audit.capture())
        assertNotEquals(audit.firstValue.idempotencyKey, audit.secondValue.idempotencyKey)
        assertTrue(audit.allValues.none { requireNotNull(it.idempotencyKey).contains("shared-manager-key") })
    }

    @Test
    fun `issuing an access link for an eligible acting party creates a bootstrap ShareLink bound to the party's Share`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()

        val issuance = fixture.service.issue(fixture.command(party.id))

        assertEquals(party.shareId, issuance.shareLink.shareId)
        assertEquals(ShareLinkMode.VERIFICATION_BOOTSTRAP, issuance.shareLink.linkMode)
        assertTrue(issuance.rawToken.isNotBlank())
        assertEquals(sha256Hex(issuance.rawToken), issuance.shareLink.tokenHash)
    }

    @Test
    fun `the author lists each party's access links without their secrets and a caller who may not manage parties cannot`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val issuance = fixture.service.issue(fixture.command(party.id))

        val links = fixture.service.links(fixture.request.id, fixture.access)
        whenever(fixture.authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "denied"))

        assertEquals(listOf(issuance.shareLink.id to party.id), links.map { it.shareLink.id to it.partyId })
        assertThrows(io.quarkus.security.ForbiddenException::class.java) {
            fixture.service.links(fixture.request.id, fixture.access)
        }
    }

    @Test
    fun `a link issued or replaced without an expiry or use limit takes the configured defaults`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val before = Instant.now()

        val issued = fixture.service.issue(fixture.command(party.id)).shareLink
        val replaced =
            fixture.service.replace(fixture.replaceCommand(fixture.activeBootstrapShareLink(party), party)).shareLink

        listOf(issued, replaced).forEach { link ->
            assertEquals(25, link.maxUses)
            val expiresAt = requireNotNull(link.expiresAt).toInstant()
            assertFalse(expiresAt.isBefore(before.plus(Duration.ofDays(30))))
            assertFalse(expiresAt.isAfter(Instant.now().plus(Duration.ofDays(30))))
        }
    }

    @Test
    fun `an author's own expiry and use limit are kept`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val expiresAt = Timestamp.from(Instant.now().plus(Duration.ofDays(2)))

        val link = fixture.service.issue(fixture.command(party.id).copy(expiresAt = expiresAt, maxUses = 3)).shareLink

        assertEquals(expiresAt, link.expiresAt)
        assertEquals(3, link.maxUses)
    }

    @Test
    fun `issuance is refused when the parent Exchange requires recipient sign-in`()
    {
        val fixture = Fixture()
        fixture.exchange.requireRecipientSignIn = true
        val party = fixture.activeActingParty()

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issue(fixture.command(party.id))
        }
        assertEquals(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, ex.reasonCode)
    }

    @Test
    fun `rotation and replacement are refused once the parent Exchange requires recipient sign-in`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party)
        val tokenHash = shareLink.tokenHash
        fixture.exchange.requireRecipientSignIn = true

        val rotation = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.rotate(fixture.rotateCommand(shareLink, party))
        }
        val replacement = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.replace(fixture.replaceCommand(shareLink, party))
        }

        assertEquals(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, rotation.reasonCode)
        assertEquals(InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED, replacement.reasonCode)
        assertEquals(tokenHash, shareLink.tokenHash)
        assertEquals(ShareLinkStatus.ACTIVE, shareLink.status)
        verify(fixture.requestAccessSessionService, never()).revokeAllForShareLink(any())
    }

    @Test
    fun `issuance is refused for the subject party`()
    {
        val fixture = Fixture()
        val subject = InformationRequestParty().apply {
            informationRequestId = fixture.request.id
            roleKey = InformationRequestShareRoleKey.SUBJECT
            active = true
        }.also { fixture.saveParty(it) }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issue(fixture.command(subject.id))
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE, refusal.reasonCode)
    }

    @Test
    fun `issuance is refused for a party with no request-party Share yet`()
    {
        val fixture = Fixture()
        val party = InformationRequestParty().apply {
            informationRequestId = fixture.request.id
            roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
            principalKind = PrincipalKind.PARTICIPANT
            principalId = UUID.randomUUID()
            active = true
            shareId = null
        }.also { fixture.saveParty(it) }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issue(fixture.command(party.id))
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE, refusal.reasonCode)
    }

    @Test
    fun `issuance is refused for a revoked party`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty().apply { active = false }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issue(fixture.command(party.id))
        }
        assertEquals(InformationRequestErrorCatalog.PARTY_ALREADY_REVOKED, refusal.reasonCode)
    }

    @Test
    fun `a direct link outside the request is answered like an unknown link and one of its own states why`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val foreign = ShareLink().apply {
            shareId = UUID.randomUUID()
            tokenHash = "foreign-${UUID.randomUUID()}"
            linkMode = ShareLinkMode.DIRECT_GRANT
            status = ShareLinkStatus.ACTIVE
        }.also { fixture.savedShareLinks += it }
        val own = ShareLink().apply {
            shareId = requireNotNull(party.shareId)
            tokenHash = "own-${UUID.randomUUID()}"
            linkMode = ShareLinkMode.DIRECT_GRANT
            status = ShareLinkStatus.ACTIVE
        }.also { fixture.savedShareLinks += it }

        val outside = assertThrows(IllegalArgumentException::class.java) {
            fixture.service.revoke(fixture.revokeCommand(foreign, party))
        }
        val unknown = assertThrows(IllegalArgumentException::class.java) {
            fixture.service.revoke(fixture.revokeCommand(ShareLink().apply { shareId = UUID.randomUUID() }, party))
        }
        val inside = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.revoke(fixture.revokeCommand(own, party))
        }

        assertEquals(unknown.message, outside.message)
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, inside.reasonCode)
    }

    @Test
    fun `issuance is denied when the caller lacks manage-parties capability`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        whenever(
            fixture.authorizationService.authorize(any(), any(), any(), any()),
        ).thenReturn(Decision.Deny("DENIED", "no"))

        assertThrows(io.quarkus.security.ForbiddenException::class.java) {
            fixture.service.issue(fixture.command(party.id))
        }
    }

    @Test
    fun `issuance requires the current party ETag`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()

        assertThrows(CommandPreconditionException::class.java) {
            fixture.service.issue(fixture.command(party.id, precondition = CommandPrecondition.Absent))
        }
    }

    @Test
    fun `a repeated issuance under the same idempotency key does not mint a second ShareLink`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val command = fixture.command(party.id)

        fixture.service.issue(command)
        val retry = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.issue(command)
        }

        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_ALREADY_ISSUED, retry.reasonCode)
        assertEquals(1, fixture.savedShareLinks.size)
    }

    @Test
    fun `rotating an active bootstrap link mints a new token, resets used count, and clears any outstanding challenge`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party).apply {
            usedCount = 3
            contactOtpHash = "stale-hash"
            contactOtpExpiresAt = Timestamp.from(Instant.now().plusSeconds(60))
        }

        val issuance = fixture.service.rotate(fixture.rotateCommand(shareLink, party))

        assertEquals(shareLink.id, issuance.shareLink.id)
        assertEquals(sha256Hex(issuance.rawToken), issuance.shareLink.tokenHash)
        assertEquals(0, issuance.shareLink.usedCount)
        assertNull(issuance.shareLink.contactOtpHash)
        assertNull(issuance.shareLink.contactOtpExpiresAt)
        assertEquals(1, issuance.shareLink.rotationCount)
        assertNotNull(issuance.shareLink.rotatedAt)
        verify(fixture.requestAccessSessionService).revokeAllForShareLink(shareLink.id)
    }

    @Test
    fun `rotating a revoked bootstrap link is refused`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party).apply { status = ShareLinkStatus.REVOKED }

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.rotate(fixture.rotateCommand(shareLink, party))
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_REVOKED, ex.reasonCode)
    }

    @Test
    fun `rotating an expired bootstrap link is refused`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party).apply {
            expiresAt = Timestamp.from(Instant.now().minusSeconds(60))
        }

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.rotate(fixture.rotateCommand(shareLink, party))
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_EXPIRED, ex.reasonCode)
    }

    @Test
    fun `rotating a direct-grant ShareLink is refused`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party).apply { linkMode = ShareLinkMode.DIRECT_GRANT }

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.rotate(fixture.rotateCommand(shareLink, party))
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_INVALID, refusal.reasonCode)
    }

    @Test
    fun `rotating denies when the caller lacks manage-parties capability`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party)
        whenever(
            fixture.authorizationService.authorize(any(), any(), any(), any()),
        ).thenReturn(Decision.Deny("DENIED", "no"))

        assertThrows(io.quarkus.security.ForbiddenException::class.java) {
            fixture.service.rotate(fixture.rotateCommand(shareLink, party))
        }
    }

    @Test
    fun `rotating requires the current party ETag`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party)

        assertThrows(CommandPreconditionException::class.java) {
            fixture.service.rotate(fixture.rotateCommand(shareLink, party, precondition = CommandPrecondition.Absent))
        }
    }

    @Test
    fun `a repeated rotation under the same idempotency key cannot recover the new token`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party)
        val command = fixture.rotateCommand(shareLink, party)

        fixture.service.rotate(command)
        val retry = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.rotate(command)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_ALREADY_ROTATED, retry.reasonCode)
    }

    @Test
    fun `replacing an active bootstrap link revokes the old link and issues a new one bound to the same Share`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val oldLink = fixture.activeBootstrapShareLink(party)

        val issuance = fixture.service.replace(fixture.replaceCommand(oldLink, party))

        assertEquals(ShareLinkStatus.REVOKED, oldLink.status)
        assertNotNull(oldLink.revokedAt)
        assertEquals(issuance.shareLink.createdAt, oldLink.revokedAt)
        assertEquals(party.shareId, issuance.shareLink.shareId)
        assertEquals(oldLink.id, issuance.shareLink.replacesShareLinkId)
        assertEquals(ShareLinkStatus.ACTIVE, issuance.shareLink.status)
        assertNotEquals(oldLink.id, issuance.shareLink.id)
        verify(fixture.requestAccessSessionService).revokeAllForShareLink(oldLink.id)
    }

    @Test
    fun `issued and replacement links name the authorized principal that created them`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val application = PrincipalRef.application(UUID.randomUUID())
        val serviceAccount = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID.randomUUID())

        val issued = fixture.service.issue(
            fixture.command(party.id).copy(access = RequestAccessContext(application, fixture.authorizationContext)),
        )
        val replaced = fixture.service.replace(
            fixture.replaceCommand(issued.shareLink, party)
                .copy(access = RequestAccessContext(serviceAccount, fixture.authorizationContext)),
        )

        assertEquals(PrincipalKind.APPLICATION, issued.shareLink.createdByPrincipalKind)
        assertEquals(application.id, issued.shareLink.createdByPrincipalId)
        assertEquals(PrincipalKind.SERVICE_ACCOUNT, replaced.shareLink.createdByPrincipalKind)
        assertEquals(serviceAccount.id, replaced.shareLink.createdByPrincipalId)
    }

    @Test
    fun `access links are refused for parties that sign in with an account`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty().apply { principalKind = PrincipalKind.USER }
        val link = fixture.activeBootstrapShareLink(party)

        val refusals = listOf(
            assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.issue(fixture.command(party.id)) },
            assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.service.rotate(
                    fixture.rotateCommand(
                        link,
                        party
                    )
                )
            },
            assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.service.replace(
                    fixture.replaceCommand(
                        link,
                        party
                    )
                )
            },
        )

        assertTrue(refusals.all { it.reasonCode == InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE })
        assertEquals(
            ShareLinkStatus.REVOKED,
            fixture.service.revoke(fixture.revokeCommand(link, party)).shareLink.status
        )
    }

    @Test
    fun `no access link is issued, resent, or replaced for a participant who linked an account`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty().apply { linkedUserShareId = UUID.randomUUID() }
        val link = fixture.activeBootstrapShareLink(party)

        val refusals = listOf(
            assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.issue(fixture.command(party.id)) },
            assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.service.rotate(fixture.rotateCommand(link, party))
            },
            assertThrows(InformationRequestLifecycleException::class.java) {
                fixture.service.replace(fixture.replaceCommand(link, party))
            },
        )

        assertEquals(
            List(3) { InformationRequestErrorCatalog.ACCESS_LINK_PARTY_ACCOUNT_LINKED },
            refusals.map { it.reasonCode },
        )
        assertEquals(
            ShareLinkStatus.REVOKED,
            fixture.service.revoke(fixture.revokeCommand(link, party)).shareLink.status
        )
    }

    @Test
    fun `each link change advances the party revision and returns the party ETag`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()

        val issued = fixture.service.issue(fixture.command(party.id))
        assertEquals(2, party.partyRevision)
        assertEquals(InformationRequestETag.partyOf(party), issued.partyETag)
        val rotated = fixture.service.rotate(fixture.rotateCommand(issued.shareLink, party))
        assertEquals(3, party.partyRevision)
        assertEquals(InformationRequestETag.partyOf(party), rotated.partyETag)
        val replaced = fixture.service.replace(fixture.replaceCommand(rotated.shareLink, party))
        assertEquals(4, party.partyRevision)
        assertEquals(InformationRequestETag.partyOf(party), replaced.partyETag)

        assertThrows(CommandPreconditionException::class.java) {
            fixture.service.revoke(
                fixture.revokeCommand(
                    replaced.shareLink,
                    party,
                    CommandPrecondition.ExpectedRevision(issued.partyETag),
                    "revoke-with-stale-tag",
                ),
            )
        }
        val revoked = fixture.service.revoke(fixture.revokeCommand(replaced.shareLink, party))
        assertEquals(5, party.partyRevision)
        assertEquals(InformationRequestETag.partyOf(party), revoked.partyETag)
        val replayed = fixture.service.revoke(fixture.revokeCommand(replaced.shareLink, party))
        assertEquals(revoked.partyETag, replayed.partyETag)
        assertEquals(5, party.partyRevision)
    }

    @Test
    fun `link commands lock the party before the link`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val link = fixture.activeBootstrapShareLink(party)

        fixture.service.rotate(fixture.rotateCommand(link, party))

        val locks = inOrder(fixture.partyRepository, fixture.shareLinks)
        locks.verify(fixture.partyRepository).findByIdForUpdate(party.id)
        locks.verify(fixture.shareLinks).lockShareLinkForUpdate(link.id)
    }

    @Test
    fun `an expiry that is not in the future or a use limit below one is refused`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val link = fixture.activeBootstrapShareLink(party)
        val past = Timestamp.from(Instant.now().minusSeconds(60))

        listOf(
            { fixture.service.issue(fixture.command(party.id, idempotencyKey = "issue-past").copy(expiresAt = past)) },
            { fixture.service.issue(fixture.command(party.id, idempotencyKey = "issue-zero").copy(maxUses = 0)) },
            {
                fixture.service.replace(
                    fixture.replaceCommand(link, party, idempotencyKey = "replace-past").copy(expiresAt = past)
                )
            },
            {
                fixture.service.replace(
                    fixture.replaceCommand(link, party, idempotencyKey = "replace-zero").copy(maxUses = 0)
                )
            },
        ).forEach { command ->
            val refusal = assertThrows(InformationRequestCommandRequestException::class.java) { command() }
            assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_LIMITS_INVALID, refusal.reasonCode)
        }
        assertEquals(listOf(link), fixture.savedShareLinks)
        assertEquals(ShareLinkStatus.ACTIVE, link.status)
    }

    @Test
    fun `links record their verification strength and the time they were revoked`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()

        val issued = fixture.service.issue(fixture.command(party.id))
        assertEquals(RequestAccessSessionVerificationStrength.EMAIL_OTP, issued.shareLink.verificationStrength)
        assertNull(issued.shareLink.revokedAt)
        val revoked = fixture.service.revoke(fixture.revokeCommand(issued.shareLink, party))

        assertNotNull(revoked.shareLink.revokedAt)
    }

    @Test
    fun `link commands are refused once the request has finished`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val link = fixture.activeBootstrapShareLink(party)
        fixture.request.state = InformationRequestState.CLOSED

        assertThrows(InformationRequestLifecycleException::class.java) { fixture.service.issue(fixture.command(party.id)) }
        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.rotate(
                fixture.rotateCommand(
                    link,
                    party
                )
            )
        }
        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.replace(
                fixture.replaceCommand(
                    link,
                    party
                )
            )
        }
        assertEquals(ShareLinkStatus.ACTIVE, link.status)
    }

    @Test
    fun `replacing a revoked bootstrap link is refused`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val oldLink = fixture.activeBootstrapShareLink(party).apply { status = ShareLinkStatus.REVOKED }

        val ex = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.replace(fixture.replaceCommand(oldLink, party))
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_REVOKED, ex.reasonCode)
    }

    @Test
    fun `a repeated replacement under the same idempotency key cannot recover the new token`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val oldLink = fixture.activeBootstrapShareLink(party)
        val command = fixture.replaceCommand(oldLink, party)

        fixture.service.replace(command)
        val retry = assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.replace(command)
        }
        assertEquals(InformationRequestErrorCatalog.ACCESS_LINK_ALREADY_REPLACED, retry.reasonCode)
    }

    @Test
    fun `revoking an active bootstrap link marks it revoked and revokes its sessions`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party)

        val revoked = fixture.service.revoke(fixture.revokeCommand(shareLink, party))

        assertEquals(ShareLinkStatus.REVOKED, revoked.shareLink.status)
        verify(fixture.requestAccessSessionService).revokeAllForShareLink(shareLink.id)
    }

    @Test
    fun `an automatic party cutoff revokes every active link and its sessions and records each with its cause`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val first = fixture.activeBootstrapShareLink(party)
        val second = fixture.activeBootstrapShareLink(party)
        fixture.activeBootstrapShareLink(party).apply { status = ShareLinkStatus.REVOKED }
        val cutoff = InformationRequestCredentialCutoff(
            InformationRequestAuditActor.of(
                RequestAccessContext(
                    fixture.actor,
                    AuthorizationContext(sessionRef = "cutoff-manager-session")
                )
            ),
            InformationRequestCredentialCutoff.PARTY_REVOKED,
        )

        val revoked = fixture.service.revokeAllForParty(fixture.request, party, cutoff)

        assertEquals(setOf(first.id, second.id), revoked.map { it.id }.toSet())
        assertEquals(ShareLinkStatus.REVOKED, first.status)
        assertEquals(ShareLinkStatus.REVOKED, second.status)
        verify(fixture.requestAccessSessionService).revokeAllForShareLink(first.id)
        verify(fixture.requestAccessSessionService).revokeAllForShareLink(second.id)
        assertEquals(listOf("REVOKE_ACCESS_LINK", "REVOKE_ACCESS_LINK"), fixture.history.map { it.mutation.name })
        assertTrue(fixture.history.all { it.reasonCode == "PARTY_REVOKED" && it.partyId == party.id && it.actorId == fixture.actor.id })
        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder, times(2)).record(audit.capture())
        assertEquals(revoked.map { it.id.toString() }, audit.allValues.map { it.payload["shareLinkId"] })
        audit.allValues.forEach { record ->
            assertEquals("information_request.access_link.revoke", record.eventTypeKey)
            assertEquals("PARTY_REVOKED", record.reason)
            assertEquals("cutoff-manager-session", record.sessionId)
            assertEquals(AuditActorKind.HUMAN, record.actorKind)
            assertEquals(MANAGER_LABEL, record.actorLabel)
            assertEquals(
                AuditOwnerScope.Organization(requireNotNull(fixture.request.ownerOrganizationId)),
                record.owner
            )
            assertEquals(
                "information_request.revoke_access_link|${fixture.request.id}|${record.payload["shareLinkId"]}|party_revoked",
                record.idempotencyKey
            )
            assertEquals(
                0,
                InformationRequestAuditPayloadPolicy.projectionOf(record.payload) { _ -> true }.withheldKeyCount
            )
            assertFalse(record.payload.values.any { value -> value == first.tokenHash || value == second.tokenHash })
        }
    }

    @Test
    fun `a system cutoff on a Personal request records the Personal owner and no caller session`() {
        val fixture = Fixture()
        fixture.request.ownerType = InformationRequestOwnerType.USER
        fixture.request.ownerUserId = UUID.randomUUID()
        fixture.request.ownerOrganizationId = null
        val party = fixture.activeActingParty()
        fixture.activeBootstrapShareLink(party)
        val system = PrincipalRef(PrincipalKind.SERVICE_ACCOUNT, UUID(0, 0))

        fixture.service.revokeAllForParty(
            fixture.request,
            party,
            InformationRequestCredentialCutoff(
                InformationRequestAuditActor.system(system),
                InformationRequestCredentialCutoff.ORGANIZATION_MEMBER_REMOVED
            ),
        )

        val audit = argumentCaptor<AuditEventDraft>()
        verify(fixture.auditRecorder).record(audit.capture())
        assertEquals(AuditOwnerScope.Personal(requireNotNull(fixture.request.ownerUserId)), audit.firstValue.owner)
        assertEquals(AuditActorKind.SYSTEM, audit.firstValue.actorKind)
        assertEquals(system.id, audit.firstValue.actorId)
        assertEquals("service-account:${system.id}", audit.firstValue.actorLabel)
        assertNull(audit.firstValue.sessionId)
        assertEquals(InformationRequestTransitionActorKind.SERVICE_ACCOUNT, fixture.history.single().actorKind)
    }

    @Test
    fun `a party without a Share or without active links cuts nothing off and records nothing`() {
        val fixture = Fixture()
        val withoutShare = fixture.activeActingParty().apply { shareId = null }
        val withRevokedLinkOnly = fixture.activeActingParty()
        fixture.activeBootstrapShareLink(withRevokedLinkOnly).apply { status = ShareLinkStatus.REVOKED }
        val cutoff = InformationRequestCredentialCutoff(
            InformationRequestAuditActor.of(fixture.access),
            InformationRequestCredentialCutoff.PARTY_REVOKED
        )

        assertTrue(fixture.service.revokeAllForParty(fixture.request, withoutShare, cutoff).isEmpty())
        assertTrue(fixture.service.revokeAllForParty(fixture.request, withRevokedLinkOnly, cutoff).isEmpty())

        assertTrue(fixture.history.isEmpty())
        verifyNoInteractions(fixture.auditRecorder, fixture.publisher, fixture.requestAccessSessionService)
    }

    @Test
    fun `a degraded audit refuses an automatic cutoff rather than leaving it unrecorded`() {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        fixture.activeBootstrapShareLink(party)
        whenever(fixture.auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Degraded(
                UUID.randomUUID(),
                "unavailable"
            )
        )

        assertThrows(AuditCaptureFailedException::class.java) {
            fixture.service.revokeAllForParty(
                fixture.request,
                party,
                InformationRequestCredentialCutoff(
                    InformationRequestAuditActor.of(fixture.access),
                    InformationRequestCredentialCutoff.PARTY_REVOKED
                ),
            )
        }
        verifyNoInteractions(fixture.publisher)
    }

    @Test
    fun `revoking an already-revoked bootstrap link is a no-op`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party).apply { status = ShareLinkStatus.REVOKED }

        val revoked = fixture.service.revoke(fixture.revokeCommand(shareLink, party))

        assertEquals(ShareLinkStatus.REVOKED, revoked.shareLink.status)
    }

    @Test
    fun `revoking denies when the caller lacks manage-parties capability`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party)
        whenever(
            fixture.authorizationService.authorize(any(), any(), any(), any()),
        ).thenReturn(Decision.Deny("DENIED", "no"))

        assertThrows(io.quarkus.security.ForbiddenException::class.java) {
            fixture.service.revoke(fixture.revokeCommand(shareLink, party))
        }
    }

    private fun sha256Hex(raw: String): String =
        MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private class Fixture
    {
        val savedParties = mutableMapOf<UUID, InformationRequestParty>()
        val savedShareLinks = mutableListOf<ShareLink>()

        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
        }
        val exchange = Exchange().apply {
            id = request.exchangeId
            ownerOrganizationId = request.ownerOrganizationId
            status = ExchangeStatus.ACCEPTED_STARTED
            isDeleted = false
            requireRecipientSignIn = false
        }
        val authorizationContext =
            AuthorizationContext(activeOrgId = request.ownerOrganizationId, sessionRef = "bootstrap-manager-session")
        val actor = PrincipalRef.user(UUID.randomUUID())
        val access = RequestAccessContext(actor, authorizationContext)

        val requestRepository = mock<InformationRequestRepository>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val exchangeLookup = mock<ExchangeLookupService>()
        val shareLinks = mock<ShareLinkService>()
        val authorizationService = mock<AuthorizationService>()
        val requestAccessSessionService = mock<RequestAccessSessionService>()
        val receiptStore = InMemoryBootstrapCommandReceiptStore()
        val commandReceiptService = CommandReceiptService(receiptStore, CommandReceiptScope())
        val history = mutableListOf<InformationRequestTransition>()
        val transitions = mock<InformationRequestTransitionRepository>()
        val auditRecorder = mock<AuditRecorder>()
        val publisher = mock<DomainEventPublisher>()
        val displays = mock<PrincipalDisplayService>()
        val transitionHistory = InformationRequestTransitionHistoryService(
            transitions, auditRecorder, publisher, mock<InformationRequestResponseStart>(),
            InformationRequestAuditActorLabelResolver(displays), CommandReceiptScope(),
        )

        val service = InformationRequestBootstrapShareLinkService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            exchangeLookup = exchangeLookup,
            shareLinks = shareLinks,
            authorizationService = authorizationService,
            commandReceiptService = commandReceiptService,
            requestAccessSessionService = requestAccessSessionService,
            abuseLimits = InformationRequestAbuseLimits(accessLinkLifetime = Duration.ofDays(30), accessLinkUses = 25),
            accessLinkRecorder = InformationRequestAccessLinkRecorder(transitionHistory),
            continuationGuard = mock(),
        )

        init
        {
            whenever(displays.userLabel(actor.id)).thenReturn(MANAGER_LABEL)
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
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requestRepository.findRequestByIdForUpdate(request.id)).thenReturn(request)
            whenever(exchangeLookup.findExchange(request.exchangeId)).thenReturn(exchange)
            whenever(partyRepository.findByIdForUpdate(any())).thenAnswer { savedParties[it.getArgument(0)] }
            whenever(partyRepository.findByShareId(any())).thenAnswer { invocation ->
                savedParties.values.firstOrNull { it.shareId == invocation.getArgument<UUID>(0) }
            }
            whenever(shareLinks.create(any())).thenAnswer {
                it.getArgument<ShareLink>(0).also { link -> savedShareLinks += link }
            }
            whenever(shareLinks.update(any())).thenAnswer { it.getArgument(0) }
            whenever(partyRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(shareLinks.lockShareLinkForUpdate(any())).thenAnswer { invocation ->
                savedShareLinks.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
            }
            whenever(shareLinks.findShareLink(any())).thenAnswer { invocation ->
                savedShareLinks.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
            }
            whenever(partyRepository.findForRequest(request.id)).thenAnswer { savedParties.values.toList() }
            whenever(shareLinks.bootstrapLinksForShares(any())).thenAnswer { invocation ->
                val shareIds = invocation.getArgument<Collection<UUID>>(0)
                savedShareLinks.filter { it.shareId in shareIds && it.linkMode == ShareLinkMode.VERIFICATION_BOOTSTRAP }
            }
            whenever(shareLinks.activeBootstrapLinksForShare(any())).thenAnswer { invocation ->
                savedShareLinks.filter {
                    it.shareId == invocation.getArgument<UUID>(0) &&
                            it.linkMode == ShareLinkMode.VERIFICATION_BOOTSTRAP &&
                            it.status != ShareLinkStatus.REVOKED
                }
            }
            whenever(
                authorizationService.authorize(any(), any(), any(), any()),
            ).thenReturn(Decision.Allow())
        }

        fun saveParty(party: InformationRequestParty): InformationRequestParty
        {
            savedParties[party.id] = party
            return party
        }

        fun activeActingParty(): InformationRequestParty =
            InformationRequestParty().apply {
                informationRequestId = request.id
                roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
                principalKind = PrincipalKind.PARTICIPANT
                principalId = UUID.randomUUID()
                shareId = UUID.randomUUID()
                active = true
                partyRevision = 1
            }.also { saveParty(it) }

        fun activeBootstrapShareLink(party: InformationRequestParty): ShareLink =
            ShareLink().apply {
                shareId = requireNotNull(party.shareId)
                tokenHash = "seed-hash-${UUID.randomUUID()}"
                linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
                status = ShareLinkStatus.ACTIVE
            }.also { savedShareLinks += it }

        fun command(
            partyId: UUID,
            precondition: CommandPrecondition = CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partyOf(requireNotNull(savedParties[partyId])),
            ),
            idempotencyKey: String = "issue-$partyId",
        ) = IssueInformationRequestBootstrapShareLinkCommand(
            requestId = request.id,
            partyId = partyId,
            access = access,
            precondition = precondition,
            idempotencyKey = idempotencyKey,
        )

        fun rotateCommand(
            shareLink: ShareLink,
            party: InformationRequestParty,
            precondition: CommandPrecondition = CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partyOf(party),
            ),
            idempotencyKey: String = "rotate-${shareLink.id}",
        ) = RotateInformationRequestBootstrapShareLinkCommand(
            requestId = request.id,
            shareLinkId = shareLink.id,
            access = access,
            precondition = precondition,
            idempotencyKey = idempotencyKey,
        )

        fun replaceCommand(
            shareLink: ShareLink,
            party: InformationRequestParty,
            precondition: CommandPrecondition = CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partyOf(party),
            ),
            idempotencyKey: String = "replace-${shareLink.id}",
        ) = ReplaceInformationRequestBootstrapShareLinkCommand(
            requestId = request.id,
            shareLinkId = shareLink.id,
            access = access,
            precondition = precondition,
            idempotencyKey = idempotencyKey,
        )

        fun revokeCommand(
            shareLink: ShareLink,
            party: InformationRequestParty,
            precondition: CommandPrecondition = CommandPrecondition.ExpectedRevision(
                InformationRequestETag.partyOf(party),
            ),
            idempotencyKey: String = "revoke-${shareLink.id}",
        ) = RevokeInformationRequestBootstrapShareLinkCommand(
            requestId = request.id,
            shareLinkId = shareLink.id,
            access = access,
            precondition = precondition,
            idempotencyKey = idempotencyKey,
        )
    }
}

private const val MANAGER_LABEL = "Process Manager <manager@process.test>"

private class InMemoryBootstrapCommandReceiptStore : CommandReceiptStore
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
