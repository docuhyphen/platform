package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.InvalidOtpException
import com.docuhyphen.app.api.exception.MaxAttemptsOTPExceededException
import com.docuhyphen.app.api.exception.OTPExpiredException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationStatus
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.EMAIL
import com.docuhyphen.app.api.service.communication.MfaService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Instant

data class StepUpMfaChallenge(
    val sessionId: String,
    val message: String,
    val mfaType: String,
    val emailFallbackEnabled: Boolean,
)

class StepUpMfaRateLimitedException(val retryAfterSeconds: Long) : RuntimeException(
    "Please wait before requesting another verification code."
)

@ApplicationScoped
class StepUpMfaChallengeService(
    private val mfaService: MfaService,
    private val otpService: OtpService,
    private val configurationService: ConfigurationService,
    private val authenticatorMfaService: AuthenticatorMfaService,
)
{
    @Transactional
    fun initiate(appUser: AppUser, ipAddress: String, actionDescription: String?): StepUpMfaChallenge
    {
        val friendlyActionDescription = StepUpActionLabelFormatter.labelFor(actionDescription)
        val mfaSession = mfaService.createMfaSession(
            user = appUser,
            mfaType = appUser.mfaType,
            ipAddress = ipAddress,
            actionDescription = friendlyActionDescription,
        )
        if (appUser.mfaType == EMAIL)
        {
            mfaService.doEmailMFA(appUser, mfaSession.mfaToken!!, friendlyActionDescription)
        }
        return StepUpMfaChallenge(
            sessionId = mfaSession.id.toString(),
            message = if (appUser.mfaType == EMAIL)
                "A verification code was sent to your email."
            else
                "Enter the code from your authenticator app.",
            mfaType = appUser.mfaType.name,
            emailFallbackEnabled = appUser.emailMfaFallbackEnabled,
        )
    }

    @Transactional(dontRollbackOn = [InvalidOtpException::class, MaxAttemptsOTPExceededException::class])
    fun complete(appUser: AppUser, sessionId: String, code: String)
    {
        val record = mfaService.getMfaRecordByEmailAndSessionId(appUser.email, sessionId)
            ?: throw InvalidOtpException("Invalid verification code.")
        if (record.mfaType != EMAIL && record.mfaType?.isAuthenticator() != true)
        {
            throw IllegalArgumentException("Unsupported step-up verification method.")
        }
        if (record.status == MultifactorAuthenticationStatus.COMPLETED)
        {
            throw InvalidOtpException("Verification code already used.")
        }
        if (record.status == MultifactorAuthenticationStatus.LOCKED)
        {
            throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
        }
        if (record.expiryDateTime?.before(Timestamp.from(Instant.now())) == true)
        {
            throw OTPExpiredException("Verification code expired.")
        }

        record.attemptCount++
        if (record.attemptCount > configurationService.getMaxSignInAttempts())
        {
            record.status = MultifactorAuthenticationStatus.LOCKED
            mfaService.updateRecord(record)
            throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
        }
        val valid = if (record.mfaType == EMAIL)
            otpService.verifyEmailOtp(code, record.mfaToken!!)
        else
            authenticatorMfaService.verifyUserCode(appUser, code)
        if (!valid)
        {
            mfaService.updateRecord(record)
            throw InvalidOtpException("Invalid verification code.")
        }

        record.status = MultifactorAuthenticationStatus.COMPLETED
        mfaService.updateRecord(record)
        mfaService.removeMfaRecord(record)
    }

    @Transactional
    fun regenerateOrFallback(appUser: AppUser, sessionId: String, ipAddress: String): StepUpMfaChallenge
    {
        val record = mfaService.getMfaRecordByEmailAndSessionId(appUser.email, sessionId)
            ?: throw InvalidOtpException("Invalid step-up session.")
        if (record.status == MultifactorAuthenticationStatus.COMPLETED)
        {
            throw InvalidOtpException("Step-up already completed.")
        }
        if (record.status == MultifactorAuthenticationStatus.LOCKED)
        {
            throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
        }
        val usingEmailFallback = record.mfaType?.isAuthenticator() == true
        if (usingEmailFallback)
        {
            if (!appUser.emailMfaFallbackEnabled)
            {
                throw IllegalArgumentException("Email fallback is not enabled for this account.")
            }
            record.mfaType = EMAIL
        }

        if (!usingEmailFallback)
        {
            val cooldownUntil = record.createdDate.toInstant()
                .plusSeconds(configurationService.getSignInResendCooldownSeconds())
            if (Instant.now().isBefore(cooldownUntil))
            {
                throw StepUpMfaRateLimitedException(cooldownUntil.epochSecond - Instant.now().epochSecond)
            }
        }
        mfaService.enforceRateLimits(appUser.email, record.ipAddress ?: ipAddress)
        val newCode = mfaService.regenerateOtp(record)
        mfaService.doEmailMFA(appUser, newCode, record.actionDescription)
        return StepUpMfaChallenge(
            sessionId = sessionId,
            message = "A new verification code has been sent.",
            mfaType = EMAIL.name,
            emailFallbackEnabled = appUser.emailMfaFallbackEnabled,
        )
    }
}
