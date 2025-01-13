package com.securedocsshare.app.service

import com.securedocsshare.app.api.model.ConfirmationPasswordRequiredException
import com.securedocsshare.app.api.model.EmailNotFoundException
import com.securedocsshare.app.api.model.EmailRequiredException
import com.securedocsshare.app.api.model.IncorrectSignUpCompletionStatusException
import com.securedocsshare.app.api.model.InvalidEmailException
import com.securedocsshare.app.api.model.InvalidOtpException
import com.securedocsshare.app.api.model.InvalidSignUpStatusException
import com.securedocsshare.app.api.model.MaxAttemptsOTPExceededException
import com.securedocsshare.app.api.model.OTPExpiredException
import com.securedocsshare.app.api.model.PasswordMismatchException
import com.securedocsshare.app.api.model.PasswordRequiredException
import com.securedocsshare.app.api.model.PasswordRequirementsNotMetException
import com.securedocsshare.app.api.model.AppUser
import com.securedocsshare.app.api.model.AppUserExistsException
import com.securedocsshare.app.api.model.ExistingSignUpException
import com.securedocsshare.app.api.model.OtpRequiredException
import com.securedocsshare.app.api.model.PasswordContainsEmailException
import com.securedocsshare.app.api.model.SignUpEntity
import com.securedocsshare.app.api.model.SignUpStatus.EXPIRED
import com.securedocsshare.app.api.model.SignUpStatus.PENDING
import com.securedocsshare.app.api.model.SignUpStatus.VERIFIED
import com.securedocsshare.app.repository.AppUserRepository
import com.securedocsshare.app.repository.SignUpRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
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
                    this.otp = otp
                    this.expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes.toLong())
                }
                signUpRepository.update(existingSignUp)
            }
            else
            {
                val signUpEntity = SignUpEntity().apply {
                    this.email = email
                    this.otp = otp
                    this.expiresAt = LocalDateTime.now().plusMinutes(expirationMinutes.toLong())
                }
                signUpRepository.save(signUpEntity)
            }

            emailService.sendEmail(
                email,
                "${configurationService.getAppEmailSubjectTitle()} | Sign Up",
                """
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
        //ToDo: Implement rate limiter for specific email

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

        if (signUpEntity.status != PENDING)
        {
            logger.warn("Sign up OTP regeneration failed: Entity (${signUpEntity.status}) is not Pending")
            throw InvalidSignUpStatusException("Cannot regenerate OTP for a non-pending sign-up.")
        }

        val newOtp = otpService.generateEmailOtp()
        val configExpiryMinutes = configurationService.getSignUpOtpExpiryMins()
        val expirationTime = LocalDateTime.now().plusMinutes(configExpiryMinutes)

        signUpEntity.otp = newOtp
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

        val signUpEntity = signUpRepository.findByEmail(email!!)
            ?: throw EmailNotFoundException().also {
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

            signUpEntity.status = EXPIRED
            signUpRepository.update(signUpEntity)

            if (minutesTillNextAttempt > 0)
            {
                throw MaxAttemptsOTPExceededException("Maximum attempts exceeded. Please wait $minutesTillNextAttempt minutes before trying again.").also {
                    logger.warn("Sign up completion failed. Max attempts reached. Next attempt allowed in $minutesTillNextAttempt minutes.")
                }
            }
            else
            {
                signUpEntity.status = PENDING
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

        if (signUpEntity.otp != otp)
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
        signUpEntity.status = VERIFIED
        signUpRepository.update(signUpEntity)

        val passwordSalt = authenticationService.generatePasswordSalt()
        val hashedPassword = authenticationService.hashPassword(password, passwordSalt)

        return AppUser().apply {
            this.email = email
            this.passwordSalt = Base64.getEncoder().encodeToString(passwordSalt)
            this.password = hashedPassword
            this.verificationCompleted = true
            this.isActive = true
        }.also {
            logger.info("Successfully signed up")
            appUserRepository.save(it)
        }
    }


    fun completeSignUp2(email: String?, otp: String?, password: String?, passwordConfirmation: String?): AppUser
    {
        if (email.isNullOrBlank())
        {
            logger.warn("Sign up completion  failed: Email is null or blank")
            throw EmailRequiredException()
        }

        if (authenticationService.isEmailInvalid(email))
        {
            logger.warn("Sign up completion failed: Email validation failed")
            throw InvalidEmailException()
        }

        if (otp.isNullOrBlank())
        {
            logger.warn("Sign up completion failed: OTP is null or blank")
            throw OtpRequiredException()
        }

        if (password.isNullOrBlank())
        {
            logger.warn("Sign up completion failed: Password is null or blank")
            throw PasswordRequiredException()
        }

        if (passwordConfirmation.isNullOrBlank())
        {
            logger.warn("Sign up completion failed: Confirmation password is null or blank")
            throw ConfirmationPasswordRequiredException()
        }

        if (authenticationService.isPasswordStrong(password))
        {
            logger.warn("Sign up completion failed: Password validation failed")
            throw PasswordRequirementsNotMetException()
        }

        if (password != passwordConfirmation)
        {
            logger.warn("Sign up completion failed: Passwords do not match")
            throw PasswordMismatchException()
        }

        if (password.contains(email))
        {
            logger.warn("Sign up completion failed: Passwords contains email")
            throw PasswordContainsEmailException()
        }

        val signUpEntity = signUpRepository.findByEmail(email)

        if (signUpEntity == null)
        {
            logger.warn("Sign up completion failed: Entity not found with email ($email)")
            throw EmailNotFoundException()
        }

        val maxAttempts = configurationService.getMaxSignUpCompletionOtpAttempts()

        if (signUpEntity.attempts >= maxAttempts)
        {
            val now = LocalDateTime.now()
            val minutesTillNextAttempt = Duration.between(now, signUpEntity.expiresAt).toMinutes()

            signUpEntity.status = EXPIRED
            signUpRepository.update(signUpEntity)

            if (minutesTillNextAttempt > 0)
            {
                logger.warn("Sign up completion failed. Max attempts reached. Next attempt allowed in $minutesTillNextAttempt minutes.")
                throw MaxAttemptsOTPExceededException("Maximum attempts exceeded. Please wait $minutesTillNextAttempt minutes before trying again.")
            }
            else
            {
                logger.warn("Sign up completion failed. Max attempts reached, but OTP has already expired.")
                throw MaxAttemptsOTPExceededException("Maximum attempts exceeded. The OTP has already expired, please initiate a new sign-up.")
            }
        }

        if (signUpEntity.status != PENDING)
        {

            logger.warn("Sign up completion failed. Status is not pending")
            throw IncorrectSignUpCompletionStatusException(signUpEntity.status)
        }

        if (signUpEntity.expiresAt.isBefore(LocalDateTime.now()))
        {
            logger.warn("Sign up completion failed. OTP $otp expired")
            throw OTPExpiredException(otp)
        }

        if (signUpEntity.otp != otp)
        {
            signUpEntity.attempts++
            signUpRepository.update(signUpEntity)

            logger.warn("Sign up completion failed. OTP $otp invalid")
            throw InvalidOtpException()
        }

        signUpEntity.status = VERIFIED
        signUpRepository.update(signUpEntity)

        val passwordSalt = authenticationService.generatePasswordSalt()
        val hashedPassword = authenticationService.hashPassword(password, passwordSalt)

        return AppUser().apply {
            this.email = email
            this.passwordSalt = Base64.getEncoder().encodeToString(passwordSalt)
            this.password = hashedPassword
            this.verificationCompleted = true
            this.isActive = true
        }.also {
            logger.info("Successfully signed up")
            appUserRepository.save(it)
        }
    }
}