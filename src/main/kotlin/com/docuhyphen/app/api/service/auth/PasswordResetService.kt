package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.model.entity.MfaRecord
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationStatus
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.MfaService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

@RequestScoped
class PasswordResetService @Inject constructor(
    private val appUserRepository: AppUserRepository,
    private val mfaService: MfaService,
    private val otpService: OtpService,
    private val authenticationService: AuthenticationService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
    private val signOutService: SignOutService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PasswordResetService::class.java)
        private val UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
    }

    fun initiatePasswordReset(email: String?)
    {
        validateEmail(email)

        val appUser = appUserRepository.findByEmail(email!!)
            ?: throw EmailNotFoundException().also {
                logger.warn("Password reset request failed. User not found for email: $email")
            }

        if (!appUser.isActive || appUser.deprovisionedAt != null)
        {
            // Don't reveal account state on the public initiate endpoint — surface the
            // same neutral outcome the resource layer maps EmailNotFoundException to.
            throw EmailNotFoundException().also {
                logger.warn("Password reset blocked: inactive/deprovisioned account for email: $email")
            }
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

        val body = emailTemplateService.renderPasswordResetRequestEmail(
            verificationCode = otp,
            expiryMinutes = expiryMinutes,
        )
        emailService.sendEmail(
            to = email,
            subject = "${configurationService.emailSubjectTitle} | Account recovery",
            body = body,
            useHtml = true,
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
            throw OTPExpiredException("Your verification code has expired.").also {
                logger.warn("Password reset failed. OTP expired for email: $email")
            }
        }

        val appUser = appUserRepository.findByEmail(email!!)
            ?: throw EmailNotFoundException().also {
                logger.error("Password reset failed. App user not found for email: $email after OTP verification")
            }

        if (!appUser.isActive || appUser.deprovisionedAt != null)
        {
            throw EmailNotFoundException().also {
                logger.warn("Password reset completion blocked: inactive/deprovisioned account for email: $email")
            }
        }

        val passwordSalt = authenticationService.generatePasswordSalt()
        val hashedPassword = authenticationService.hashPassword(newPassword!!, passwordSalt)

        appUser.apply {
            this.password = hashedPassword
            this.passwordSalt = Base64.getEncoder().encodeToString(passwordSalt.toByteArray())
        }

        appUserRepository.update(appUser)
        mfaService.removeMfaRecord(mfaRecord)

        try
        {
            val body = emailTemplateService.renderPasswordChangedEmail(
                email = appUser.email,
                changedAt = UTC_FORMATTER.format(Instant.now()),
            )
            emailService.sendEmail(
                to = appUser.email,
                subject = "${configurationService.emailSubjectTitle} | Password changed",
                body = body,
                useHtml = true,
            )
        }
        catch (e: Exception)
        {
            logger.error("Failed to send password-changed confirmation to {}", appUser.email, e)
        }

        // Revoke every active session + refresh token for this user. Any other browser that
        // was already signed in receives SESSION_REVOKED over the realtime channel and is
        // logged out immediately; subsequent API calls also fail at the auth filter.
        runCatching {
            signOutService.signOutByUserId(appUser.id, RevocationReasonCode.PASSWORD_CHANGED)
        }.onFailure { e ->
            logger.error("Password reset succeeded but session revocation failed for user={}", appUser.id, e)
        }
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