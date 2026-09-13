package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.IssuedRequestAccessSession
import com.docuhyphen.app.api.model.entity.RequestAccessSessionVerificationStrength
import com.docuhyphen.app.api.model.entity.ShareLink
import com.docuhyphen.app.api.model.entity.ShareLinkMode
import com.docuhyphen.app.api.model.entity.ShareLinkStatus
import com.docuhyphen.app.api.repository.exchange.ExternalParticipantRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.user.AppUserService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class InformationRequestContactProofService @Inject constructor(
    private val shareLinkRepository: ShareLinkRepository,
    private val partyRepository: InformationRequestPartyRepository,
    private val appUserService: AppUserService,
    private val externalParticipantRepository: ExternalParticipantRepository,
    private val otpService: OtpService,
    private val emailService: EmailService,
    private val requestAccessSessionService: RequestAccessSessionService,
)
{
    @Transactional
    fun issueChallenge(rawToken: String)
    {
        val (shareLink, party) = resolveBootstrapLink(rawToken, lockLink = true, enforceUseLimit = true)
        throwIfOtpLocked(shareLink)
        val email = resolvePartyContactEmail(party)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED,
                "This party has no contact address on file to verify",
            )

        val otp = otpService.generateEmailOtp()
        shareLink.contactOtpHash = otpService.hashOtp(otp)
        shareLink.contactOtpExpiresAt = Timestamp.from(Instant.now().plusSeconds(OTP_VALIDITY_SECONDS))
        shareLink.contactOtpFailedAttempts = 0
        shareLink.contactOtpLockedUntil = null
        shareLinkRepository.update(shareLink)

        emailService.sendEmail(
            email,
            "Verify your access to this request",
            "Your verification code is $otp. It expires in ${OTP_VALIDITY_SECONDS / 60} minutes.",
        )
    }

    @Transactional
    fun verifyChallenge(
        rawToken: String,
        otp: String,
        sessionExpiresAt: Timestamp? = null,
    ): IssuedRequestAccessSession
    {
        val (shareLink, party) = resolveBootstrapLink(rawToken, lockLink = true, enforceUseLimit = true)
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
            registerOtpFailure(shareLink)
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_INVALID,
                "Invalid verification code",
            )
        }

        shareLink.contactOtpHash = null
        shareLink.contactOtpExpiresAt = null
        shareLink.contactOtpFailedAttempts = 0
        shareLink.contactOtpLockedUntil = null
        shareLink.usedCount += 1
        shareLinkRepository.update(shareLink)

        val participant = PrincipalRef(
            kind = requireNotNull(party.principalKind) { "Information Request party has no principal" },
            id = requireNotNull(party.principalId) { "Information Request party has no principal" },
        )
        return requestAccessSessionService.issue(
            shareLink = shareLink,
            participant = participant,
            verificationStrength = RequestAccessSessionVerificationStrength.EMAIL_OTP,
            expiresAt = sessionExpiresAt,
        )
    }

    fun resolveBootstrapLink(
        rawToken: String,
        lockLink: Boolean = false,
        enforceUseLimit: Boolean = false,
    ): Pair<ShareLink, InformationRequestParty>
    {
        val tokenHash = hash(rawToken)
        val shareLink = (
            if (lockLink) shareLinkRepository.findByTokenHashForUpdate(tokenHash)
            else shareLinkRepository.findByTokenHash(tokenHash)
            )
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_INVALID,
                "Invalid or unknown access link",
            )
        if (shareLink.linkMode != ShareLinkMode.VERIFICATION_BOOTSTRAP)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_INVALID,
                "This access link is not a request contact-proof link",
            )
        }
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
        val maxUses = shareLink.maxUses
        if (enforceUseLimit && maxUses != null && shareLink.usedCount >= maxUses)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_EXHAUSTED,
                "This access link has reached its usage limit",
            )
        }

        val party = partyRepository.findByShareId(shareLink.shareId)
        if (party == null || !party.active)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_LINK_INVALID,
                "This access link is no longer bound to an active request party",
            )
        }
        return shareLink to party
    }

    private fun throwIfOtpLocked(shareLink: ShareLink)
    {
        val lockedUntil = shareLink.contactOtpLockedUntil ?: return
        if (lockedUntil.after(Timestamp.from(Instant.now())))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_LOCKED,
                "Too many invalid verification attempts. Try again later.",
            )
        }
        shareLink.contactOtpLockedUntil = null
        shareLink.contactOtpFailedAttempts = 0
        shareLinkRepository.update(shareLink)
    }

    private fun registerOtpFailure(shareLink: ShareLink)
    {
        shareLink.contactOtpFailedAttempts += 1
        if (shareLink.contactOtpFailedAttempts >= OTP_MAX_FAILED_ATTEMPTS)
        {
            shareLink.contactOtpFailedAttempts = 0
            shareLink.contactOtpLockedUntil = Timestamp.from(Instant.now().plusSeconds(OTP_LOCKOUT_SECONDS))
        }
        shareLinkRepository.update(shareLink)
    }

    private fun resolvePartyContactEmail(party: InformationRequestParty): String? =
        when (party.principalKind)
        {
            PrincipalKind.PARTICIPANT ->
                party.principalId?.let { externalParticipantRepository.findById(it)?.email }
            PrincipalKind.USER ->
                party.principalId?.let { appUserService.getById(it)?.email }
            else -> null
        }

    private fun hash(rawToken: String): String =
        MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object
    {
        const val OTP_VALIDITY_SECONDS: Long = 600
        const val OTP_MAX_FAILED_ATTEMPTS: Int = 5
        const val OTP_LOCKOUT_SECONDS: Long = 300
    }
}
