package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.InactiveAccountException
import com.docuhyphen.app.api.exception.InvalidOtpException
import com.docuhyphen.app.api.exception.PasswordChangeRequiredException
import com.docuhyphen.app.api.exception.InvalidSignInCredentialsException
import com.docuhyphen.app.api.exception.MaxAttemptsOTPExceededException
import com.docuhyphen.app.api.exception.OTPExpiredException
import com.docuhyphen.app.api.exception.SignUpRequiredException
import com.docuhyphen.app.api.exception.TemporaryPasswordExpiredException
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
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class SignInService @Inject constructor(
    private val authenticationService: AuthenticationService,
    private val tokenIssuanceService: TokenIssuanceService,
    private val mfaService: MfaService,
    private val appUserService: AppUserService,
    private val otpService: OtpService,
    private val configurationService: ConfigurationService,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val authenticatorMfaService: AuthenticatorMfaService,
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

        organizationIdentityPolicyService.assertProviderAllowedForEmail(
            sanitizedEmail,
            com.docuhyphen.app.api.model.entity.IdentityProviderType.INTERNAL,
        )

        val appUser = appUserService.findByEmail(sanitizedEmail) ?: throw InvalidSignInCredentialsException()

        // Temporary placeholder accounts (created when an Exchange recipient has no existing account)
        // must complete sign-up before signing in. This check runs before the isActive guard so that
        // any temporary user — regardless of their isActive state — receives the correct prompt.
        if (appUser.isTemporary && appUser.deprovisionedAt == null)
        {
            logger.warn("Sign in blocked: temporary account requires sign-up for {}", sanitizedEmail.maskEmailForLogs())
            throw SignUpRequiredException()
        }

        if (!appUser.isActive || appUser.deprovisionedAt != null)
        {
            logger.warn("Sign in blocked: inactive/deprovisioned account for {}", sanitizedEmail.maskEmailForLogs())
            throw InactiveAccountException()
        }

        // An account provisioned through an external identity provider has no password. Treating
        // that as a plain credential failure keeps the response identical to an unknown email, so
        // the endpoint cannot be used to discover which addresses exist as external-only accounts.
        val storedPasswordHash = appUser.password
        if (storedPasswordHash.isNullOrBlank() || !authenticationService.validatePassword(password, storedPasswordHash))
        {
            logger.warn("Sign in failed: Invalid password for {}", sanitizedEmail.maskEmailForLogs())
            throw InvalidSignInCredentialsException()
        }

        if (appUser.isPasswordTemporary)
        {
            val tempExpiry = appUser.temporaryPasswordExpiresAt
            if (tempExpiry == null || tempExpiry.before(Timestamp.from(Instant.now())))
            {
                logger.warn("Sign in blocked: temporary password expired for {}", sanitizedEmail.maskEmailForLogs())
                throw TemporaryPasswordExpiredException()
            }

            logger.info("Sign in requires password change for {}", sanitizedEmail.maskEmailForLogs())
            throw PasswordChangeRequiredException()
        }

        val mfaSession = mfaService.createMfaSession(
            user = appUser,
            mfaType = appUser.mfaType,
            ipAddress = ipAddress,
        )

        when (appUser.mfaType)
        {
            EMAIL -> mfaService.doEmailMFA(appUser, mfaSession.mfaToken!!)
            MultifactorAuthenticationType.GOOGLE_AUTHENTICATOR,
            MultifactorAuthenticationType.MICROSOFT_AUTHENTICATOR -> Unit
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

    @Transactional(dontRollbackOn = [InvalidOtpException::class, MaxAttemptsOTPExceededException::class])
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

        if (mfaRecord.mfaType == EMAIL && mfaRecord.expiryDateTime?.before(Timestamp.from(Instant.now())) == true)
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
                    mfaService.updateRecord(mfaRecord)
                    logger.warn("Sign in completion failed: Invalid OTP for {}", sanitizedEmail.maskEmailForLogs())
                    throw InvalidOtpException("Invalid verification code")
                }
            }

            MultifactorAuthenticationType.GOOGLE_AUTHENTICATOR,
            MultifactorAuthenticationType.MICROSOFT_AUTHENTICATOR ->
            {
                if (mfaRecord.status == MultifactorAuthenticationStatus.COMPLETED)
                {
                    throw InvalidOtpException("Invalid verification code")
                }
                if (mfaRecord.status == MultifactorAuthenticationStatus.LOCKED)
                {
                    throw MaxAttemptsOTPExceededException("Too many invalid attempts.")
                }
                if (!authenticatorMfaService.verifyUserCode(mfaRecord.appUser!!, sanitizedOTP))
                {
                    mfaService.updateRecord(mfaRecord)
                    logger.warn("Sign in completion failed: Invalid authenticator code for {}", sanitizedEmail.maskEmailForLogs())
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

        val signedInUser = mfaRecord.appUser!!

        // Re-check eligibility at the moment tokens are minted. An administrator may have
        // deactivated or deprovisioned the account between the password step and this one, and
        // that decision must take effect immediately rather than at the next request.
        if (signedInUser.isTemporary && signedInUser.deprovisionedAt == null)
        {
            logger.warn("Sign in completion blocked: temporary account for {}", sanitizedEmail.maskEmailForLogs())
            throw SignUpRequiredException()
        }

        if (!signedInUser.isActive || signedInUser.deprovisionedAt != null)
        {
            logger.warn("Sign in completion blocked: inactive account for {}", sanitizedEmail.maskEmailForLogs())
            throw InactiveAccountException()
        }

        // Issue token triple via shared service
        val tokenTriple = tokenIssuanceService.issueTokenTriple(signedInUser, userAgent, ipAddress)

        mfaService.removeMfaRecord(mfaRecord)

        if (signedInUser.settings?.notifyLogin == true)
        {
            try
            {
                val whenIso = Instant.now().toString()
                val deviceLine = userAgent?.takeIf { it.isNotBlank() } ?: "Unknown device"
                val ipLine = ipAddress?.takeIf { it.isNotBlank() } ?: "Unknown IP"
                val rendered = emailTemplateService.renderNewSignInAlertEmail(
                    firstName = signedInUser.person?.firstName,
                    signInAtIso = whenIso,
                    device = deviceLine,
                    ipAddress = ipLine,
                )
                emailService.sendEmail(
                    signedInUser.email,
                    rendered.subject,
                    rendered.body,
                    useHtml = true,
                )
            }
            catch (e: Exception)
            {
                logger.warn("Failed to send sign-in notification to {}", sanitizedEmail.maskEmailForLogs(), e)
            }
        }

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
                    subject = "Sign In Verification",
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
            this.mfaType = mfaRecord.mfaType?.name
        }
    }

    @Transactional
    fun createEmailFallbackChallenge(email: String?, sessionId: String?): MfaSessionDto
    {
        if (email.isNullOrBlank() || sessionId.isNullOrBlank())
        {
            throw InvalidSignInCredentialsException()
        }
        val sanitizedEmail = email.normalizeEmailOrNull() ?: throw InvalidSignInCredentialsException()
        val mfaRecord = mfaService.getMfaRecordByEmailAndSessionId(sanitizedEmail, sessionId)
            ?: throw InvalidSignInCredentialsException()
        val appUser = mfaRecord.appUser ?: throw InvalidSignInCredentialsException()
        if (!mfaRecord.mfaType!!.isAuthenticator() || !appUser.emailMfaFallbackEnabled)
        {
            throw InvalidSignInCredentialsException()
        }
        if (mfaRecord.status != MultifactorAuthenticationStatus.PENDING)
        {
            throw InvalidSignInCredentialsException()
        }

        mfaService.enforceRateLimits(sanitizedEmail, mfaRecord.ipAddress ?: "0.0.0.0")
        mfaRecord.mfaType = EMAIL
        val otp = mfaService.regenerateOtp(mfaRecord)
        mfaService.doEmailMFA(appUser, otp)
        return MfaSessionDto().apply {
            id = UUID.fromString(mfaRecord.sessionId)
            mfaType = EMAIL.name
            emailFallbackEnabled = true
        }
    }

}
