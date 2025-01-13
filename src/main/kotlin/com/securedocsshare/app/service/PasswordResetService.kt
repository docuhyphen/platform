package com.securedocsshare.app.service

import com.securedocsshare.app.api.model.ConfirmationPasswordRequiredException
import com.securedocsshare.app.api.model.EmailNotFoundException
import com.securedocsshare.app.api.model.EmailRequiredException
import com.securedocsshare.app.api.model.InvalidEmailException
import com.securedocsshare.app.api.model.InvalidOtpException
import com.securedocsshare.app.api.model.OTPExpiredException
import com.securedocsshare.app.api.model.OtpRequiredException
import com.securedocsshare.app.api.model.PasswordMismatchException
import com.securedocsshare.app.api.model.PasswordRequiredException
import com.securedocsshare.app.api.model.PasswordRequirementsNotMetException
import com.securedocsshare.app.api.model.MultifactorAuthenticationType
import com.securedocsshare.app.api.model.MfaRecord
import com.securedocsshare.app.repository.AppUserRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

@RequestScoped
class PasswordResetService @Inject constructor(
    private val appUserRepository: AppUserRepository,
    private val mfaService: MfaService,
    private val otpService: OtpService,
    private val authenticationService: AuthenticationService,
    private val emailService: EmailService,
    private val configurationService: ConfigurationService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PasswordResetService::class.java)
    }

    fun initiatePasswordReset(email: String?)
    {

        if (email.isNullOrBlank())
        {
            logger.warn("Password reset request failed. Email is null or blank.")
            throw EmailRequiredException()
        }

        if (authenticationService.isEmailInvalid(email))
        {
            logger.warn("Password reset request failed. Email format is invalid: $email")
            throw InvalidEmailException()
        }

        val appUser = appUserRepository.findByEmail(email)
            ?: run {
                logger.warn("Password reset request failed. User not found for email: $email")
                throw EmailNotFoundException()
            }

        val otp = otpService.generateEmailOtp()
        val expiryMinutes = configurationService.getPasswordResetOtpExpiryMins()
        val expiryDate = Timestamp.from(Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(expiryMinutes)))

        val mfaRecord = MfaRecord().apply {
            this.id = UUID.randomUUID()
            this.appUser = appUser
            this.mfaType = MultifactorAuthenticationType.EMAIL
            this.mfaToken = otp
            this.createdDate = Timestamp.from(Instant.now())
            this.expiryDateTime = expiryDate
        }

        mfaService.saveMfaRecord(mfaRecord)

        emailService.sendEmail(
            email,
            "${configurationService.getAppEmailSubjectTitle()} | Password reset",
            """
                You have requested that your password be reset. 
                To continue, you will need this OTP $otp
            """.trimIndent()
        )

        logger.info("Password reset OTP sent to email: $email")
    }

    fun completePasswordReset(email: String?, otp: String?, newPassword: String?, confirmPassword: String?)
    {

        if (email.isNullOrBlank())
        {
            logger.warn("Password reset failed. Email is null or blank.")
            throw EmailRequiredException()
        }

        if (!authenticationService.isEmailInvalid(email))
        {
            logger.warn("Password reset failed. Email format is invalid: $email")
            throw InvalidEmailException()
        }

        if (otp.isNullOrBlank())
        {
            logger.warn("Password reset failed. OTP is null or blank.")
            throw OtpRequiredException()
        }

        if (newPassword.isNullOrBlank())
        {
            logger.warn("Password reset failed. New password is null or blank.")
            throw PasswordRequiredException()
        }

        if (confirmPassword.isNullOrBlank())
        {
            logger.warn("Password reset failed. Confirmation password is null or blank.")
            throw ConfirmationPasswordRequiredException()
        }

        if (newPassword != confirmPassword)
        {
            logger.warn("Password reset failed. New password and confirmation password do not match.")
            throw PasswordMismatchException()
        }

        if (!authenticationService.isPasswordStrong(newPassword))
        {
            logger.warn("Password reset failed. New password does not meet strength requirements.")
            throw PasswordRequirementsNotMetException()
        }

        val mfaRecord = mfaService.getMfaRecordByEmailAndOtp(email, otp)
            ?: run {
                logger.warn("Password reset failed. Invalid OTP: $otp")
                throw InvalidOtpException()
            }

        if (mfaRecord.expiryDateTime?.before(Timestamp.from(Instant.now())) == true)
        {
            logger.warn("Password reset failed. OTP expired for email: $email")
            throw OTPExpiredException("The OTP has expired.")
        }

        val appUser = appUserRepository.findByEmail(email)
            ?: run {
                logger.error("Password reset failed. App user not found for email: $email after OTP verification")
                throw EmailNotFoundException()
            }

        val passwordSalt = authenticationService.generatePasswordSalt()
        val hashedPassword = authenticationService.hashPassword(newPassword, passwordSalt)

        appUser.apply {
            this.password = hashedPassword
            this.passwordSalt = Base64.getEncoder().encodeToString(passwordSalt)
        }

        appUserRepository.save(appUser)

        logger.info("Password reset successfully for email: $email")
    }
}
