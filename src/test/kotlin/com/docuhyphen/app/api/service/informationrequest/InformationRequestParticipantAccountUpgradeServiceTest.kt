package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.CommandReceipt
import com.docuhyphen.app.api.model.entity.ExternalParticipant
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.ParticipantAccountLink
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.ParticipantAccountLinkRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandPreconditionException
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandReceiptStore
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.user.AppUserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.only
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

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
    fun `an upgrade or its replay requires the secret of the named session`()
    {
        val fixture = Fixture()
        val party = fixture.activeParticipantParty()
        val session = fixture.activeSession(party)
        val command = fixture.command(party, session)
        whenever(fixture.requestAccessSessionService.authenticate(null)).thenThrow(
            InformationRequestLifecycleException(InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED, "Missing credential"),
        )
        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(command.copy(sessionToken = null))
        }
        fixture.service.upgrade(command)
        assertThrows(InformationRequestLifecycleException::class.java) {
            fixture.service.upgrade(command.copy(sessionToken = null))
        }
        val other = RequestAccessSession()
        whenever(fixture.requestAccessSessionService.authenticate("other-secret")).thenReturn(other)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.upgrade(command.copy(sessionToken = "other-secret"))
        }
        assertEquals(1, fixture.savedLinks.size)
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

        val requestRepository = mock<InformationRequestRepository>()
        val partyRepository = mock<InformationRequestPartyRepository>()
        val shareLinkRepository = mock<ShareLinkRepository>()
        val externalParticipantRepository = mock<ExternalParticipantRepository>()
        val appUserService = mock<AppUserService>()
        val shareService = mock<ShareService>()
        val participantAccountLinkRepository = mock<ParticipantAccountLinkRepository>()
        val requestAccessSessionService = mock<RequestAccessSessionService>()
        val receiptStore = InMemoryUpgradeCommandReceiptStore()
        val commandReceiptService = CommandReceiptService(receiptStore)

        val service = InformationRequestParticipantAccountUpgradeService(
            requestRepository = requestRepository,
            partyRepository = partyRepository,
            shareLinkRepository = shareLinkRepository,
            externalParticipantRepository = externalParticipantRepository,
            appUserService = appUserService,
            shareService = shareService,
            participantAccountLinkRepository = participantAccountLinkRepository,
            requestAccessSessionService = requestAccessSessionService,
            commandReceiptService = commandReceiptService,
        )

        init
        {
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(shareLinkRepository.findByIdForUpdate(shareLink.id)).thenReturn(shareLink)
            whenever(shareLinkRepository.update(any())).thenAnswer { it.getArgument(0) }
            whenever(shareLinkRepository.findActiveBootstrapLinksForShare(any())).thenAnswer {
                if (shareLink.status != ShareLinkStatus.REVOKED) listOf(shareLink) else emptyList<ShareLink>()
            }
            whenever(externalParticipantRepository.findById(participant.id)).thenReturn(participant)
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
                shareService.grantRoleKeyWithPrincipalProvenance(
                    any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull(),
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
            whenever(shareService.findDirectForPrincipalOnResource(any(), any(), any(), any())).thenAnswer {
                grantedShares.lastOrNull()
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
                whenever(partyRepository.findByShareId(requireNotNull(party.shareId))).thenReturn(party)
            }

        fun activeSession(party: InformationRequestParty): RequestAccessSession =
            RequestAccessSession().apply {
                id = UUID.randomUUID()
                shareLinkId = shareLink.id
                participantPrincipalKind = requireNotNull(party.principalKind)
                participantPrincipalId = requireNotNull(party.principalId)
            }.also { session ->
                whenever(requestAccessSessionService.authenticate("${session.id}.secret")).thenReturn(session)
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
            sessionToken = "${session.id}.secret",
            appUserId = appUser.id,
            precondition = precondition,
            idempotencyKey = idempotencyKey,
        )
    }
}

private class InMemoryUpgradeCommandReceiptStore : CommandReceiptStore
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
