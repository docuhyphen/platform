package com.dochyphen.app.api.service.auth

import com.dochyphen.app.api.exception.InvalidOtpException
import com.dochyphen.app.api.exception.InvalidSignInCredentialsException
import com.dochyphen.app.api.exception.OTPExpiredException
import com.dochyphen.app.api.model.entity.AuthToken
import com.dochyphen.app.api.model.entity.MfaRecord
import com.dochyphen.app.api.model.entity.MultifactorAuthenticationType
import com.dochyphen.app.api.service.AppUserService
import com.dochyphen.app.api.service.config.ConfigurationService
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.communication.MfaService
import com.dochyphen.app.api.service.communication.OtpService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
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

    fun initiateSignIn(email: String?, password: String?)
    {
        if (email.isNullOrBlank() || password.isNullOrBlank())
        {
            val emailErrorMessage = when
            {
                email.isNullOrBlank() -> "Email is blank."
                authenticationService.isEmailInvalid(email) -> "Email is invalid."
                else -> ""
            }

            val passwordErrorMessage = if (password.isNullOrBlank()) "Password is blank." else ""

            val errorMessage =
                listOf(emailErrorMessage, passwordErrorMessage)
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")

            logger.warn("Sign in failed: $errorMessage")
            throw InvalidSignInCredentialsException()
        }

        val appUser = appUserService.findUserByEmail(email) ?: throw InvalidSignInCredentialsException()

        if (!authenticationService.validatePassword(password, appUser.password!!))
        {
            logger.warn("Sign in failed: Invalid password for email $email")
            throw InvalidSignInCredentialsException()
        }

        // Generate OTP for MFA
        val otp = otpService.generateEmailOtp()
        val hashedOtp = otpService.hashOtp(otp)
        val expirationTime = Timestamp.from(
            Instant.now().plusMillis(TimeUnit.MINUTES.toMillis(configurationService.getSignInEmailOtpMFAExpiryMins()))
        )

        // Save OTP to MfaRecordRepository
        val mfaRecord = MfaRecord().apply {
            this.appUser = appUser
            this.mfaToken = hashedOtp
            this.expiryDateTime = expirationTime
            this.mfaType = MultifactorAuthenticationType.EMAIL
        }

        mfaService.saveRecord(mfaRecord)

        // Send OTP via email
        emailService.sendEmail(
            to = email,
            subject = "${configurationService.getAppEmailSubjectTitle()} | Sign In OTP",
            body = "Your OTP for sign in is: $otp. It will expire in ${configurationService.getSignInEmailOtpMFAExpiryMins()} minutes."
        )

        logger.info("Sign in initiated for email $email. OTP sent.")
    }

    fun completeSignIn(email: String?, otp: String?): String
    {
        if (email.isNullOrBlank() || otp.isNullOrBlank())
        {
            val emailErrorMessage = if (email.isNullOrBlank()) "Email is blank." else ""
            val otpErrorMessage = if (otp.isNullOrBlank()) "OTP is blank." else ""

            val errorMessage = listOf(emailErrorMessage, otpErrorMessage).filter { it.isNotEmpty() }.joinToString(" ")

            logger.warn("Sign in completion failed: $errorMessage")
            throw InvalidOtpException()
        }

        val mfaRecord = mfaService.getLatestMfaRecordByEmail(email) ?: throw InvalidOtpException()

        if (mfaRecord.expiryDateTime!!.before(Timestamp.from(Instant.now())))
        {
            logger.warn("Sign in completion failed: OTP expired for email $email")
            throw OTPExpiredException("OTP expired.")
        }

        if (!BCrypt.checkpw(otp, mfaRecord.mfaToken))
        {
            logger.warn("Sign in completion failed: Invalid OTP for email $email")
            throw InvalidOtpException()
        }

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

        logger.info("Sign in completed for email $email. JWT token generated.")
        return signInToken
    }
}