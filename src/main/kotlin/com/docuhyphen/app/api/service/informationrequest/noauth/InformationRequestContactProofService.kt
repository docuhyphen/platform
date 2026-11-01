package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.model.informationrequest.noauth.IssuedRequestAccessSession
import com.docuhyphen.app.api.model.informationrequest.noauth.LockedInformationRequestBootstrapLink
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.exchange.ShareLinkService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.parent.lockParentExchangeOf
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestParticipantOwner
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class ContactProofInvalidException : InformationRequestLifecycleException(
    InformationRequestErrorCatalog.CONTACT_PROOF_INVALID,
    "Invalid verification code",
)

@ApplicationScoped
class InformationRequestContactProofService @Inject constructor(
    private val shareLinks: ShareLinkService,
    private val partyRepository: InformationRequestPartyRepository,
    private val requestRepository: InformationRequestRepository,
    private val exchangeLookup: ExchangeLookupService,
    private val externalParticipantService: ExternalParticipantService,
    private val otpService: OtpService,
    private val codeMailer: InformationRequestVerificationCodeMailer,
    private val requestAccessSessionService: RequestAccessSessionService,
    private val contactProofRecorder: InformationRequestContactProofRecorder,
    private val abuseSignal: InformationRequestAbuseSignal,
)
{
    @Transactional
    fun issueChallenge(rawToken: String)
    {
        val locked = lockBootstrapLink(rawToken)
        requireCredentialCommandAllowed(locked, InformationRequestMutation.ISSUE_CONTACT_CHALLENGE)
        val (request, shareLink, party) = locked
        throwIfOtpLocked(shareLink)
        if (shareLink.contactOtpChallengeCount >= OTP_MAX_CHALLENGES)
        {
            abuseSignal.refused(InformationRequestAbuseControl.CONTACT_CODE_CHALLENGE_LIMIT, "link=${shareLink.id}")
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_CHALLENGE_LIMIT,
                "This access link has reached its verification code limit",
            )
        }
        val email = contactEmailOf(request, party)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED,
                "This party has no contact address on file to verify",
            )

        val otp = otpService.generateEmailOtp()
        shareLink.contactOtpHash = otpService.hashOtp(otp)
        shareLink.contactOtpExpiresAt = Timestamp.from(Instant.now().plusSeconds(OTP_VALIDITY_SECONDS))
        shareLink.contactOtpChallengeCount += 1
        shareLinks.update(shareLink)
        contactProofRecorder.recordChallenge(request, party, shareLink)

        codeMailer.sendAfterCommit(
            email,
            "Verify your access to this request",
            "Your verification code is $otp. It expires in ${OTP_VALIDITY_SECONDS / 60} minutes.",
        )
    }

    @Transactional(dontRollbackOn = [ContactProofInvalidException::class])
    fun verifyChallenge(
        rawToken: String,
        otp: String,
        sessionExpiresAt: Timestamp? = null,
    ): IssuedRequestAccessSession
    {
        val locked = lockBootstrapLink(rawToken)
        requireCredentialCommandAllowed(locked, InformationRequestMutation.ISSUE_ACCESS_SESSION)
        val (request, shareLink, party) = locked
        throwIfOtpLocked(shareLink)

        val storedHash = shareLink.contactOtpHash
        val expiresAt = shareLink.contactOtpExpiresAt
        if (storedHash == null || expiresAt == null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED,
                "No verification code has been issued for this access link",
            )
        }
        if (!expiresAt.after(Timestamp.from(Instant.now())))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_EXPIRED,
                "Verification code has expired",
            )
        }
        if (otp.isBlank() || !otpService.verifyEmailOtp(otp, storedHash))
        {
            val failedAttempt = registerOtpFailure(shareLink)
            contactProofRecorder.recordRefusal(request, party, shareLink, failedAttempt)
            throw ContactProofInvalidException()
        }

        shareLink.contactOtpHash = null
        shareLink.contactOtpExpiresAt = null
        shareLink.contactOtpChallengeCount = 0
        shareLink.contactOtpFailedAttempts = 0
        shareLink.contactOtpLockedUntil = null
        shareLink.usedCount += 1
        shareLink.lastUsedAt = Timestamp.from(Instant.now())
        shareLinks.update(shareLink)

        val participant = PrincipalRef(
            kind = requireNotNull(party.principalKind) { "Information Request party has no principal" },
            id = requireNotNull(party.principalId) { "Information Request party has no principal" },
        )
        val issued = requestAccessSessionService.issue(
            shareLink = shareLink,
            participant = participant,
            verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP,
            expiresAt = sessionExpiresAt,
        )
        contactProofRecorder.recordSessionIssued(request, party, shareLink, issued.session)
        externalParticipantService.verifyContact(InformationRequestParticipantOwner.of(request), participant.id)
        return issued
    }

    fun resolveBootstrapLink(
        rawToken: String,
        enforceUseLimit: Boolean = false,
    ): Pair<ShareLink, InformationRequestParty> =
        readableBootstrapLink(shareLinks.findByTokenHash(hash(rawToken)) ?: throw unknownLink(), enforceUseLimit)

    fun resolveBootstrapLinkById(shareLinkId: UUID): Pair<ShareLink, InformationRequestParty> =
        readableBootstrapLink(shareLinks.findShareLink(shareLinkId) ?: throw unknownLink(), enforceUseLimit = false)

    private fun readableBootstrapLink(
        shareLink: ShareLink,
        enforceUseLimit: Boolean,
    ): Pair<ShareLink, InformationRequestParty>
    {
        requireBootstrapMode(shareLink)
        val party = partyRepository.findByShareId(shareLink.shareId)
        requireNotAccountLinked(party)
        if (!revokedWhenItsRequestEnded(shareLink, party)) requireNotRevoked(shareLink)
        requireOpen(shareLink, party, enforceUseLimit)
        requestAccessSessionService.requireNoAuthPermitted(shareLink.id)
        return shareLink to requireNotNull(party)
    }

    private fun revokedWhenItsRequestEnded(shareLink: ShareLink, party: InformationRequestParty?): Boolean =
        shareLink.status == ShareLinkStatus.REVOKED && party != null &&
                requestRepository.findById(party.informationRequestId)?.state in SESSIONS_OUTLIVE_LINKS

    fun lockBootstrapLink(rawToken: String, enforceUseLimit: Boolean = true): LockedInformationRequestBootstrapLink {
        val candidate = shareLinks.findByTokenHash(hash(rawToken)) ?: throw unknownLink()
        requireBootstrapMode(candidate)
        val boundParty = partyRepository.findByShareId(candidate.shareId)
        requireUsable(candidate, boundParty, enforceUseLimit)
        val requestId = requireNotNull(boundParty).informationRequestId
        val parent = lockParentExchangeOf(requestId, requestRepository, exchangeLookup)
        val request = requestRepository.findRequestByIdForUpdate(requestId) ?: throw unboundLink()
        val shareLink = shareLinks.lockShareLinkForUpdate(candidate.id) ?: throw unknownLink()
        val party = partyRepository.findByIdForUpdate(boundParty.id)
        requireUsable(shareLink, party, enforceUseLimit)
        requestAccessSessionService.requireNoAuthPermitted(shareLink.id)
        return LockedInformationRequestBootstrapLink(request, shareLink, requireNotNull(party), parent)
    }

    private fun requireBootstrapMode(shareLink: ShareLink) {
        if (shareLink.linkMode != ShareLinkMode.VERIFICATION_BOOTSTRAP)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_INVALID,
                "This access link is not a request contact-proof link",
            )
        }
    }

    private fun requireUsable(shareLink: ShareLink, party: InformationRequestParty?, enforceUseLimit: Boolean) {
        requireNotAccountLinked(party)
        requireNotRevoked(shareLink)
        requireOpen(shareLink, party, enforceUseLimit)
    }

    private fun requireNotAccountLinked(party: InformationRequestParty?) {
        if (party?.active == true && party.linkedUserShareId != null) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_PARTY_ACCOUNT_LINKED,
                "This access link belongs to a participant who now signs in with a linked account",
            )
        }
    }

    private fun requireNotRevoked(shareLink: ShareLink) {
        if (shareLink.status == ShareLinkStatus.REVOKED)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_REVOKED,
                "This access link has been revoked",
            )
        }
    }

    private fun requireOpen(shareLink: ShareLink, party: InformationRequestParty?, enforceUseLimit: Boolean) {
        val now = Timestamp.from(Instant.now())
        val expiresAt = shareLink.expiresAt
        if (shareLink.status == ShareLinkStatus.EXPIRED || (expiresAt != null && !expiresAt.after(now)))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_EXPIRED,
                "This access link has expired",
            )
        }
        val maxUses = shareLink.maxUses
        if (enforceUseLimit && maxUses != null && shareLink.usedCount >= maxUses)
        {
            abuseSignal.refused(InformationRequestAbuseControl.ACCESS_LINK_USE_LIMIT, "link=${shareLink.id}")
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_EXHAUSTED,
                "This access link has reached its usage limit",
            )
        }
        if (party == null || !party.active) throw unboundLink()
        if (party.principalKind != PrincipalKind.PARTICIPANT)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_PARTY_NOT_ELIGIBLE,
                "This access link belongs to a party that signs in with an account",
            )
        }
    }

    private fun unknownLink() = InformationRequestLifecycleException(
        InformationRequestErrorCatalog.ACCESS_LINK_INVALID,
        "Invalid or unknown access link",
    )

    private fun unboundLink() = InformationRequestLifecycleException(
        InformationRequestErrorCatalog.ACCESS_LINK_INVALID,
        "This access link is no longer bound to an active request party",
    )

    private fun throwIfOtpLocked(shareLink: ShareLink)
    {
        val lockedUntil = shareLink.contactOtpLockedUntil ?: return
        if (lockedUntil.after(Timestamp.from(Instant.now())))
        {
            abuseSignal.refused(InformationRequestAbuseControl.CONTACT_CODE_LOCKOUT, "link=${shareLink.id}")
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_LOCKED,
                "Too many invalid verification attempts. Try again later.",
            )
        }
        shareLink.contactOtpLockedUntil = null
        shareLink.contactOtpFailedAttempts = 0
        shareLinks.update(shareLink)
    }

    private fun registerOtpFailure(shareLink: ShareLink): Int
    {
        val failedAttempt = shareLink.contactOtpFailedAttempts + 1
        shareLink.contactOtpFailedAttempts = failedAttempt
        if (failedAttempt >= OTP_MAX_FAILED_ATTEMPTS)
        {
            shareLink.contactOtpFailedAttempts = 0
            shareLink.contactOtpLockedUntil = Timestamp.from(Instant.now().plusSeconds(OTP_LOCKOUT_SECONDS))
            abuseSignal.refused(InformationRequestAbuseControl.CONTACT_CODE_LOCKOUT, "link=${shareLink.id} started")
        }
        shareLinks.update(shareLink)
        return failedAttempt
    }

    fun contactEmailOf(request: InformationRequest, party: InformationRequestParty): String? =
        party.principalId
            ?.takeIf { party.principalKind == PrincipalKind.PARTICIPANT }
            ?.let { externalParticipantService.findOwned(it, InformationRequestParticipantOwner.of(request))?.email }

    private fun hash(rawToken: String): String =
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object
    {
        const val OTP_VALIDITY_SECONDS: Long = 600
        const val OTP_MAX_FAILED_ATTEMPTS: Int = 5
        const val OTP_MAX_CHALLENGES: Int = 3
        const val OTP_LOCKOUT_SECONDS: Long = 300
        val SESSIONS_OUTLIVE_LINKS = setOf(InformationRequestState.CANCELLED, InformationRequestState.SUPERSEDED)
    }
}
