package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.*
import com.docuhyphen.app.api.extension.maskEmailForLogs
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.auth.SignUpRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.identity.IdentityProviderLinkRepository
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.contactdetails.UserContactService
import com.docuhyphen.app.api.service.notification.AppAdminNotificationService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.LockTimeoutException
import jakarta.persistence.PessimisticLockException
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@ApplicationScoped
class SignUpService @Inject constructor(
    private val signUpRepository: SignUpRepository,
    private val appUserRepository: AppUserRepository,
    private val identityProviderLinkRepository: IdentityProviderLinkRepository,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val otpService: OtpService,
    private val configurationService: ConfigurationService,
    private val authenticationService: AuthenticationService,
    private val signUpEmailConfirmationTokenService: SignUpEmailConfirmationTokenService,
    private val userContactService: UserContactService,
    private val exchangeRepository: ExchangeRepository,
    private val disposableEmailDomainService: DisposableEmailDomainService,
    private val subscriptionPolicyService: SubscriptionPolicyService,
    private val organizationMembershipService: OrganizationMembershipService,
    private val appAdminNotificationService: AppAdminNotificationService,
    private val signUpRequestGuard: SignUpRequestGuard,
    private val signUpCompletionFollowUpService: SignUpCompletionFollowUpService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignUpService::class.java)
    }

    fun initiateSignUp(email: String?, clientIp: String, requestId: String?)
    {
        signUpRequestGuard.enforceInitiationBudget(clientIp, requestId)
        val sanitized = requireValidEmail(email, "Sign up")

        if (disposableEmailDomainService.isDisposable(sanitized))
        {
            logger.warn("Sign up failed: Disposable email domain rejected for {}", sanitized.maskEmailForLogs())
            throw DisposableEmailAddressException()
        }

        signUpRequestGuard.enforceInitiationAddressBudget(clientIp, sanitized, requestId)

        if (appUserRepository.findActiveByEmail(sanitized) != null)
        {
            logger.info("Sign up initiation skipped: an account already exists for {}", sanitized.maskEmailForLogs())
            return
        }

        val existingSignUp = signUpRepository.findByEmail(sanitized)
        if (existingSignUp != null && existingSignUp.otpExpiryTimestamp.isAfter(LocalDateTime.now()))
        {
            logger.info(
                "Sign up initiation skipped: a verification code is still active for {}",
                sanitized.maskEmailForLogs()
            )
            return
        }

        val signUpEntity = existingSignUp ?: SignUpEntity().apply { this.email = sanitized }
        val otp = issueCode(signUpEntity)
        if (existingSignUp == null)
        {
            signUpRepository.save(signUpEntity)
        }
        else
        {
            signUpRepository.update(signUpEntity)
        }

        val expiryMinutes = configurationService.getSignUpOtpExpiryMins()
        val confirmationToken = signUpEmailConfirmationTokenService.issueToken(sanitized, expiryMinutes)
        emailService.sendEmail(
            to = sanitized,
            subject = "Sign Up Email Verification",
            body = emailTemplateService.renderSignUpInitiationEmail(sanitized, otp, confirmationToken, expiryMinutes),
            useHtml = true
        )

        logger.info("Sign up initiation successful for {}", sanitized.maskEmailForLogs())
    }

    fun regenerateOtp(email: String?, clientIp: String, requestId: String?)
    {
        signUpRequestGuard.enforceResendBudget(clientIp, requestId)
        val sanitizedEmail = requireValidEmail(email, "Sign up OTP regeneration")
        signUpRequestGuard.enforceResendAddressBudget(clientIp, sanitizedEmail, requestId)

        if (appUserRepository.findActiveByEmail(sanitizedEmail) != null)
        {
            logger.info(
                "Sign up OTP regeneration skipped: an account already exists for {}",
                sanitizedEmail.maskEmailForLogs()
            )
            return
        }

        val signUpEntity = signUpRepository.findByEmail(sanitizedEmail)
        if (signUpEntity == null)
        {
            logger.info("Sign up OTP regeneration skipped: no sign up record for {}", sanitizedEmail.maskEmailForLogs())
            return
        }

        val newOtp = issueCode(signUpEntity)
        signUpRepository.update(signUpEntity)

        val expiryMinutes = configurationService.getSignUpOtpExpiryMins()
        val confirmationToken = signUpEmailConfirmationTokenService.issueToken(sanitizedEmail, expiryMinutes)
        emailService.sendEmail(
            to = sanitizedEmail,
            subject = "Sign Up verification code",
            body = emailTemplateService.renderSignUpOtpRegenerationEmail(newOtp, confirmationToken, expiryMinutes),
            useHtml = true
        )

        logger.info("Sign up OTP regeneration successful")
    }

    fun peekEmailFromConfirmationToken(token: String?): String?
    {
        if (token.isNullOrBlank()) return null
        return signUpEmailConfirmationTokenService.peekToken(token)
    }

    @Transactional
    fun completeSignUpViaToken(
        token: String?,
        password: String?,
        passwordConfirmation: String?,
        clientIp: String,
        requestId: String?,
    ): AppUser
    {
        signUpRequestGuard.enforceCompletionBudget(clientIp, requestId)

        val email = peekEmailFromConfirmationToken(token)?.normalizeEmailOrNull()
            ?: throw InvalidSignUpConfirmationTokenException().also {
                logger.warn("Sign up token completion failed: token invalid or expired")
            }

        validatePasswordFields(email, password, passwordConfirmation, "Sign up token completion")

        val signUpEntity = signUpRepository.findByEmailForUpdate(email)
        if (signUpEntity == null
            || signUpEntity.status == SignUpStatus.VERIFIED
            || signUpEntity.otpExpiryTimestamp.isBefore(LocalDateTime.now())
        )
        {
            logger.warn("Sign up token completion failed: sign up record is missing, completed, or expired")
            throw InvalidSignUpConfirmationTokenException()
        }

        ensureNoRegisteredAccount(email)

        return finalizeSignUp(signUpEntity, email, password!!, token)
    }

    @Transactional(dontRollbackOn = [SignUpVerificationRejectedException::class])
    fun completeSignUp(
        email: String?,
        otp: String?,
        password: String?,
        passwordConfirmation: String?,
        clientIp: String,
        requestId: String?,
    ): AppUser
    {
        signUpRequestGuard.enforceCompletionBudget(clientIp, requestId)
        val normalizedEmail = validateInputs(email, otp, password, passwordConfirmation)

        val signUpEntity =
            lockSignUpWithoutWaiting(normalizedEmail) ?: throw SignUpVerificationRejectedException().also {
                logger.warn("Sign up completion failed: no sign up record for {}", normalizedEmail.maskEmailForLogs())
            }

        verifyCode(signUpEntity, otp!!)
        ensureNoRegisteredAccount(normalizedEmail)

        return finalizeSignUp(signUpEntity, normalizedEmail, password!!, null)
    }

    private fun issueCode(signUpEntity: SignUpEntity): String
    {
        val otp = otpService.generateEmailOtp()
        signUpEntity.otp = otpService.hashOtp(otp)
        signUpEntity.otpExpiryTimestamp = LocalDateTime.now().plusMinutes(configurationService.getSignUpOtpExpiryMins())
        signUpEntity.otpAttempts = 0
        signUpEntity.status = SignUpStatus.PENDING
        return otp
    }

    private fun lockSignUpWithoutWaiting(email: String): SignUpEntity?
    {
        return try
        {
            signUpRepository.findByEmailForUpdateNoWait(email)
        }
        catch (exception: PessimisticLockException)
        {
            throw signUpVerificationBusy(email)
        }
        catch (exception: LockTimeoutException)
        {
            throw signUpVerificationBusy(email)
        }
    }

    private fun signUpVerificationBusy(email: String): SignUpVerificationBusyException
    {
        logger.warn("Sign up completion rejected: another completion is in progress for {}", email.maskEmailForLogs())
        return SignUpVerificationBusyException()
    }

    private fun verifyCode(signUpEntity: SignUpEntity, otp: String)
    {
        val maxAttempts = configurationService.getMaxSignUpCompletionOtpAttempts()
        val rejectionReason = when
        {
            signUpEntity.status == SignUpStatus.VERIFIED -> "sign up already completed"
            signUpEntity.otpAttempts >= maxAttempts -> "attempt budget exhausted"
            signUpEntity.otpExpiryTimestamp.isBefore(LocalDateTime.now()) -> "verification code expired"
            else -> null
        }

        if (rejectionReason != null)
        {
            logger.warn("Sign up completion failed: {}", rejectionReason)
            throw SignUpVerificationRejectedException()
        }

        if (!otpService.verifyEmailOtp(otp, signUpEntity.otp))
        {
            recordFailedAttempt(signUpEntity, maxAttempts)
            throw SignUpVerificationRejectedException()
        }
    }

    private fun recordFailedAttempt(signUpEntity: SignUpEntity, maxAttempts: Long)
    {
        signUpEntity.otpAttempts++
        if (signUpEntity.otpAttempts >= maxAttempts)
        {
            signUpEntity.status = SignUpStatus.EXPIRED_MAX_RETRIES
        }
        signUpRepository.update(signUpEntity)
        logger.warn(
            "Sign up completion failed: wrong verification code, attempt {} of {}",
            signUpEntity.otpAttempts,
            maxAttempts
        )
    }

    private fun ensureNoRegisteredAccount(email: String)
    {
        appUserRepository.findActiveByEmail(email)?.let {
            logger.warn("Sign up completion failed: an account already exists for {}", email.maskEmailForLogs())
            throw AppUserExistsException()
        }
    }

    private fun requireValidEmail(email: String?, operation: String): String
    {
        val normalizedEmail = email.normalizeEmailOrNull()
            ?: throw EmailRequiredException().also { logger.warn("{} failed: Email is null or blank", operation) }

        if (authenticationService.isEmailInvalid(normalizedEmail))
        {
            logger.warn("{} failed: Email validation failed for {}", operation, normalizedEmail.maskEmailForLogs())
            throw InvalidEmailException()
        }

        return normalizedEmail
    }

    private fun validateInputs(email: String?, otp: String?, password: String?, passwordConfirmation: String?): String
    {
        val normalizedEmail = requireValidEmail(email, "Sign up completion")

        if (otp.isNullOrBlank())
        {
            throw OtpRequiredException().also { logger.warn("Sign up completion failed: OTP is null or blank") }
        }

        validatePasswordFields(normalizedEmail, password, passwordConfirmation, "Sign up completion")

        return normalizedEmail
    }

    private fun validatePasswordFields(
        email: String,
        password: String?,
        passwordConfirmation: String?,
        operation: String
    )
    {
        if (password.isNullOrBlank())
        {
            throw PasswordRequiredException().also { logger.warn("{} failed: Password is null or blank", operation) }
        }

        if (passwordConfirmation.isNullOrBlank())
        {
            throw ConfirmationPasswordRequiredException().also {
                logger.warn(
                    "{} failed: Confirmation password is null or blank",
                    operation
                )
            }
        }

        if (!authenticationService.isPasswordStrong(password))
        {
            throw PasswordRequirementsNotMetException().also {
                logger.warn(
                    "{} failed: Password validation failed",
                    operation
                )
            }
        }

        if (password != passwordConfirmation)
        {
            throw PasswordMismatchException().also { logger.warn("{} failed: Passwords do not match", operation) }
        }

        if (password.lowercase().contains(email))
        {
            throw PasswordContainsEmailException().also { logger.warn("{} failed: Password contains email", operation) }
        }
    }

    private fun finalizeSignUp(
        signUpEntity: SignUpEntity,
        email: String,
        password: String,
        confirmationToken: String?
    ): AppUser
    {
        signUpEntity.status = SignUpStatus.VERIFIED
        signUpRepository.update(signUpEntity)

        val passwordSalt = authenticationService.generatePasswordSalt()
        val hashedPassword = authenticationService.hashPassword(password, passwordSalt)

        // Temp-user merge: if the no-auth recipient flow previously created a placeholder
        // AppUser for this email, upgrade it in place. The id is preserved so Exchange,
        // ExchangeParticipant, and other FK references all keep pointing at the same
        // row, no re-pointing or cascading updates needed.
        val existingTemp = appUserRepository.findTemporaryByEmail(email)
        val savedUser = if (existingTemp != null)
        {
            organizationMembershipService.enforceSeatsForAccountActivation(existingTemp.id)
            existingTemp.apply {
                this.email = email
                this.passwordSalt = passwordSalt
                this.password = hashedPassword
                this.emailVerificationComplete = true
                this.isActive = true
                this.isTemporary = false
            }.also {
                appUserRepository.update(it)
                logger.info("Successfully upgraded temp AppUser on sign-up")
            }
        }
        else
        {
            AppUser().apply {
                this.email = email
                this.passwordSalt = passwordSalt
                this.password = hashedPassword
                this.emailVerificationComplete = true
                this.isActive = true
            }.also {
                appUserRepository.save(it)
                logger.info("Successfully signed up new AppUser")
            }
        }

        runCatching { seedContactsAfterSignup(savedUser) }
            .onFailure { logger.warn("Failed to seed contacts after sign-up for {}", email.maskEmailForLogs(), it) }

        // A newly registered account starts on the default individual plan. An upgraded temp
        // placeholder keeps any record it already had rather than being reset.
        runCatching { subscriptionPolicyService.ensureUserPolicy(savedUser.id) }
            .onFailure {
                logger.warn(
                    "Failed to create subscription record after sign-up for {}",
                    email.maskEmailForLogs(),
                    it
                )
            }

        runCatching { createInternalIdpLink(savedUser) }
            .onFailure {
                logger.warn(
                    "Failed to create INTERNAL IDP link after sign-up for {}",
                    email.maskEmailForLogs(),
                    it
                )
            }

        runCatching { appAdminNotificationService.notifyNewUserRegistration(email) }
            .onFailure {
                logger.warn(
                    "Failed to notify an App Administrator about new user {}",
                    email.maskEmailForLogs(),
                    it
                )
            }

        signUpCompletionFollowUpService.scheduleAfterCommit(email, confirmationToken)

        return savedUser
    }

    /**
     * After a sign-up completes, whether it was a fresh new user or an upgrade of a temp
     * placeholder, seed the contact graph for any reciprocity-gated relationships that were
     * deferred at accept time:
     *
     * - Backfill `UserContact.contactAppUserId` for rows that previously stored only the
     *   email (the initiator's view of this user when this user was still temp).
     * - For every accepted session where this user was the recipient, write the recipient-side
     *   contact entry (owner=newUser, contact=session.initiator). The initiator-side was
     *   already recorded at accept time; this completes the bidirectional pair.
     *
     * Failures here must never block the sign-up; the caller wraps this in runCatching.
     */
    private fun seedContactsAfterSignup(newUser: AppUser)
    {
        val updatedRows = userContactService.backfillContactAppUserIdForEmail(newUser.email, newUser.id)
        if (updatedRows > 0)
        {
            logger.info("Backfilled contactAppUserId on {} rows for new user", updatedRows)
        }

        val pastSessions = exchangeRepository.findByRecipientId(newUser.id)
        pastSessions
            .filter { it.status == ExchangeStatus.ACCEPTED_STARTED || it.status == ExchangeStatus.ENDED }
            .forEach { session ->
                val initiator = session.initiator ?: return@forEach
                userContactService.recordOneWayFromSignupMerge(newUser, initiator, session.id)
            }
    }

    /**
     * Creates the INTERNAL identity-provider link for a user who registered with email + password.
     * Idempotent, if the link already exists (e.g. for an upgraded temp user) this is a no-op.
     * Failures must never block sign-up; callers wrap this in runCatching.
     */
    private fun createInternalIdpLink(appUser: AppUser)
    {
        val existing = identityProviderLinkRepository.findByProviderAndExternalSubjectId(
            IdentityProviderType.INTERNAL, appUser.id.toString()
        )
        if (existing != null) return

        val link = IdentityProviderLink().apply {
            this.appUser = appUser
            this.provider = IdentityProviderType.INTERNAL
            this.externalSubjectId = appUser.id.toString()
            this.externalEmail = appUser.email
        }
        identityProviderLinkRepository.save(link)
        logger.info("Created INTERNAL IDP link for user={}", appUser.id)
    }

}
