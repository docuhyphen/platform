package com.dochyphen.app.api.service

import com.dochyphen.app.api.exception.ConfirmationPasswordRequiredException
import com.dochyphen.app.api.exception.EmailNotFoundException
import com.dochyphen.app.api.exception.EmailRequiredException
import com.dochyphen.app.api.exception.InvalidEmailException
import com.dochyphen.app.api.exception.InvalidOtpException
import com.dochyphen.app.api.exception.OTPExpiredException
import com.dochyphen.app.api.exception.OtpRequiredException
import com.dochyphen.app.api.exception.PasswordMismatchException
import com.dochyphen.app.api.exception.PasswordRequiredException
import com.dochyphen.app.api.exception.PasswordRequirementsNotMetException
import com.dochyphen.app.api.model.*
import com.dochyphen.app.api.model.entity.MfaRecord
import com.dochyphen.app.api.model.entity.MultifactorAuthenticationStatus
import com.dochyphen.app.api.model.entity.MultifactorAuthenticationType
import com.dochyphen.app.api.repository.AppUserRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*
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
        validateEmail(email)

        val appUser = appUserRepository.findByEmail(email!!)
            ?: throw EmailNotFoundException().also {
                logger.warn("Password reset request failed. User not found for email: $email")
            }

        val otp = otpService.generateEmailOtp()
        val expiryMinutes = configurationService.getPasswordResetOtpExpiryMins()
        val expiryDate = Timestamp.from(Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(expiryMinutes)))

        val mfaRecord = MfaRecord().apply {
            this.id = UUID.randomUUID()
            this.appUser = appUser
            this.mfaType = MultifactorAuthenticationType.PASSWORD_RESET
            this.mfaToken = otp
            this.status = MultifactorAuthenticationStatus.PENDING
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
        validateEmail(email)
        validateOtp(otp)
        validatePasswords(newPassword, confirmPassword)

        val mfaRecord = mfaService.getMfaRecordByTokenAndType(otp!!, MultifactorAuthenticationType.PASSWORD_RESET)
            ?: throw InvalidOtpException().also {
                logger.warn("Password reset failed. Invalid OTP: $otp")
            }

        if (mfaRecord.expiryDateTime?.before(Timestamp.from(Instant.now())) == true)
        {
            throw OTPExpiredException("The OTP has expired.").also {
                logger.warn("Password reset failed. OTP expired for email: $email")
            }
        }

        val appUser = appUserRepository.findByEmail(email!!)
            ?: throw EmailNotFoundException().also {
                logger.error("Password reset failed. App user not found for email: $email after OTP verification")
            }

        val passwordSalt = authenticationService.generatePasswordSalt()
        val hashedPassword = authenticationService.hashPassword(newPassword!!, passwordSalt)

        appUser.apply {
            this.password = hashedPassword
            this.passwordSalt = Base64.getEncoder().encodeToString(passwordSalt.toByteArray())
        }

        appUserRepository.update(appUser)
        mfaService.removeMfaRecord(mfaRecord)
        logger.info("Password reset successfully for email: $email")
    }

    private fun validateEmail(email: String?)
    {
        if (email.isNullOrBlank())
        {
            throw EmailRequiredException().also {
                logger.warn("Password reset request failed. Email is null or blank.")
            }
        }

        if (authenticationService.isEmailInvalid(email))
        {
            throw InvalidEmailException().also {
                logger.warn("Password reset request failed. Email format is invalid: $email")
            }
        }
    }

    private fun validateOtp(otp: String?)
    {
        if (otp.isNullOrBlank())
        {
            throw OtpRequiredException().also {
                logger.warn("Password reset failed. OTP is null or blank.")
            }
        }
    }

    private fun validatePasswords(newPassword: String?, confirmPassword: String?)
    {
        if (newPassword.isNullOrBlank())
        {
            throw PasswordRequiredException().also {
                logger.warn("Password reset failed. New password is null or blank.")
            }
        }

        if (confirmPassword.isNullOrBlank())
        {
            throw ConfirmationPasswordRequiredException().also {
                logger.warn("Password reset failed. Confirmation password is null or blank.")
            }
        }

        if (newPassword != confirmPassword)
        {
            throw PasswordMismatchException().also {
                logger.warn("Password reset failed. New password and confirmation password do not match.")
            }
        }

        if (!authenticationService.isPasswordStrong(newPassword))
        {
            throw PasswordRequirementsNotMetException().also {
                logger.warn("Password reset failed. New password does not meet strength requirements.")
            }
        }
    }
}