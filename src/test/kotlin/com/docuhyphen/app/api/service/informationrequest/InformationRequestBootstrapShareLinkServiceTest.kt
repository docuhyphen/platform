package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestBootstrapShareLinkServiceTest
{
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
    fun `issuance is refused for the subject party`()
    {
        val fixture = Fixture()
        val subject = InformationRequestParty().apply {
            informationRequestId = fixture.request.id
            roleKey = InformationRequestShareRoleKey.SUBJECT
            active = true
        }.also { fixture.saveParty(it) }

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.issue(fixture.command(subject.id))
        }
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

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.issue(fixture.command(party.id))
        }
    }

    @Test
    fun `issuance is refused for a revoked party`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty().apply { active = false }

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.issue(fixture.command(party.id))
        }
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

        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.rotate(fixture.rotateCommand(shareLink, party))
        }
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
        assertEquals(party.shareId, issuance.shareLink.shareId)
        assertEquals(oldLink.id, issuance.shareLink.replacesShareLinkId)
        assertEquals(ShareLinkStatus.ACTIVE, issuance.shareLink.status)
        assertNotEquals(oldLink.id, issuance.shareLink.id)
        verify(fixture.requestAccessSessionService).revokeAllForShareLink(oldLink.id)
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

        assertEquals(ShareLinkStatus.REVOKED, revoked.status)
        verify(fixture.requestAccessSessionService).revokeAllForShareLink(shareLink.id)
    }

    @Test
    fun `revoking an already-revoked bootstrap link is a no-op`()
    {
        val fixture = Fixture()
        val party = fixture.activeActingParty()
        val shareLink = fixture.activeBootstrapShareLink(party).apply { status = ShareLinkStatus.REVOKED }

        val revoked = fixture.service.revoke(fixture.revokeCommand(shareLink, party))

        assertEquals(ShareLinkStatus.REVOKED, revoked.status)
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
        val authorizationContext = AuthorizationContext(activeOrgId = request.ownerOrganizationId)
        val actor = PrincipalRef.user(UUID.randomUUID())
        val access = RequestAccessContext(actor, authorizationContext)

        val requestRepository = mock<InformationRequestRepository>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val exchangeRepository = mock<ExchangeRepository>()
        val shareLinkRepository = mock<ShareLinkRepository>()
        val authorizationService = mock<AuthorizationService>()
        val requestAccessSessionService = mock<RequestAccessSessionService>()
        val receiptStore = InMemoryBootstrapCommandReceiptStore()
        val commandReceiptService = CommandReceiptService(receiptStore)

        val service = InformationRequestBootstrapShareLinkService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            exchangeRepository = exchangeRepository,
            shareLinkRepository = shareLinkRepository,
            authorizationService = authorizationService,
            commandReceiptService = commandReceiptService,
            requestAccessSessionService = requestAccessSessionService,
        )

        init
        {
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(exchangeRepository.findById(request.exchangeId)).thenReturn(exchange)
            whenever(partyRepository.findByIdForUpdate(any())).thenAnswer { savedParties[it.getArgument(0)] }
            whenever(partyRepository.findByShareId(any())).thenAnswer { invocation ->
                savedParties.values.firstOrNull { it.shareId == invocation.getArgument<UUID>(0) }
            }
            whenever(shareLinkRepository.save(any())).thenAnswer {
                it.getArgument<ShareLink>(0).also { link -> savedShareLinks += link }
            }
            whenever(shareLinkRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(shareLinkRepository.findByIdForUpdate(any())).thenAnswer { invocation ->
                savedShareLinks.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
            }
            whenever(shareLinkRepository.findById(any())).thenAnswer { invocation ->
                savedShareLinks.firstOrNull { it.id == invocation.getArgument<UUID>(0) }
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

private class InMemoryBootstrapCommandReceiptStore : CommandReceiptStore
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
