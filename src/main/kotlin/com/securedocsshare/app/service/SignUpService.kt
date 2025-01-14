package com.securedocsshare.app.service

import com.securedocsshare.app.api.model.*
import com.securedocsshare.app.api.model.MultifactorAuthenticationStatus.COMPLETED
import com.securedocsshare.app.repository.AppUserRepository
import com.securedocsshare.app.repository.SignUpRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64

@ApplicationScoped
class SignUpService @Inject constructor(
    private val signUpRepository: SignUpRepository,
    private val appUserRepository: AppUserRepository,
    private val emailService: EmailService,
    private val otpService: OtpService,
    private val configurationService: ConfigurationService,
    private val authenticationService: AuthenticationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignUpService::class.java)
    }

    fun initiateSignUp(email: String?)
    {
        if (email.isNullOrBlank())
        {
            logger.warn("Sign up failed: Email is null or blank")
            throw EmailRequiredException()
        }

        if (authenticationService.isEmailInvalid(email))
        {
            logger.warn("Sign up failed: Email ($email) validation failed")
            throw InvalidEmailException()
        }

        try
        {
            appUserRepository.findByEmail(email)?.let {
                throw AppUserExistsException()
            }

            val existingSignUp = signUpRepository.findByEmail(email)

            val otp = otpService.generateEmailOtp()
            val expirationMinutes = configurationService.getSignUpOtpExpiryMins()
            val appBaseUrl = configurationService.getAppBaseURL()
            val emailConfirmationLink = "$appBaseUrl/sign-up/email-confirm?email=$email&otp=$otp"

            if (existingSignUp != null)
            {
                if (existingSignUp.expiresAt.isAfter(LocalDateTime.now()))
                {
                    throw ExistingSignUpException()
                }

                existingSignUp.apply {
                    this.otp = otpService.hashOtp(otp)

                    this.expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes.toLong())
                }
                signUpRepository.update(existingSignUp)
            }
            else
            {
                val signUpEntity = SignUpEntity().apply {
                    this.email = email
                    this.otp = otpService.hashOtp(otp)
                    this.expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes.toLong())
                }
                signUpRepository.save(signUpEntity)
            }

            emailService.sendEmail(
                email, "${configurationService.getAppEmailSubjectTitle()} | Sign Up", """
            Thank you for signing up with Secure Document Share.
            Here's the OTP you'll need to continue: $otp
            Alternatively, you can click on this link: $emailConfirmationLink
            
            NOTE: The OTP expires in $expirationMinutes minutes.
        """.trimIndent()
            )

            logger.info("Sign up successful. Email: $email, otp: $otp")
        }
        catch (exception: Exception)
        {
            logger.error("Failed to initiate signup. ", exception)
            throw exception
        }
    }

    fun regenerateOtp(email: String?)
    {
        if (email.isNullOrBlank())
        {
            logger.warn("Sign up OTP regeneration failed: Email is null or blank")
            throw EmailRequiredException()
        }

        if (authenticationService.isEmailInvalid(email))
        {
            logger.warn("Sign up OTP generation failed: Email ($email) validation failed")
            throw InvalidEmailException()
        }

        appUserRepository.findByEmail(email)?.let {
            throw AppUserExistsException()
        }

        val signUpEntity = signUpRepository.findByEmail(email)

        if (signUpEntity == null)
        {
            logger.warn("Sign up OTP regeneration failed: Entity not found by ($email)")
            throw EmailNotFoundException()
        }

        if (signUpEntity.status != SignUpStatus.PENDING)
        {
            logger.warn("Sign up OTP regeneration failed: Entity (${signUpEntity.status}) is not Pending")
            throw InvalidSignUpStatusException("Cannot regenerate OTP for a non-pending sign-up.")
        }

        val newOtp = otpService.generateEmailOtp()
        val configExpiryMinutes = configurationService.getSignUpOtpExpiryMins()
        val expirationTime = LocalDateTime.now().plusMinutes(configExpiryMinutes)

        signUpEntity.otp = otpService.hashOtp(newOtp)
        signUpEntity.expiresAt = expirationTime

        signUpRepository.update(signUpEntity)

        emailService.sendEmail(
            to = email,
            subject = "${configurationService.getAppEmailSubjectTitle()} | Your OTP has been regenerated",
            body = "Your new OTP is: $newOtp. It will expire in ${configurationService.getSignUpOtpExpiryMins()} minutes."
        )

        logger.info("Sign up OTP regeneration successful")
    }

    fun completeSignUp(email: String?, otp: String?, password: String?, passwordConfirmation: String?): AppUser
    {
        validateInputs(email, otp, password, passwordConfirmation)

        val signUpEntity = signUpRepository.findByEmail(email!!) ?: throw EmailNotFoundException().also {
            logger.warn("Sign up completion failed: Entity not found with email ($email)")
        }

        handleMaxAttempts(signUpEntity)

        ensureOtpValidity(signUpEntity, otp)

        return finalizeSignUp(signUpEntity, email, password!!)
    }

    private fun validateInputs(email: String?, otp: String?, password: String?, passwordConfirmation: String?)
    {
        if (email.isNullOrBlank()) throw EmailRequiredException().also {
            logger.warn("Sign up completion failed: Email is null or blank")
        }

        appUserRepository.findByEmail(email)?.let {
            throw AppUserExistsException()
        }

        if (authenticationService.isEmailInvalid(email)) throw InvalidEmailException().also { logger.warn("Sign up completion failed: Email validation failed") }

        if (otp.isNullOrBlank()) throw OtpRequiredException().also { logger.warn("Sign up completion failed: OTP is null or blank") }
        if (password.isNullOrBlank()) throw PasswordRequiredException().also { logger.warn("Sign up completion failed: Password is null or blank") }
        if (passwordConfirmation.isNullOrBlank()) throw ConfirmationPasswordRequiredException().also { logger.warn("Sign up completion failed: Confirmation password is null or blank") }

        if (!authenticationService.isPasswordStrong(password)) throw PasswordRequirementsNotMetException().also {
            logger.warn("Sign up completion failed: Password validation failed")
        }
        if (password != passwordConfirmation) throw PasswordMismatchException().also { logger.warn("Sign up completion failed: Passwords do not match") }
        if (password.contains(email)) throw PasswordContainsEmailException().also { logger.warn("Sign up completion failed: Password contains email") }
    }

    private fun handleMaxAttempts(signUpEntity: SignUpEntity)
    {
        val maxAttempts = configurationService.getMaxSignUpCompletionOtpAttempts()

        if (signUpEntity.attempts >= maxAttempts)
        {
            val now = LocalDateTime.now()
            val minutesTillNextAttempt = Duration.between(now, signUpEntity.expiresAt).toMinutes()

            signUpEntity.status = SignUpStatus.EXPIRED
            signUpRepository.update(signUpEntity)

            if (minutesTillNextAttempt > 0)
            {
                throw MaxAttemptsOTPExceededException("Maximum attempts exceeded. Please wait $minutesTillNextAttempt minutes before trying again.").also {
                    logger.warn("Sign up completion failed. Max attempts reached. Next attempt allowed in $minutesTillNextAttempt minutes.")
                }
            }
            else
            {
                signUpEntity.status = SignUpStatus.PENDING
                signUpEntity.attempts = 0
                signUpRepository.update(signUpEntity)
                logger.info("Status reset to PENDING as expiration has passed.")
            }
        }
    }

    private fun ensureOtpValidity(signUpEntity: SignUpEntity, otp: String?)
    {
        if (signUpEntity.expiresAt.isBefore(LocalDateTime.now()))
        {
            throw OTPExpiredException(otp!!).also {
                logger.warn("Sign up completion failed. OTP $otp expired")
            }
        }

        if (!BCrypt.checkpw(otp, signUpEntity.otp))
        {
            signUpEntity.attempts++
            signUpRepository.update(signUpEntity)

            throw InvalidOtpException().also {
                logger.warn("Sign up completion failed. OTP $otp invalid")
            }
        }
    }

    private fun finalizeSignUp(signUpEntity: SignUpEntity, email: String, password: String): AppUser
    {
        signUpEntity.status = SignUpStatus.VERIFIED
        signUpRepository.update(signUpEntity)

        val passwordSalt = authenticationService.generatePasswordSalt()
        val hashedPassword = authenticationService.hashPassword(password, passwordSalt)

        return AppUser().apply {
            this.email = email
            this.passwordSalt = passwordSalt
            this.password = hashedPassword
            this.verificationCompleted = true
            this.isActive = true
        }.also {
            logger.info("Successfully signed up")
            appUserRepository.save(it)
        }
    }
}