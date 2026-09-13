package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ParticipantAccountLink
import com.docuhyphen.app.api.model.entity.PrincipalKind
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
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.model.informationrequest.UpgradeInformationRequestParticipantAccountCommand
import com.docuhyphen.app.api.model.informationrequest.InformationRequestParticipantAccountUpgrade
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Completes a respondent's transition from an email-only Participant to a registered App User.
 *
 * The caller must already hold an active [RequestAccessSession] proving contact for the
 * Participant-held request party being upgraded; this service authenticates by that session
 * rather than by any party-management capability, since the actor is the respondent, not an
 * Information Request administrator. It persists a [ParticipantAccountLink] recording the identity
 * fact, grants the App User an equivalent Share on the same Information Request, and revokes every
 * bootstrap-mode ShareLink and session still active on the party's Share -- all without rewriting
 * the Participant's own Share, party, or ShareLink history.
 */
@ApplicationScoped
class InformationRequestParticipantAccountUpgradeService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val shareLinkRepository: ShareLinkRepository,
    private val externalParticipantRepository: ExternalParticipantRepository,
    private val appUserService: AppUserService,
    private val shareService: ShareService,
    private val participantAccountLinkRepository: ParticipantAccountLinkRepository,
    private val requestAccessSessionService: RequestAccessSessionService,
    private val commandReceiptService: CommandReceiptService,
)
{
    @Transactional
    fun upgrade(
        command: UpgradeInformationRequestParticipantAccountCommand,
    ): InformationRequestParticipantAccountUpgrade
    {
        val credentialSession = requestAccessSessionService.authenticate(command.sessionToken)
        require(credentialSession.id == command.sessionId) { "Session credential does not match the requested session" }
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = UPGRADE_OPERATION,
            actor = CommandActorRef.accessSession(command.sessionId),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = upgradeFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = upgradeMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replayResult(decision.result)
        }
    }

    private fun upgradeMutation(
        command: UpgradeInformationRequestParticipantAccountCommand,
    ): InformationRequestParticipantAccountUpgrade
    {
        val request = requestRepository.findById(command.requestId)
            ?: throw IllegalArgumentException("Information Request not found")

        val session = requestAccessSessionService.requireActive(command.sessionId)
        val shareLink = shareLinkRepository.findByIdForUpdate(session.shareLinkId)
            ?: throw IllegalArgumentException("Information Request access link not found")
        require(shareLink.linkMode == ShareLinkMode.VERIFICATION_BOOTSTRAP) {
            "Only a bootstrap-mode access link session can complete a registration upgrade"
        }
        requireUsableShareLink(shareLink)

        val party = partyRepository.findByShareId(shareLink.shareId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))
        require(party.active) { "Information Request party is already revoked" }
        require(party.principalKind == PrincipalKind.PARTICIPANT) {
            "Only a Participant-held request party can complete a registration upgrade"
        }
        require(
            session.participantPrincipalKind == party.principalKind &&
                session.participantPrincipalId == party.principalId,
        ) { "Session is not bound to this request party" }

        val participantId = requireNotNull(party.principalId)
        val participant = externalParticipantRepository.findById(participantId)
            ?: throw IllegalArgumentException("Information Request participant not found")
        val appUser = appUserService.getById(command.appUserId)
            ?: throw IllegalArgumentException("App User not found")
        if (participant.emailLower != appUser.email.trim().lowercase())
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_EMAIL_MISMATCH,
                "The registered account's email does not match this participant's verified contact address",
            )
        }

        val existingLink = participantAccountLinkRepository.findByParticipantId(participantId)
        if (existingLink != null && existingLink.appUserId != appUser.id)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTICIPANT_ACCOUNT_ALREADY_LINKED,
                "This participant has already completed a verified registration upgrade to a different account",
            )
        }
        val link = existingLink ?: run {
            val now = Timestamp.from(Instant.now())
            participantAccountLinkRepository.save(
                ParticipantAccountLink().apply {
                    this.participantId = participantId
                    appUserId = appUser.id
                    linkedViaInformationRequestId = request.id
                    linkedViaShareLinkId = shareLink.id
                    linkedAt = now
                    createdAt = now
                },
            )
        }

        val grantedShare = shareService.grantRoleKeyWithPrincipalProvenance(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = request.id,
            principalKind = PrincipalKind.USER,
            principalId = appUser.id,
            roleName = party.roleKey.name,
            grantedByPrincipal = PrincipalRef.user(appUser.id),
            resourceLabel = RESOURCE_LABEL,
        )

        shareLinkRepository.findActiveBootstrapLinksForShare(shareLink.shareId).forEach { activeLink ->
            requestAccessSessionService.revokeAllForShareLink(activeLink.id)
            activeLink.status = ShareLinkStatus.REVOKED
            shareLinkRepository.update(activeLink)
        }

        return InformationRequestParticipantAccountUpgrade(link, grantedShare)
    }

    private fun requireUsableShareLink(shareLink: ShareLink)
    {
        if (shareLink.status == ShareLinkStatus.REVOKED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_REVOKED,
                "This access link has been revoked",
            )
        }
        val now = Timestamp.from(Instant.now())
        val expiresAt = shareLink.expiresAt
        if (shareLink.status == ShareLinkStatus.EXPIRED || (expiresAt != null && !expiresAt.after(now)))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_EXPIRED,
                "This access link has expired",
            )
        }
    }

    private fun replayResult(result: CommandResultReference): InformationRequestParticipantAccountUpgrade
    {
        require(result.resourceType == ResourceType.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK) {
            "Command receipt does not reference a participant account link"
        }
        val link = participantAccountLinkRepository.findById(result.resourceId)
            ?: throw IllegalArgumentException("Participant account link receipt target not found")
        val grantedShare = shareService.findDirectForPrincipalOnResource(
            PrincipalKind.USER,
            link.appUserId,
            ResourceType.INFORMATION_REQUEST,
            link.linkedViaInformationRequestId,
        ) ?: throw IllegalArgumentException("Participant account link's granted Share not found")
        return InformationRequestParticipantAccountUpgrade(link, grantedShare)
    }

    private fun upgradeFingerprint(command: UpgradeInformationRequestParticipantAccountCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                UPGRADE_OPERATION,
                command.requestId.toString(),
                command.sessionId.toString(),
                command.appUserId.toString(),
            ).joinToString("|"),
        )

    private fun InformationRequestParticipantAccountUpgrade.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST_PARTICIPANT_ACCOUNT_LINK,
            resourceId = participantAccountLink.id,
        )

    private companion object
    {
        const val RESOURCE_LABEL = "Information Request"
        const val UPGRADE_OPERATION = "upgrade-information-request-participant-account"
    }
}
