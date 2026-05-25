package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.InactiveAccountException
import com.docuhyphen.app.api.exception.InvalidOtpException
import com.docuhyphen.app.api.exception.InvalidSignInCredentialsException
import com.docuhyphen.app.api.exception.MaxAttemptsOTPExceededException
import com.docuhyphen.app.api.exception.OTPExpiredException
import com.docuhyphen.app.api.exception.TooManyRequestsException
import com.docuhyphen.app.api.extension.maskEmailForLogs
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.model.dto.MfaSessionDto
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationStatus
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.EMAIL
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.MfaService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class SignInService @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val mfaService: MfaService,
    private val appUserService: AppUserService,
    private val otpService: OtpService,
    private val configurationService: ConfigurationService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
)
{

    companion object
    {
        private val logger = LoggerFactory.getLogger(SignInService::class.java)
    }

    @Transactional
    fun initiateSignIn(email: String?, password: String?, ipAddress: String?): MfaSessionDto
    {
        if (email.isNullOrBlank() || password.isNullOrBlank() || ipAddress.isNullOrBlank())
        {
            val emailErrorMessage = when
            {
                email.isNullOrBlank() -> "Email is blank."
                authenticationService.isEmailInvalid(email) -> "Email is invalid. "
                else -> ""
            }

            val passwordErrorMessage = if (password.isNullOrBlank()) "Password is blank. " else ""

            val ipAddressErrorMessage = if (ipAddress.isNullOrBlank()) "IP address is blank. " else ""

            val errorMessage =
                listOf(emailErrorMessage, passwordErrorMessage, ipAddressErrorMessage)
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")

            logger.warn("Sign in failed: $errorMessage")
            throw InvalidSignInCredentialsException()
        }

        val sanitizedEmail = email.normalizeEmailOrNull()!!

        val appUser = appUserService.findByEmail(sanitizedEmail) ?: throw InvalidSignInCredentialsException()

        if (!appUser.isActive || appUser.deprovisionedAt != null)
        {
            logger.warn("Sign in blocked: inactive/deprovisioned account for {}", sanitizedEmail.maskEmailForLogs())
            throw InactiveAccountException()
        }

        if (!authenticationService.validatePassword(password, appUser.password!!))
        {
            logger.warn("Sign in failed: Invalid password for {}", sanitizedEmail.maskEmailForLogs())
            throw InvalidSignInCredentialsException()
        }

        val mfaSession = mfaService.createMfaSession(
            user = appUser,
            mfaType = appUser.mfaType,
            ipAddress = ipAddress,
        )

        when (appUser.mfaType)
        {
            EMAIL -> mfaService.doEmailMFA(appUser, mfaSession.mfaToken!!)
            MultifactorAuthenticationType.SMS -> TODO("Implement SMS OTP sending")
            MultifactorAuthenticationType.PASSKEY -> TODO("Implement passkey OTP sending")
            else ->
            {
                logger.warn("Sign in failed: MFA type is NONE for {}", sanitizedEmail.maskEmailForLogs())
                throw InvalidSignInCredentialsException()
            }
        }

        logger.info("Sign in initiated for {}", sanitizedEmail.maskEmailForLogs())

        return mfaSession
    }

    @Transactional
    fun completeSignIn(email: String?, otp: String?, sessionId: String?, userAgent: String? = null, ipAddress: String? = null): TokenTriple
    {
        if (email.isNullOrBlank() || otp.isNullOrBlank() || sessionId.isNullOrBlank())
        {
            val emailErrorMessage = when
            {
                email.isNullOrBlank() -> "Email is blank."
                authenticationService.isEmailInvalid(email) -> "Email is invalid. "
                else -> ""
            }

            val otpErrorMessage = if (otp.isNullOrBlank()) "OTP is blank." else ""

            val sessionIdErrorMessage = if (sessionId.isNullOrBlank()) "Session ID is blank." else ""

            val errorMessage = listOf(emailErrorMessage, otpErrorMessage, sessionIdErrorMessage)
                .filter { it.isNotEmpty() }.joinToString(" ")

            logger.warn("Sign in completion failed: $errorMessage")
            throw InvalidOtpException("Invalid verification code")
        }

        val sanitizedEmail = email.normalizeEmailOrNull()!!
        val sanitizedOTP = otp.trim()
        val mfaRecord =
            mfaService.getMfaRecordByEmailAndSessionId(sanitizedEmail, sessionId) ?: throw InvalidOtpException("Invalid verification code")

        if (mfaRecord.expiryDateTime!!.before(Timestamp.from(Instant.now())))
        {
            logger.warn("Sign in completion failed: verification code expired for {}", sanitizedEmail.maskEmailForLogs())

            throw OTPExpiredException("Verification code expired.")
        }

        // Track verification attempts
        mfaRecord.attemptCount++

        if (mfaRecord.attemptCount > configurationService.getMaxSignInAttempts())
        {
            mfaRecord.status = MultifactorAuthenticationStatus.LOCKED
            mfaService.updateRecord(mfaRecord)

            throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
        }

        when (mfaRecord.mfaType)
        {
            EMAIL ->
            {
                if (mfaRecord.status == MultifactorAuthenticationStatus.COMPLETED)
                {
                    logger.warn("Sign in completion failed: OTP already used for {}", sanitizedEmail.maskEmailForLogs())
                    throw InvalidOtpException("Invalid verification code")
                }

                if (mfaRecord.status == MultifactorAuthenticationStatus.LOCKED)
                {
                    logger.warn("Sign in completion failed: OTP locked for {}", sanitizedEmail.maskEmailForLogs())
                    throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
                }

                if (!otpService.verifyEmailOtp(sanitizedOTP, mfaRecord.mfaToken!!))
                {
                    logger.warn("Sign in completion failed: Invalid OTP for {}", sanitizedEmail.maskEmailForLogs())
                    throw InvalidOtpException("Invalid verification code")
                }
            }

            else ->
            {
                logger.warn("Sign in completion failed: Unsupported MFA type for {}", sanitizedEmail.maskEmailForLogs())
                throw Exception("Server error: unsupported MFA type.")
            }
        }

        mfaRecord.status = MultifactorAuthenticationStatus.COMPLETED
        mfaService.updateRecord(mfaRecord)

        // Issue token triple via shared service
        val tokenTriple = tokenIssuanceService.issueTokenTriple(mfaRecord.appUser!!, userAgent, ipAddress)

        mfaService.removeMfaRecord(mfaRecord)

        logger.info("Sign in completed for {}", sanitizedEmail.maskEmailForLogs())
        return tokenTriple
    }

    @Transactional
    fun redoMfa(email: String?, sessionId: String?): MfaSessionDto
    {
        if (email.isNullOrBlank() || sessionId.isNullOrBlank())
        {
            val emailErrorMessage = when
            {
                email.isNullOrBlank() -> "Email is blank."
                authenticationService.isEmailInvalid(email) -> "Email is invalid. "
                else -> ""
            }

            val sessionIdErrorMessage = if (sessionId.isNullOrBlank()) "Session ID is blank." else ""

            val errorMessage = listOf(emailErrorMessage, sessionIdErrorMessage)
                .filter { it.isNotEmpty() }.joinToString(" ")

            logger.warn("Redo MFA failed: $errorMessage")
            throw InvalidSignInCredentialsException()
        }

        val sanitizedEmail = email.normalizeEmailOrNull()!!
        val mfaRecord =
            mfaService.getMfaRecordByEmailAndSessionId(sanitizedEmail, sessionId) ?: throw InvalidSignInCredentialsException()

        if (mfaRecord.status == MultifactorAuthenticationStatus.COMPLETED)
        {
            logger.warn("Redo MFA failed: OTP already used for {}", sanitizedEmail.maskEmailForLogs())
            throw InvalidSignInCredentialsException()
        }

        if (mfaRecord.status == MultifactorAuthenticationStatus.LOCKED)
        {
            logger.warn("Redo MFA failed: OTP locked for {}", sanitizedEmail.maskEmailForLogs())
            throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
        }

        val resendCooldownSeconds = configurationService.getSignInResendCooldownSeconds()
        val cooldownUntil = mfaRecord.createdDate.toInstant().plusSeconds(resendCooldownSeconds)
        if (Instant.now().isBefore(cooldownUntil))
        {
            throw TooManyRequestsException("Please wait before requesting another verification code.")
        }

        mfaService.enforceRateLimits(sanitizedEmail, mfaRecord.ipAddress ?: "0.0.0.0")

        // Regenerate OTP and get the plaintext version for sending
        val newOtp = mfaService.regenerateOtp(mfaRecord)

        // Send the new OTP based on MFA type
        when (mfaRecord.mfaType)
        {
            EMAIL -> {
                val emailBody = emailTemplateService.renderSignInMfaResendEmail(
                    otp = newOtp,
                    expiryMinutes = configurationService.getSignInEmailOtpMFAExpiryMins(),
                )

                emailService.sendEmail(
                    to = mfaRecord.appUser!!.email,
                    subject = "${configurationService.emailSubjectTitle} | Sign In Verification",
                    body = emailBody,
                    useHtml = true,
                )
            }
            MultifactorAuthenticationType.SMS -> TODO("Implement SMS OTP sending")
            MultifactorAuthenticationType.PASSKEY -> TODO("Implement passkey OTP sending")
            else ->
            {
                logger.warn("Redo MFA failed: Unsupported MFA type for {}", sanitizedEmail.maskEmailForLogs())
                throw Exception("Server error: unsupported MFA type.")
            }
        }

        logger.info("Redo MFA initiated for {}", sanitizedEmail.maskEmailForLogs())

        return MfaSessionDto().apply {
            this.id = UUID.fromString(mfaRecord.sessionId)
            // Don't include plaintext OTP in response for security
            this.mfaTokenHashed = mfaRecord.mfaToken
        }
    }

}