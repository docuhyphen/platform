package com.dochyphen.app.api.service.auth

import com.dochyphen.app.api.exception.InvalidOtpException
import com.dochyphen.app.api.exception.InvalidSignInCredentialsException
import com.dochyphen.app.api.exception.MaxAttemptsOTPExceededException
import com.dochyphen.app.api.exception.OTPExpiredException
import com.dochyphen.app.api.model.dto.MfaSessionDto
import com.dochyphen.app.api.model.entity.AuthToken
import com.dochyphen.app.api.model.entity.MultifactorAuthenticationStatus
import com.dochyphen.app.api.model.entity.MultifactorAuthenticationType
import com.dochyphen.app.api.model.entity.MultifactorAuthenticationType.EMAIL
import com.dochyphen.app.api.service.AppUserService
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.communication.MfaService
import com.dochyphen.app.api.service.communication.OtpService
import com.dochyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@RequestScoped
class SignInService @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val mfaService: MfaService,
    private val appUserService: AppUserService,
    private val otpService: OtpService,
    private val configurationService: ConfigurationService,
    private val emailService: EmailService,
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

        val sanitizedEmail = email.trim().lowercase()

        val appUser = appUserService.findByEmail(email) ?: throw InvalidSignInCredentialsException()

        if (!authenticationService.validatePassword(password, appUser.password!!))
        {
            logger.warn("Sign in failed: Invalid password for email $sanitizedEmail")
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
                logger.warn("Sign in failed: MFA type is NONE for email $sanitizedEmail")
                throw InvalidSignInCredentialsException()
            }
        }

        logger.info("Sign in initiated for email $sanitizedEmail. OTP sent.")

        return mfaSession
    }

    @Transactional
    fun completeSignIn(email: String?, otp: String?, sessionId: String?): String
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

        val sanitizedEmail = email.trim().lowercase()
        val sanitizedOTP = otp.trim()
        val mfaRecord =
            mfaService.getMfaRecordByEmailAndSessionId(sanitizedEmail, sessionId) ?: throw InvalidOtpException("Invalid verification code")

        if (mfaRecord.expiryDateTime!!.before(Timestamp.from(Instant.now())))
        {
            logger.warn("Sign in completion failed: verification code expired for email $sanitizedEmail")

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
                    logger.warn("Sign in completion failed: OTP already used for email $sanitizedEmail")
                    throw InvalidOtpException("Invalid verification code")
                }

                if (mfaRecord.status == MultifactorAuthenticationStatus.LOCKED)
                {
                    logger.warn("Sign in completion failed: OTP locked for email $sanitizedEmail")
                    throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
                }

                if (!otpService.verifyEmailOtp(sanitizedOTP, mfaRecord.mfaToken!!))
                {
                    logger.warn("Sign in completion failed: Invalid OTP for email $sanitizedEmail")
                    throw InvalidOtpException("Invalid verification")
                }
            }

            else ->
            {
                logger.warn("Sign in completion failed: Unsupported MFA type for email $sanitizedEmail")
                throw Exception("Server error: unsupported MFA type.")
            }
        }


        mfaRecord.status = MultifactorAuthenticationStatus.COMPLETED
        mfaService.updateRecord(mfaRecord)

        // Generate JWT token
        val signInToken = authenticationService.generateSignInToken(mfaRecord.appUser!!)

        // Save AuthToken to AuthTokenRepository
        val authToken = AuthToken().apply {
            this.appUser = mfaRecord.appUser
            this.token = signInToken
            this.expiryDateTime = Timestamp.from(
                Instant.now().plusMillis(TimeUnit.HOURS.toMillis(configurationService.getSignInTokenExpiryHours()))
            )
        }

        authenticationService.saveAuthToken(authToken)
        mfaService.removeMfaRecord(mfaRecord)

        logger.info("Sign in completed for email $sanitizedEmail. JWT token generated.")
        return signInToken
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

        val sanitizedEmail = email.trim().lowercase()
        val mfaRecord =
            mfaService.getMfaRecordByEmailAndSessionId(sanitizedEmail, sessionId) ?: throw InvalidSignInCredentialsException()

        if (mfaRecord.status == MultifactorAuthenticationStatus.COMPLETED)
        {
            logger.warn("Redo MFA failed: OTP already used for email $sanitizedEmail")
            throw InvalidSignInCredentialsException()
        }

        if (mfaRecord.status == MultifactorAuthenticationStatus.LOCKED)
        {
            logger.warn("Redo MFA failed: OTP locked for email $sanitizedEmail")
            throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
        }

        // Regenerate OTP and get the plaintext version for sending
        val newOtp = mfaService.regenerateOtp(mfaRecord)

        // Send the new OTP based on MFA type
        when (mfaRecord.mfaType)
        {
            EMAIL -> {
                emailService.sendEmail(
                    to = mfaRecord.appUser!!.email,
                    subject = "${configurationService.getAppEmailSubjectTitle()} | Sign In Verification",
                    body = """Your new verification code for your sign in is: $newOtp.
                            |It will expire in ${configurationService.getSignInEmailOtpMFAExpiryMins()} minutes.
                            |If you didn't request this code, please ignore this email.""".trimMargin()
                )
            }
            MultifactorAuthenticationType.SMS -> TODO("Implement SMS OTP sending")
            MultifactorAuthenticationType.PASSKEY -> TODO("Implement passkey OTP sending")
            else ->
            {
                logger.warn("Redo MFA failed: Unsupported MFA type for email $sanitizedEmail")
                throw Exception("Server error: unsupported MFA type.")
            }
        }

        logger.info("Redo MFA initiated for email $sanitizedEmail. New OTP sent.")

        return MfaSessionDto().apply {
            this.id = UUID.fromString(mfaRecord.sessionId)
            // Don't include plaintext OTP in response for security
            this.mfaTokenHashed = mfaRecord.mfaToken
        }
    }
}