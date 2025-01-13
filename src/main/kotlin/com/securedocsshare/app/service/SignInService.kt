package com.securedocsshare.app.service

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import java.util.Date
import com.securedocsshare.app.api.model.DataIntegrityException
import com.securedocsshare.app.api.model.InvalidOtpException
import com.securedocsshare.app.api.model.InvalidSignInCredentialsException
import com.securedocsshare.app.api.model.MaximumSignInAttemptsExceeded
import com.securedocsshare.app.api.model.OTPExpiredException
import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.AuthToken
import com.securedocsshare.app.api.model.AuthTokenNotFoundException
import com.securedocsshare.app.api.model.EmailRequiredException
import com.securedocsshare.app.api.model.MultifactorAuthenticationType
import com.securedocsshare.app.api.model.MfaRecord
import com.securedocsshare.app.repository.AppUserRepository
import com.securedocsshare.app.repository.AuthTokenRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.times
import kotlin.toString

@RequestScoped
class SignInService @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val authTokenRepository: AuthTokenRepository,
    private val mfaService: MfaService,
    private val appUserRepository: AppUserRepository,
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
        //ToDo: Implement rate limiting (e.g., allow only 5 attempts per minute per IP)

        if (email.isNullOrBlank() || password.isNullOrBlank())
        {
            val emailErrorMessage = when
            {
                email.isNullOrBlank() -> "Email is blank."
                authenticationService.isEmailInvalid(email) -> "Email is invalid."
                else -> ""
            }

            val passwordErrorMessage = if (password.isNullOrBlank()) "Password is blank." else ""

            val errorMessage = listOf(emailErrorMessage, passwordErrorMessage)
                .filter { it.isNotEmpty() }
                .joinToString(" ")

            logger.error("Sign in failed. $errorMessage")
            throw InvalidSignInCredentialsException()
        }

        val appUser = appUserRepository.findByEmail(email)
            ?: run {
                logger.warn("Sign in failed. App user doesn't exist with email: $email")
                throw InvalidSignInCredentialsException()
            }

        val passwordSalt = Base64.getDecoder().decode(appUser.passwordSalt)
        val isPasswordValid = authenticationService.validatePassword(password, appUser.password, passwordSalt)

        if (appUser.signInAttempts >= configurationService.getMaxSignInAttempts())
        {
            logger.warn("Unable to Sign In. Maximum sign in attempts exceeded for email: $email")
            throw MaximumSignInAttemptsExceeded();
        }

        if (!isPasswordValid || email != appUser.email)
        {
            val errorMessage = buildString {
                if (!isPasswordValid) append("Password is invalid. ")
                if (email != appUser.email) append("Email is invalid.")
            }.trim()

            logger.warn("Sign in failed. $errorMessage")
            throw InvalidSignInCredentialsException()
        }

        doSignInMFA(appUser)
    }

    fun completeSignIn(email: String?, otp: String?, mfaType: MultifactorAuthenticationType?): String
    {
        if (otp.isNullOrBlank())
        {
            logger.warn("Sign in completion failed. OTP is null or blank.")
            throw InvalidOtpException()
        }

        if (email.isNullOrBlank())
        {
            logger.warn("Sign in completion failed. Email is null or blank.")
            throw EmailRequiredException()
        }

        val mfaRecord = mfaService.getMfaRecordByEmailAndOtp(email, otp)
            ?: run {
                logger.warn("Sign in completion failed. Invalid OTP or MFA type.")
                throw InvalidOtpException()
            }

        mfaRecord.expiryDateTime?.let { expiryDateTime ->

            if (expiryDateTime.before(Timestamp.from(Instant.now())))
            {
                logger.warn("Sign in completion failed. OTP expired.")
                throw OTPExpiredException("OTP has expired")
            }
        } ?: {

            logger.error("Error completing sign in. MFA record has an expiry date time that is null. THIS SHOULDN'T HAPPEN ")
            throw DataIntegrityException("An unknown error occurred completing sign-in.")
        }

        val appUser = mfaRecord.appUser ?: run {
            logger.error("Error completing sign in failed. MFA record AppUser is null. THIS SHOULDN'T HAPPEN")
            throw DataIntegrityException("An unknown error occurred completing sign-in.")
        }

        val token = authenticationService.generateSignInToken(appUser)

        persistToken(appUser, token)

        logger.info("Sign-in completed successfully. Token issued for user ${appUser.email}")

        return token
    }

    fun refreshToken(oldToken: String): String
    {
        val newToken = authenticationService.refreshToken(oldToken)
        val claims = Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(configurationService.getJwtSecret().toByteArray()))
            .build()
            .parseClaimsJws(newToken)
            .body

        appUserRepository.findById(UUID.fromString(claims.subject))?.let {
            persistToken(it, newToken)
        } ?: AuthTokenNotFoundException()

        return newToken
    }

    private fun persistToken(appUser: AppUser, token: String)
    {
        val tokenRecord = AuthToken().apply {
            this.appUser = appUser
            this.token = token
            this.expiryDateTime =
                Timestamp.from(Instant.now().plusMillis(TimeUnit.HOURS.toMillis(configurationService.getSignInTokenExpiryHours())))
            this.createdDate = Timestamp.from(Instant.now())
        }

        authTokenRepository.save(tokenRecord)
    }

    private fun doSignInMFA(appUser: AppUser)
    {
        val mfaRecord = MfaRecord().apply {
            this.id = UUID.randomUUID()
            this.appUser = appUser
            this.mfaType = mfaType
            this.createdDate = Timestamp.from(Instant.now())
        }

        when (appUser.mfaType)
        {
            MultifactorAuthenticationType.EMAIL ->
            {
                val configExpiryMinutes = configurationService.getSignInEmailOtpMFAExpiryMins()
                val expiryDate =
                    Timestamp.from(Instant.now().plusSeconds(TimeUnit.MINUTES.toMillis(configExpiryMinutes)))

                mfaRecord.expiryDateTime = expiryDate
                mfaRecord.mfaToken = otpService.generateEmailOtp()

                mfaService.doEmailMFA(appUser, mfaRecord)

                logger.info("Email MFA has been completed")
            }

            MultifactorAuthenticationType.SMS ->
            {
                val configExpiryMinutes = configurationService.getSignInSmsOtpMFAExpiryMins()
                val expiryDate =
                    Timestamp.from(Instant.now().plusSeconds(TimeUnit.MINUTES.toMillis(configExpiryMinutes)))

                mfaRecord.expiryDateTime = expiryDate
                mfaRecord.mfaToken = otpService.generateSmsOtp()

                mfaService.doSmsMFA(appUser, mfaRecord)
                logger.info("SMS MFA has been completed")
            }

            MultifactorAuthenticationType.PASSKEY ->
            {
                mfaService.doPasskeyMFA(appUser, mfaRecord)
                logger.info("Passkey MFA has been completed")
            }
        }

        mfaService.saveMfaRecord(mfaRecord)
    }
}
