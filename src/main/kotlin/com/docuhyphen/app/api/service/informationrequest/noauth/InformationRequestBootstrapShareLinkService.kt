package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestParentSnapshot
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestPolicyDecision
import com.docuhyphen.app.api.model.informationrequest.noauth.*
import com.docuhyphen.app.api.model.informationrequest.party.InformationRequestAccessLinkView
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestContinuationGuard
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionMatrix
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class InformationRequestBootstrapShareLinkService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val exchangeLookup: ExchangeLookupService,
    private val shareLinks: ShareLinkService,
    private val authorizationService: AuthorizationService,
    private val commandReceiptService: CommandReceiptService,
    private val requestAccessSessionService: RequestAccessSessionService,
    private val abuseLimits: InformationRequestAbuseLimits,
    private val accessLinkRecorder: InformationRequestAccessLinkRecorder,
    private val continuationGuard: InformationRequestContinuationGuard,
)
{
    @Transactional
    fun issue(
        command: IssueInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        val request = lockRequest(command.requestId, command.access)
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = ISSUE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = issueFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = issueMutation(command, request)
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

    fun links(requestId: UUID, access: RequestAccessContext): List<InformationRequestAccessLinkView>
    {
        authorize(access, requestId)
        requestRepository.findById(requestId) ?: throw IllegalArgumentException("Information Request not found")
        val partyByShare = partyRepository.findForRequest(requestId)
            .mapNotNull { party -> party.shareId?.let { it to party.id } }
            .toMap()
        return shareLinks.bootstrapLinksForShares(partyByShare.keys)
            .map { InformationRequestAccessLinkView(it, partyByShare.getValue(it.shareId)) }
    }

    private fun issueMutation(
        command: IssueInformationRequestBootstrapShareLinkCommand,
        request: InformationRequest,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        requireLimits(command.expiresAt, command.maxUses)
        requireLinkChangeAllowed(request, InformationRequestMutation.ISSUE_ACCESS_LINK)

        val party = partyRepository.findByIdForUpdate(command.partyId)
            ?: throw IllegalArgumentException("Information Request party not found")
        require(party.informationRequestId == request.id) { "Information Request party not found" }
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))
        if (!party.active) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARTY_ALREADY_REVOKED,
                "This party was removed from the request, so it cannot receive an access link",
            )
        }
        if (party.roleKey == InformationRequestShareRoleKey.SUBJECT) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE,
                "A subject party cannot receive an access link",
            )
        }
        val shareId = party.shareId ?: throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE,
            "This party does not hold access to the request yet, so it cannot receive an access link",
        )
        requireEligibleParty(party)

        val rawToken = generateToken()
        val now = Timestamp.from(Instant.now())
        val shareLink = ShareLink().apply {
            this.shareId = shareId
            tokenHash = hash(rawToken)
            linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
            verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP
            expiresAt = command.expiresAt ?: defaultExpiry()
            maxUses = command.maxUses ?: abuseLimits.accessLinkUses
            status = ShareLinkStatus.ACTIVE
            createdByPrincipalKind = command.access.principal.kind
            createdByPrincipalId = command.access.principal.id
            createdAt = now
        }
        val saved = shareLinks.create(shareLink)
        advancePartyRevision(party, now)
        accessLinkRecorder.record(
            request,
            party,
            saved,
            InformationRequestMutation.ISSUE_ACCESS_LINK,
            command.access,
            command.idempotencyKey
        )
        return InformationRequestBootstrapShareLinkIssuance(saved, rawToken, InformationRequestETag.partyOf(party))
    }

    @Transactional
    fun rotate(
        command: RotateInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        val request = lockRequest(command.requestId, command.access)
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = ROTATE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = rotateFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = rotateMutation(command, request)
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
        request: InformationRequest,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        requireLinkChangeAllowed(request, InformationRequestMutation.ROTATE_ACCESS_LINK)
        val (shareLink, party) = resolveBootstrapShareLink(command.requestId, command.shareLinkId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))
        requireEligibleParty(party)
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
        val saved = shareLinks.update(shareLink)
        advancePartyRevision(party, now)
        accessLinkRecorder.record(
            request,
            party,
            saved,
            InformationRequestMutation.ROTATE_ACCESS_LINK,
            command.access,
            command.idempotencyKey
        )
        return InformationRequestBootstrapShareLinkIssuance(saved, rawToken, InformationRequestETag.partyOf(party))
    }

    @Transactional
    fun replace(
        command: ReplaceInformationRequestBootstrapShareLinkCommand,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        val request = lockRequest(command.requestId, command.access)
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = REPLACE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = replaceFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = replaceMutation(command, request)
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
        request: InformationRequest,
    ): InformationRequestBootstrapShareLinkIssuance
    {
        requireLimits(command.expiresAt, command.maxUses)
        requireLinkChangeAllowed(request, InformationRequestMutation.REPLACE_ACCESS_LINK)
        val (oldShareLink, party) = resolveBootstrapShareLink(command.requestId, command.shareLinkId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))
        requireEligibleParty(party)
        requireActive(oldShareLink)

        val now = Timestamp.from(Instant.now())
        requestAccessSessionService.revokeAllForShareLink(oldShareLink.id)
        oldShareLink.status = ShareLinkStatus.REVOKED
        oldShareLink.revokedAt = now
        shareLinks.update(oldShareLink)

        val rawToken = generateToken()
        val newShareLink = ShareLink().apply {
            shareId = oldShareLink.shareId
            tokenHash = hash(rawToken)
            linkMode = ShareLinkMode.VERIFICATION_BOOTSTRAP
            verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP
            expiresAt = command.expiresAt ?: defaultExpiry()
            maxUses = command.maxUses ?: abuseLimits.accessLinkUses
            status = ShareLinkStatus.ACTIVE
            replacesShareLinkId = oldShareLink.id
            createdByPrincipalKind = command.access.principal.kind
            createdByPrincipalId = command.access.principal.id
            createdAt = now
        }
        val saved = shareLinks.create(newShareLink)
        advancePartyRevision(party, now)
        accessLinkRecorder.record(
            request,
            party,
            saved,
            InformationRequestMutation.REPLACE_ACCESS_LINK,
            command.access,
            command.idempotencyKey
        )
        return InformationRequestBootstrapShareLinkIssuance(saved, rawToken, InformationRequestETag.partyOf(party))
    }

    @Transactional
    fun revoke(command: RevokeInformationRequestBootstrapShareLinkCommand): InformationRequestBootstrapShareLinkRevocation
    {
        val request = lockRequest(command.requestId, command.access)
        val receiptRequest = CommandReceiptRequest(
            resource = ResourceRef.informationRequest(command.requestId),
            operation = REVOKE_OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = revokeFingerprint(command),
        )
        return when (
            val decision = commandReceiptService.runOnce(receiptRequest) {
                val result = revokeMutation(command, request)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        ResourceType.INFORMATION_REQUEST_ACCESS_LINK,
                        result.shareLink.id,
                        etag = result.partyETag
                    ),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
                resolveBootstrapShareLink(command.requestId, decision.result.resourceId).let { (shareLink, party) ->
                    InformationRequestBootstrapShareLinkRevocation(shareLink, InformationRequestETag.partyOf(party))
                }
        }
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun revokeAllForRequest(request: InformationRequest, cutoff: InformationRequestCredentialCutoff): List<ShareLink> =
        partiesOf(request).flatMap { party -> revokeLinks(request, party, cutoff, revokeSessions = true) }

    @Transactional(Transactional.TxType.MANDATORY)
    fun revokeLinksKeepingSessions(
        request: InformationRequest,
        cutoff: InformationRequestCredentialCutoff
    ): List<ShareLink> =
        partiesOf(request).flatMap { party -> revokeLinks(request, party, cutoff, revokeSessions = false) }

    @Transactional(Transactional.TxType.MANDATORY)
    fun revokeAllForParty(
        request: InformationRequest,
        party: InformationRequestParty,
        cutoff: InformationRequestCredentialCutoff,
    ): List<ShareLink> = revokeLinks(request, party, cutoff, revokeSessions = true)

    private fun partiesOf(request: InformationRequest): List<InformationRequestParty> =
        partyRepository.findForRequest(request.id)
            .sortedWith(compareBy<InformationRequestParty>({ it.createdAt }, { it.id }))

    private fun revokeLinks(
        request: InformationRequest,
        party: InformationRequestParty,
        cutoff: InformationRequestCredentialCutoff,
        revokeSessions: Boolean,
    ): List<ShareLink>
    {
        val shareId = party.shareId ?: return emptyList()
        return shareLinks.activeBootstrapLinksForShare(shareId)
            .sortedWith(compareBy<ShareLink>({ it.createdAt }, { it.id }))
            .map { shareLink ->
                if (revokeSessions) requestAccessSessionService.revokeAllForShareLink(shareLink.id)
                shareLink.status = ShareLinkStatus.REVOKED
                shareLink.revokedAt = Timestamp.from(Instant.now())
                shareLinks.update(shareLink).also { accessLinkRecorder.recordCutoff(request, party, it, cutoff) }
            }
    }

    private fun revokeMutation(
        command: RevokeInformationRequestBootstrapShareLinkCommand,
        request: InformationRequest,
    ): InformationRequestBootstrapShareLinkRevocation
    {
        val (shareLink, party) = resolveBootstrapShareLink(command.requestId, command.shareLinkId)
        command.precondition.requireSatisfiedBy(InformationRequestETag.partyOf(party))

        requestAccessSessionService.revokeAllForShareLink(shareLink.id)
        if (shareLink.status == ShareLinkStatus.REVOKED)
        {
            return InformationRequestBootstrapShareLinkRevocation(shareLink, InformationRequestETag.partyOf(party))
        }
        val now = Timestamp.from(Instant.now())
        shareLink.status = ShareLinkStatus.REVOKED
        shareLink.revokedAt = now
        val saved = shareLinks.update(shareLink)
        advancePartyRevision(party, now)
        accessLinkRecorder.record(
            request,
            party,
            saved,
            InformationRequestMutation.REVOKE_ACCESS_LINK,
            command.access,
            command.idempotencyKey
        )
        return InformationRequestBootstrapShareLinkRevocation(saved, InformationRequestETag.partyOf(party))
    }

    private fun lockRequest(requestId: UUID, access: RequestAccessContext): InformationRequest {
        authorize(access, requestId)
        return requestRepository.findRequestByIdForUpdate(requestId)
            ?: throw IllegalArgumentException("Information Request not found")
    }

    private fun resolveBootstrapShareLink(
        requestId: UUID,
        shareLinkId: UUID,
    ): Pair<ShareLink, InformationRequestParty>
    {
        val candidate = shareLinks.findShareLink(shareLinkId) ?: throw linkNotFound()
        val boundParty = partyRepository.findByShareId(candidate.shareId)
            ?.takeIf { it.informationRequestId == requestId }
            ?: throw linkNotFound()
        if (candidate.linkMode != ShareLinkMode.VERIFICATION_BOOTSTRAP) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_INVALID,
                "Only a bootstrap access link can be rotated, replaced, or revoked through this operation",
            )
        }
        val party = partyRepository.findByIdForUpdate(boundParty.id) ?: throw linkNotFound()
        val shareLink = shareLinks.lockShareLinkForUpdate(shareLinkId) ?: throw linkNotFound()
        return shareLink to party
    }

    private fun linkNotFound(): IllegalArgumentException =
        IllegalArgumentException("Information Request access link not found")

    private fun advancePartyRevision(party: InformationRequestParty, now: Timestamp) {
        party.partyRevision += 1
        party.updatedAt = now
        partyRepository.update(party)
    }

    private fun requireEligibleParty(party: InformationRequestParty) {
        if (party.principalKind != PrincipalKind.PARTICIPANT) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE,
                "Access links are only for parties identified by a contact address; account holders sign in",
            )
        }
        if (party.linkedUserShareId != null) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_PARTY_ACCOUNT_LINKED,
                "This participant linked the request to an account and now signs in to respond",
            )
        }
    }

    private fun requireLimits(expiresAt: Timestamp?, maxUses: Int?) {
        val expiryInvalid = expiresAt != null && !expiresAt.after(Timestamp.from(Instant.now()))
        if (expiryInvalid || (maxUses != null && maxUses < 1)) {
            throw InformationRequestCommandRequestException(
                "An access link must expire in the future and allow at least one use",
                InformationRequestErrorCatalog.ACCESS_LINK_LIMITS_INVALID,
            )
        }
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
        CommandRequestFingerprint.of(ROTATE_OPERATION, command.requestId, command.shareLinkId)

    private fun replaceFingerprint(command: ReplaceInformationRequestBootstrapShareLinkCommand): String =
        CommandRequestFingerprint.of(
            REPLACE_OPERATION,
            command.requestId,
            command.shareLinkId,
            command.expiresAt,
            command.maxUses,
        )

    private fun revokeFingerprint(command: RevokeInformationRequestBootstrapShareLinkCommand): String =
        CommandRequestFingerprint.of(REVOKE_OPERATION, command.requestId, command.shareLinkId)

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

    private fun defaultExpiry(): Timestamp = Timestamp.from(Instant.now().plus(abuseLimits.accessLinkLifetime))

    private fun requireLinkChangeAllowed(request: InformationRequest, mutation: InformationRequestMutation) {
        val exchange = exchangeLookup.findExchange(request.exchangeId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.PARENT_STATE_INVALID,
                "Parent Exchange not found",
            )
        val decision = InformationRequestTransitionMatrix.canMutate(
            InformationRequestParentSnapshot(
                status = exchange.status,
                deleted = exchange.isDeleted,
                lockedForUpdate = true,
            ),
            request.state,
            mutation,
        )
        if (decision is InformationRequestPolicyDecision.Deny) {
            throw InformationRequestLifecycleException(decision.reasonCode, "This access link change is not allowed")
        }
        if (exchange.requireRecipientSignIn) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.RECIPIENT_SIGN_IN_REQUIRED,
                "This Exchange requires recipients to sign in; issue authenticated access instead " +
                        "of a bootstrap access link",
            )
        }
        continuationGuard.requireContinuation(
            LockedInformationRequest(exchange, request),
            InformationRequestContinuationAction.ASSIGN_PARTY_WITHIN_CAP,
        )
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
        CommandRequestFingerprint.of(
            ISSUE_OPERATION,
            command.requestId,
            command.partyId,
            command.expiresAt,
            command.maxUses,
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
