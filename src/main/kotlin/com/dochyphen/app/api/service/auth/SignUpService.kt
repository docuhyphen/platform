package com.dochyphen.app.api.service.auth

import com.dochyphen.app.api.exception.*
import com.dochyphen.app.api.model.entity.AppUser
import com.dochyphen.app.api.model.entity.SignUpEntity
import SignUpStatus
import com.dochyphen.app.api.repository.AppUserRepository
import com.dochyphen.app.api.repository.SignUpRepository
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.communication.OtpService
import com.dochyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime

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

        val sanitized = email.trim().lowercase();

        if (authenticationService.isEmailInvalid(sanitized))
        {
            logger.warn("Sign up failed: Email ($sanitized) validation failed")
            throw InvalidEmailException()
        }

        try
        {
            appUserRepository.findByEmail(sanitized)?.let {
                throw AppUserExistsException()
            }

            val existingSignUp = signUpRepository.findByEmail(sanitized)

            val otp = otpService.generateEmailOtp()
            val expirationMinutes = configurationService.getSignUpOtpExpiryMins()
            val appBaseUrl = configurationService.getAppBaseURL()
            val emailConfirmationLink = "$appBaseUrl/sign-up/email-confirm?email=$sanitized&otp=$otp"

            if (existingSignUp != null)
            {
                if (existingSignUp.otpExpiryTimestamp.isAfter(LocalDateTime.now()))
                {
                    throw ExistingSignUpException()
                }

                existingSignUp.apply {
                    this.otp = otpService.hashOtp(otp)

                    this.otpExpiryTimestamp = LocalDateTime.now().plusMinutes(expirationMinutes.toLong())
                }
                signUpRepository.update(existingSignUp)
            }
            else
            {
                val signUpEntity = SignUpEntity().apply {
                    this.email = sanitized
                    this.otp = otpService.hashOtp(otp)
                    this.otpExpiryTimestamp = LocalDateTime.now().plusMinutes(expirationMinutes.toLong())
                }
                signUpRepository.save(signUpEntity)
            }

            emailService.sendEmail(
                sanitized, "${configurationService.getAppEmailSubjectTitle()} | Sign Up", """
            Thank you for signing up with Secure Document Share.
            Here's the OTP you'll need to continue: $otp
            Alternatively, you can click on this link: $emailConfirmationLink
            
            NOTE: The OTP expires in $expirationMinutes minutes.
        """.trimIndent()
            )

            logger.info("Sign up successful. Email: $sanitized, otp: $otp")
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

        // Check if the account is OTP_LOCKED
        if (signUpEntity.status == SignUpStatus.OTP_LOCKED)
        {
            val lockCooldownMinutes = 15L // Longer cooldown for locked status
            val lockEndTime = signUpEntity.lastRegenerationAttemptTime?.plusMinutes(lockCooldownMinutes)
                ?: LocalDateTime.now()

            if (LocalDateTime.now().isBefore(lockEndTime))
            {
                val minutesRemaining = Duration.between(LocalDateTime.now(), lockEndTime).toMinutes() + 1
                logger.warn("Sign up OTP regeneration failed: Account is locked. Minutes remaining: $minutesRemaining")
                throw OtpMaxRetryLimitReachedException(
                    "Account is temporarily locked. Please wait $minutesRemaining minutes before requesting a new OTP."
                )
            }
            else
            {
                // Reset lock status after cooldown period
                signUpEntity.status = SignUpStatus.PENDING
                signUpEntity.otpRegenerationAttempts = 0
            }
        }

        // Calculate when the last OTP was generated based on expiry timestamp
        val otpExpiryMinutes = configurationService.getSignUpOtpExpiryMins().toLong()
        val lastOtpGeneratedTime = signUpEntity.otpExpiryTimestamp.minusMinutes(otpExpiryMinutes)
        val regenerationCooldownMinutes = 3L
        val cooldownEndTime = lastOtpGeneratedTime.plusMinutes(regenerationCooldownMinutes)

        // Check if we're still in the cooldown period
        if (LocalDateTime.now().isBefore(cooldownEndTime))
        {
            val minutesRemaining = Duration.between(LocalDateTime.now(), cooldownEndTime).toMinutes() + 1

            // Increment regeneration attempts and check if maximum is reached
            signUpEntity.otpRegenerationAttempts++
            signUpEntity.lastRegenerationAttemptTime = LocalDateTime.now()

            val maxRegenerationAttempts = 3 // Maximum attempts before locking

            if (signUpEntity.otpRegenerationAttempts >= maxRegenerationAttempts)
            {
                signUpEntity.status = SignUpStatus.OTP_LOCKED
                signUpRepository.update(signUpEntity)

                logger.warn("Sign up OTP regeneration failed: Account locked due to multiple rapid attempts")
                throw OtpMaxRetryLimitReachedException(
                    "Account temporarily locked due to multiple attempts. Please wait 15 minutes before trying again."
                )
            }

            signUpRepository.update(signUpEntity)
            logger.warn("Sign up OTP regeneration failed: Cooldown period active. Minutes remaining: $minutesRemaining")
            throw OtpRegenerationCooldownException("Please wait $minutesRemaining minutes before requesting a new OTP.")
        }

        val newOtp = otpService.generateEmailOtp()
        val expirationTime = LocalDateTime.now().plusMinutes(otpExpiryMinutes)

        signUpEntity.otp = otpService.hashOtp(newOtp)
        signUpEntity.otpExpiryTimestamp = expirationTime
        signUpEntity.otpAttempts = 0
        signUpEntity.otpRegenerationAttempts = 0
        signUpEntity.status = SignUpStatus.PENDING

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

        handleAttempts(signUpEntity)

        ensureOtpValidity(signUpEntity, otp)

        return finalizeSignUp(signUpEntity, email, password!!)
    }

    private fun validateInputs(email: String?, otp: String?, password: String?, passwordConfirmation: String?)
    {
        if (email.isNullOrBlank())
        {
            throw EmailRequiredException().also {
                logger.warn("Sign up completion failed: Email is null or blank")
            }
        }

        appUserRepository.findByEmail(email)?.let {
            throw AppUserExistsException()
        }

        if (authenticationService.isEmailInvalid(email))
        {
            throw InvalidEmailException().also { logger.warn("Sign up completion failed: Email validation failed") }
        }

        if (otp.isNullOrBlank())
        {
            throw OtpRequiredException().also { logger.warn("Sign up completion failed: OTP is null or blank") }
        }

        if (password.isNullOrBlank())
        {
            throw PasswordRequiredException().also { logger.warn("Sign up completion failed: Password is null or blank") }
        }

        if (passwordConfirmation.isNullOrBlank())
        {
            throw ConfirmationPasswordRequiredException().also { logger.warn("Sign up completion failed: Confirmation password is null or blank") }
        }

        if (!authenticationService.isPasswordStrong(password))
        {
            throw PasswordRequirementsNotMetException().also {
                logger.warn("Sign up completion failed: Password validation failed")
            }
        }

        if (password != passwordConfirmation)
        {
            throw PasswordMismatchException().also { logger.warn("Sign up completion failed: Passwords do not match") }
        }

        if (password.contains(email))
        {
            throw PasswordContainsEmailException().also { logger.warn("Sign up completion failed: Password contains email") }
        }
    }

    private fun handleAttempts(signUpEntity: SignUpEntity)
    {
        // First check if the account is locked
        if (signUpEntity.status == SignUpStatus.OTP_LOCKED)
        {
            val lockCooldownMinutes = 10L
            val lockEndTime = signUpEntity.lastRegenerationAttemptTime?.plusMinutes(lockCooldownMinutes)
                ?: LocalDateTime.now()

            if (LocalDateTime.now().isBefore(lockEndTime))
            {
                val minutesRemaining = Duration.between(LocalDateTime.now(), lockEndTime).toMinutes() + 1
                logger.warn("Sign up completion failed: Account is locked. Minutes remaining: $minutesRemaining")
                throw OtpMaxRetryLimitReachedException(
                    "Account is temporarily locked. Please wait $minutesRemaining minutes before attempting again."
                )
            }
            else
            {
                // Reset lock status after cooldown period
                signUpEntity.status = SignUpStatus.PENDING
                signUpEntity.otpRegenerationAttempts = 0
            }
        }

        val now = LocalDateTime.now()
        val maxAttempts = configurationService.getMaxSignUpCompletionOtpAttempts()
        val cooldownMinutes = 3L

        // Increment attempts counter
        signUpEntity.otpAttempts++

        // If attempts are under the limit, allow to continue
        if (signUpEntity.otpAttempts <= maxAttempts)
        {
            signUpRepository.update(signUpEntity)
            return
        }

        // Calculate remaining cooldown time
        val cooldownEndTime = signUpEntity.otpExpiryTimestamp.plusMinutes(cooldownMinutes)
        val minutesRemaining = Duration.between(now, cooldownEndTime).toMinutes() + 1

        logger.warn("Sign up completion failed. Max attempts ($maxAttempts) reached.")

        if (minutesRemaining <= 0) {
            logger.warn("Sign up completion failed. Max attempts reached. Cooldown period expired.")
            throw OTPExpiredException("Your OTP has expired, please request a new one.")
        }

        // Max attempts reached - mark as expired_max_retries
        signUpEntity.status = SignUpStatus.EXPIRED_MAX_RETRIES
        signUpRepository.update(signUpEntity)

        throw MaxAttemptsOTPExceededException(
            "Maximum verification attempts reached. Please try again in $minutesRemaining minutes."
        )
    }

    private fun ensureOtpValidity(signUpEntity: SignUpEntity, otp: String?)
    {
        val providedOtp = otp ?: throw InvalidOtpException()

        if (signUpEntity.otpExpiryTimestamp.isBefore(LocalDateTime.now()))
        {
            logger.warn("Sign up completion failed. OTP expired.")
            throw OTPExpiredException("Your OTP has expired")
        }

        if (!BCrypt.checkpw(providedOtp, signUpEntity.otp))
        {
            logger.warn("Sign up completion failed. Invalid OTP provided.")
            throw InvalidOtpException()
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
            this.emailVerificationComplete = true
            this.isActive = true
        }.also {
            logger.info("Successfully signed up")
            appUserRepository.save(it)
        }
    }
}