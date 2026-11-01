package com.docuhyphen.app.api.service.informationrequest.noauth

import com.docuhyphen.app.api.model.entity.RequestAccessSession
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAbuseControl
import com.docuhyphen.app.api.model.informationrequest.noauth.InformationRequestAccessSessionStepUp
import com.docuhyphen.app.api.model.informationrequest.noauth.LockedRequestAccessSession
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.exchange.ExternalParticipantService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.access.RequestAccessSessionStepUpWindow
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestParticipantOwner
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class InformationRequestAccessSessionStepUpService @Inject constructor(
    private val contactProofService: InformationRequestContactProofService,
    private val sessionService: RequestAccessSessionService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
    private val stepUpWindow: RequestAccessSessionStepUpWindow,
    private val otpService: OtpService,
    private val codeMailer: InformationRequestVerificationCodeMailer,
    private val recorder: InformationRequestContactProofRecorder,
    private val abuseSignal: InformationRequestAbuseSignal,
    private val externalParticipantService: ExternalParticipantService,
) {
    @Transactional
    fun issueChallenge(rawToken: String, sessionToken: String?) {
        val (link, session) = lockVerifiedSession(rawToken, sessionToken)
        requireStepUpUnlocked(session)
        if (session.stepUpChallengeCount >= MAX_PENDING_CHALLENGES) {
            abuseSignal.refused(InformationRequestAbuseControl.CONTACT_CODE_CHALLENGE_LIMIT, "session=${session.id}")
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_CHALLENGE_LIMIT,
                "This session has reached its verification code limit. Open the access link again to continue.",
            )
        }
        val email = contactProofService.contactEmailOf(link.request, link.party)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED,
                "This party has no contact address on file to verify",
            )

        val code = otpService.generateEmailOtp()
        session.stepUpCodeHash = otpService.hashOtp(code)
        session.stepUpCodeExpiresAt = Timestamp.from(Instant.now().plusSeconds(CODE_VALIDITY_SECONDS))
        session.stepUpChallengeCount += 1
        sessionService.update(session)
        recorder.recordStepUpChallenge(link.request, link.party, link.shareLink, session)

        codeMailer.sendAfterCommit(
            email,
            "Verify again to see protected questions",
            "Your verification code is $code. It expires in ${CODE_VALIDITY_SECONDS / 60} minutes.",
        )
    }

    @Transactional(dontRollbackOn = [ContactProofInvalidException::class])
    fun verify(rawToken: String, sessionToken: String?, code: String): InformationRequestAccessSessionStepUp {
        val (link, session) = lockVerifiedSession(rawToken, sessionToken)
        requireStepUpUnlocked(session)

        val storedHash = session.stepUpCodeHash
        val expiresAt = session.stepUpCodeExpiresAt
        if (storedHash == null || expiresAt == null) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_REQUIRED,
                "No verification code has been sent for this session",
            )
        }
        if (!expiresAt.after(Timestamp.from(Instant.now()))) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_EXPIRED,
                "Verification code has expired",
            )
        }
        if (code.isBlank() || !otpService.verifyEmailOtp(code, storedHash)) {
            val failedAttempt = registerFailure(session)
            recorder.recordStepUpRefusal(link.request, link.party, link.shareLink, session, failedAttempt)
            throw ContactProofInvalidException()
        }

        session.stepUpCodeHash = null
        session.stepUpCodeExpiresAt = null
        session.stepUpChallengeCount = 0
        session.stepUpFailedAttempts = 0
        session.steppedUpAt = Timestamp.from(Instant.now())
        sessionService.update(session)
        val verifiedUntil = Timestamp.from(stepUpWindow.verifiedUntil(session))
        recorder.recordStepUp(link.request, link.party, link.shareLink, session, verifiedUntil)
        externalParticipantService.verifyContact(
            InformationRequestParticipantOwner.of(link.request),
            requireNotNull(link.party.principalId) { "Information Request party has no principal" },
        )
        return InformationRequestAccessSessionStepUp(session.id, verifiedUntil)
    }

    private fun lockVerifiedSession(rawToken: String, sessionToken: String?): LockedRequestAccessSession {
        if (sessionToken.isNullOrBlank()) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.ACCESS_SESSION_REQUIRED,
                "A verified request session credential is required",
            )
        }
        val link = contactProofService.lockBootstrapLink(rawToken, enforceUseLimit = false)
        val session = sessionService.authenticate(sessionToken)
        val participant = PrincipalRef(
            kind = requireNotNull(link.party.principalKind) { "Information Request party has no principal" },
            id = requireNotNull(link.party.principalId) { "Information Request party has no principal" },
        )
        accessContextFactory.fromBootstrapSession(link.shareLink, session, participant)
        requireCredentialCommandAllowed(link, InformationRequestMutation.STEP_UP_ACCESS_SESSION)
        return LockedRequestAccessSession(link, session)
    }

    private fun requireStepUpUnlocked(session: RequestAccessSession) {
        val lockedUntil = session.stepUpLockedUntil ?: return
        if (lockedUntil.after(Timestamp.from(Instant.now()))) {
            abuseSignal.refused(InformationRequestAbuseControl.CONTACT_CODE_LOCKOUT, "session=${session.id}")
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.CONTACT_PROOF_LOCKED,
                "Too many invalid verification attempts. Try again later.",
            )
        }
        session.stepUpLockedUntil = null
        session.stepUpFailedAttempts = 0
        sessionService.update(session)
    }

    private fun registerFailure(session: RequestAccessSession): Int {
        val failedAttempt = session.stepUpFailedAttempts + 1
        session.stepUpFailedAttempts = failedAttempt
        if (failedAttempt >= MAX_FAILED_ATTEMPTS) {
            session.stepUpFailedAttempts = 0
            session.stepUpLockedUntil = Timestamp.from(Instant.now().plusSeconds(LOCKOUT_SECONDS))
            abuseSignal.refused(InformationRequestAbuseControl.CONTACT_CODE_LOCKOUT, "session=${session.id} started")
        }
        sessionService.update(session)
        return failedAttempt
    }

    private companion object {
        const val CODE_VALIDITY_SECONDS: Long = 600
        const val MAX_FAILED_ATTEMPTS: Int = 5
        const val MAX_PENDING_CHALLENGES: Int = 5
        const val LOCKOUT_SECONDS: Long = 300
    }
}
