package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.SignUpEntity
import com.docuhyphen.app.api.repository.user.AppUserRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.identity.IdentityProviderLinkRepository
import com.docuhyphen.app.api.repository.auth.SignUpRepository
import com.docuhyphen.app.api.service.contactdetails.UserContactService
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.DisposableEmailDomainService
import com.docuhyphen.app.api.service.auth.SignUpEmailConfirmationTokenService
import com.docuhyphen.app.api.service.auth.SignUpService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import org.mindrot.jbcrypt.BCrypt
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

internal class SignUpPermutationFixture(
    rawEmail: String,
)
{
    val normalizedEmail: String = rawEmail.trim().lowercase()
    val temporaryUser: AppUser = AppUser().apply {
        email = normalizedEmail
        isActive = false
        isTemporary = true
    }
    private val signUpRepository: SignUpRepository = mock()
    private val appUserRepository: AppUserRepository = mock()
    private val identityProviderLinkRepository: IdentityProviderLinkRepository = mock()
    private val emailService: EmailService = mock()
    private val emailTemplateService: EmailTemplateService = mock()
    private val configurationService: ConfigurationService = mock()
    private val authenticationService: AuthenticationService = mock()
    private val userContactService: UserContactService = mock()
    private val exchangeRepository: ExchangeRepository = mock()
    private val otp = "123456"
    private val password = "StrongPass!42"
    private val service: SignUpService = SignUpService(
        signUpRepository,
        appUserRepository,
        identityProviderLinkRepository,
        emailService,
        emailTemplateService,
        mock<OtpService>(),
        configurationService,
        authenticationService,
        mock<SignUpEmailConfirmationTokenService>(),
        userContactService,
        exchangeRepository,
        mock<DisposableEmailDomainService>(),
        mock<SubscriptionPolicyService>(),
        mock<com.docuhyphen.app.api.service.organization.OrganizationMembershipService>(),
    )

    init
    {
        val signUp = SignUpEntity().apply {
            email = normalizedEmail
            this.otp = BCrypt.hashpw(this@SignUpPermutationFixture.otp, BCrypt.gensalt(4))
            otpExpiryTimestamp = LocalDateTime.now().plusMinutes(10)
        }
        whenever(signUpRepository.findByEmail(normalizedEmail)).thenReturn(signUp)
        whenever(appUserRepository.findActiveByEmail(normalizedEmail)).thenReturn(null)
        whenever(appUserRepository.findTemporaryByEmail(normalizedEmail)).thenReturn(temporaryUser)
        whenever(authenticationService.isEmailInvalid(normalizedEmail)).thenReturn(false)
        whenever(authenticationService.isPasswordStrong(password)).thenReturn(true)
        whenever(authenticationService.generatePasswordSalt()).thenReturn("salt")
        whenever(authenticationService.hashPassword(password, "salt")).thenReturn("hash")
        whenever(configurationService.getMaxSignUpCompletionOtpAttempts()).thenReturn(5)
        whenever(configurationService.emailSubjectTitle).thenReturn("DocuHyphen")
        whenever(configurationService.getNewUserNotificationEmail()).thenReturn("admin@example.test")
        whenever(emailTemplateService.renderSignUpCompletionEmail(normalizedEmail)).thenReturn("completed")
        whenever(emailTemplateService.renderNewUserRegistrationNotificationEmail(normalizedEmail))
            .thenReturn("registered")
        whenever(userContactService.backfillContactAppUserIdForEmail(normalizedEmail, temporaryUser.id))
            .thenReturn(0)
        whenever(exchangeRepository.findByRecipientId(temporaryUser.id)).thenReturn(emptyList())
        whenever(
            identityProviderLinkRepository.findByProviderAndExternalSubjectId(any(), any()),
        ).thenReturn(null)
    }

    fun complete(email: String = normalizedEmail): AppUser =
        service.completeSignUp(email, otp, password, password)
}
