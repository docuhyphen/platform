package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandPrecondition
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class IssueInformationRequestBootstrapShareLinkCommand(
    val requestId: UUID,
    val partyId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
)

data class RotateInformationRequestBootstrapShareLinkCommand(
    val requestId: UUID,
    val shareLinkId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class ReplaceInformationRequestBootstrapShareLinkCommand(
    val requestId: UUID,
    val shareLinkId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
    val expiresAt: Timestamp? = null,
    val maxUses: Int? = null,
)

data class RevokeInformationRequestBootstrapShareLinkCommand(
    val requestId: UUID,
    val shareLinkId: UUID,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestBootstrapShareLinkIssuance(
    val shareLink: ShareLink,
    val rawToken: String,
)

/**
 * Issues, rotates, replaces, and revokes a [ShareLinkMode.VERIFICATION_BOOTSTRAP] [ShareLink] bound
 * to one acting request party's existing request-party Share, so that Share stays the sole
 * capability grant and this link only ever proves recipient contact for it.
 */
@ApplicationScoped
class InformationRequestBootstrapShareLinkService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val exchangeRepository: ExchangeRepository,
    private val shareLinkRepository: ShareLinkRepository,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val requestAccessSessionService: RequestAccessSessionService,
)
{
    @Transactional
    fun issue(
        command: IssueInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = ISSUE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = issueFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = issueMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_ALREADY_ISSUED,
                "This access link was already issued for this request; the original token cannot be " +
                    "recovered and this retry cannot re-issue it",
            )
        }
    }

    private fun issueMutation(
        command: IssueInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        val request = requestRepository.findById(command.requestId)
            ?: throw IllegalArgumentException("Information Request not found")

        authorize(command.access, request.id)

        val exchange = exchangeRepository.findById(request.exchangeId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
                "Parent Exchange not found",
            )
        if (exchange.requireRecipientSignIn)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED,
                "This Exchange requires recipients to sign in; issue authenticated access instead " +
                    "of a bootstrap access link",
            )
        }

        val party = partyRepository.findByIdForUpdate(command.partyId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))
        require(party.active) { "Information Request party is already revoked" }
        require(party.roleKey != InformationRequestShareRoleKey.SUBJECT) {
            "A subject party cannot receive a bootstrap access link"
        }
        val shareId = requireNotNull(party.shareId) {
            "An acting party must already hold a request-party Share before a bootstrap access link " +
                "can be issued"
        }

        val rawToken = generateToken()
        val now = Timestamp.from(Instant.now())
        val shareLink = ShareLink().apply {
            this.shareId = shareId
            tokenHash = hash(rawToken)
            linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
            expiresAt = command.expiresAt
            maxUses = command.maxUses
            status = ShareLinkStatus.ACTIVE
            createdByAppUserId = command.access.principal.id.takeIf {
                command.access.principal.kind == PrincipalKind.USER
            }
            createdAt = now
        }
        return InformationRequestBootstrapShareLinkIssuance(shareLinkRepository.save(shareLink), rawToken)
    }

    /**
     * Replaces this link's secret in place, resetting its use count and clearing any outstanding
     * contact-proof challenge, without changing its identity or any other constraint. Any session
     * already minted from this link is revoked, since a rotated secret must not leave a prior
     * session still usable.
     */
    @Transactional
    fun rotate(
        command: RotateInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = ROTATE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = rotateFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = rotateMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_ALREADY_ROTATED,
                "This access link was already rotated for this request; the new secret cannot be " +
                    "recovered and this retry cannot re-rotate it",
            )
        }
    }

    private fun rotateMutation(
        command: RotateInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        authorize(command.access, command.requestId)
        val (shareLink, party) = resolveBootstrapShareLink(command.requestId, command.shareLinkId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))
        requireActive(shareLink)

        requestAccessSessionService.revokeAllForShareLink(shareLink.id)

        val rawToken = generateToken()
        val now = Timestamp.from(Instant.now())
        shareLink.tokenHash = hash(rawToken)
        shareLink.usedCount = 0
        shareLink.contactOtpHash = null
        shareLink.contactOtpExpiresAt = null
        shareLink.contactOtpFailedAttempts = 0
        shareLink.contactOtpChallengeCount = 0
        shareLink.contactOtpLockedUntil = null
        shareLink.rotatedAt = now
        shareLink.rotationCount += 1
        return InformationRequestBootstrapShareLinkIssuance(shareLinkRepository.update(shareLink), rawToken)
    }

    /**
     * Revokes this link and issues a brand new bootstrap link bound to the same request-party
     * Share, recording which link it replaced. Used instead of [rotate] when the link's own
     * constraints (expiry, use limit) must change rather than only its secret. Any session already
     * minted from the old link is revoked.
     */
    @Transactional
    fun replace(
        command: ReplaceInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = REPLACE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = replaceFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = replaceMutation(command)
                CommandMutationResult(result, result.commandResultReference())
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_ALREADY_REPLACED,
                "This access link was already replaced for this request; the new secret cannot be " +
                    "recovered and this retry cannot re-replace it",
            )
        }
    }

    private fun replaceMutation(
        command: ReplaceInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        authorize(command.access, command.requestId)
        val (oldShareLink, party) = resolveBootstrapShareLink(command.requestId, command.shareLinkId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))
        requireActive(oldShareLink)

        requestAccessSessionService.revokeAllForShareLink(oldShareLink.id)
        oldShareLink.status = ShareLinkStatus.REVOKED
        shareLinkRepository.update(oldShareLink)

        val rawToken = generateToken()
        val now = Timestamp.from(Instant.now())
        val newShareLink = ShareLink().apply {
            shareId = oldShareLink.shareId
            tokenHash = hash(rawToken)
            linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
            expiresAt = command.expiresAt
            maxUses = command.maxUses
            status = ShareLinkStatus.ACTIVE
            replacesShareLinkId = oldShareLink.id
            createdByAppUserId = command.access.principal.id.takeIf {
                command.access.principal.kind == PrincipalKind.USER
            }
            createdAt = now
        }
        return InformationRequestBootstrapShareLinkIssuance(shareLinkRepository.save(newShareLink), rawToken)
    }

    /**
     * Revokes this link outright, along with every session already minted from it. Unlike [rotate]
     * or [replace], no new link is issued.
     */
    @Transactional
    fun revoke(command: RevokeInformationRequestBootstrapShareLinkCommand): ShareLink
    {
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = REVOKE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = revokeFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = revokeMutation(command)
                CommandMutationResult(
                    result,
                    CommandResultReference(ResourceType.INFORMATION_REQUEST_ACCESS_LINK, result.id),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> shareLinkRepository.findById(decision.result.resourceId)
                ?: throw IllegalArgumentException("Information Request access link not found")
        }
    }

    @Transactional
    fun revokeAllForShare(shareId: UUID): List<ShareLink>
    {
        return shareLinkRepository.findActiveBootstrapLinksForShare(shareId).map { shareLink ->
            requestAccessSessionService.revokeAllForShareLink(shareLink.id)
            shareLink.status = ShareLinkStatus.REVOKED
            shareLinkRepository.update(shareLink)
        }
    }

    private fun revokeMutation(command: RevokeInformationRequestBootstrapShareLinkCommand): ShareLink
    {
        authorize(command.access, command.requestId)
        val (shareLink, party) = resolveBootstrapShareLink(command.requestId, command.shareLinkId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))

        requestAccessSessionService.revokeAllForShareLink(shareLink.id)
        if (shareLink.status == ShareLinkStatus.REVOKED)
        {
            return shareLink
        }
        shareLink.status = ShareLinkStatus.REVOKED
        return shareLinkRepository.update(shareLink)
    }

    private fun resolveBootstrapShareLink(
        requestId: UUID,
        shareLinkId: UUID,
    ): Pair<ShareLink, InformationRequestParty>
    {
        val shareLink = shareLinkRepository.findByIdForUpdate(shareLinkId)
            ?: throw IllegalArgumentException("Information Request access link not found")
        require(shareLink.linkMode == ShareLinkMode.VERIFICATION_BOOTSTRAP) {
            "Only a bootstrap access link can be rotated, replaced, or revoked through this operation"
        }
        val party = partyRepository.findByShareId(shareLink.shareId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == requestId) { "Information Request access link not found" }
        return shareLink to party
    }

    private fun requireActive(shareLink: ShareLink)
    {
        if (shareLink.status == ShareLinkStatus.REVOKED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_REVOKED,
                "This access link has been revoked",
            )
        }
        val expiresAt = shareLink.expiresAt
        val expired = shareLink.status == ShareLinkStatus.EXPIRED ||
            (expiresAt != null && !expiresAt.after(Timestamp.from(Instant.now())))
        if (expired)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_EXPIRED,
                "This access link has expired",
            )
        }
    }

    private fun rotateFingerprint(command: RotateInformationRequestBootstrapShareLinkCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(ROTATE_OPERATION, command.requestId.toString(), command.shareLinkId.toString())
                .joinToString("|"),
        )

    private fun replaceFingerprint(command: ReplaceInformationRequestBootstrapShareLinkCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                REPLACE_OPERATION,
                command.requestId.toString(),
                command.shareLinkId.toString(),
                command.expiresAt?.toInstant()?.toString().orEmpty(),
                command.maxUses?.toString().orEmpty(),
            ).joinToString("|"),
        )

    private fun revokeFingerprint(command: RevokeInformationRequestBootstrapShareLinkCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(REVOKE_OPERATION, command.requestId.toString(), command.shareLinkId.toString())
                .joinToString("|"),
        )

    private fun authorize(access: RequestAccessContext, requestId: UUID)
    {
        val decision = authorizationService.authorize(
            access.principal,
            Action.INFORMATION_REQUEST_MANAGE_PARTIES,
            ResourceRef.informationRequest(requestId),
            access.authorization,
        )
        if (decision is Decision.Deny)
        {
            throw ForbiddenException("Access denied to issue an Information Request access link")
        }
    }

    private fun generateToken(): String
    {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(rawToken: String): String =
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun issueFingerprint(command: IssueInformationRequestBootstrapShareLinkCommand): String =
        CommandRequestFingerprint.sha256Hex(
            listOf(
                ISSUE_OPERATION,
                command.requestId.toString(),
                command.partyId.toString(),
                command.expiresAt?.toInstant()?.toString().orEmpty(),
                command.maxUses?.toString().orEmpty(),
            ).joinToString("|"),
        )

    private fun InformationRequestBootstrapShareLinkIssuance.commandResultReference(): CommandResultReference =
        CommandResultReference(
            resourceType = ResourceType.INFORMATION_REQUEST_ACCESS_LINK,
            resourceId = shareLink.id,
        )

    private companion object
    {
        const val ISSUE_OPERATION = "issue-information-request-bootstrap-share-link"
        const val ROTATE_OPERATION = "rotate-information-request-bootstrap-share-link"
        const val REPLACE_OPERATION = "replace-information-request-bootstrap-share-link"
        const val REVOKE_OPERATION = "revoke-information-request-bootstrap-share-link"
        val secureRandom = SecureRandom()
    }
}
