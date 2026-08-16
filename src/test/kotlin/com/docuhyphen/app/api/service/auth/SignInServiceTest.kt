package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.identity.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.exception.OTPExpiredException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.MfaRecord
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationStatus.PENDING
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.EMAIL
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.GOOGLE_AUTHENTICATOR
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.communication.MfaService
import com.docuhyphen.app.api.service.communication.OtpService
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant

class SignInServiceTest
{
    private val authenticationService = mock<AuthenticationService>()
    private val tokenIssuanceService = mock<TokenIssuanceService>()
    private val mfaService = mock<MfaService>()
    private val appUserService = mock<AppUserService>()
    private val otpService = mock<OtpService>()
    private val configurationService = mock<ConfigurationService>()
    private val emailService = mock<EmailService>()
    private val emailTemplateService = mock<EmailTemplateService>()
    private val organizationIdentityPolicyService = mock<OrganizationIdentityPolicyService>()
    private val authenticatorMfaService = mock<AuthenticatorMfaService>()

    private val service = SignInService(
        authenticationService,
        tokenIssuanceService,
        mfaService,
        appUserService,
        otpService,
        configurationService,
        emailService,
        emailTemplateService,
        organizationIdentityPolicyService,
        authenticatorMfaService,
    )

    @Test
    fun `email sign in OTP still expires by email OTP expiry timestamp`()
    {
        val record = expiredMfaRecord(EMAIL)
        whenever(mfaService.getMfaRecordByEmailAndSessionId("user@example.com", "session-id"))
            .thenReturn(record)

        assertThrows<OTPExpiredException> {
            service.completeSignIn("user@example.com", "123456", "session-id")
        }

        verify(otpService, never()).verifyEmailOtp(any(), any())
        verify(authenticatorMfaService, never()).verifyUserCode(any(), any())
    }

    @Test
    fun `authenticator app sign in ignores email OTP expiry timestamp`()
    {
        val appUser = AppUser().apply {
            email = "user@example.com"
            mfaType = GOOGLE_AUTHENTICATOR
            authenticatorSecretEncrypted = "encrypted-secret"
        }
        val record = expiredMfaRecord(GOOGLE_AUTHENTICATOR, appUser)
        val tokenTriple = TokenTriple(
            accessToken = "access-token",
            idToken = "id-token",
            refreshToken = "refresh-token",
            refreshTokenJti = "refresh-token-jti",
        )

        whenever(mfaService.getMfaRecordByEmailAndSessionId("user@example.com", "session-id"))
            .thenReturn(record)
        whenever(configurationService.getMaxSignInAttempts()).thenReturn(3)
        whenever(authenticatorMfaService.verifyUserCode(appUser, "123456")).thenReturn(true)
        whenever(tokenIssuanceService.issueTokenTriple(appUser, null, null)).thenReturn(tokenTriple)

        val result = service.completeSignIn("user@example.com", "123456", "session-id")

        assertEquals(tokenTriple, result)
        verify(authenticatorMfaService).verifyUserCode(appUser, "123456")
        verify(mfaService).updateRecord(record)
        verify(mfaService).removeMfaRecord(record)
    }

    private fun expiredMfaRecord(
        mfaType: com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType,
        appUser: AppUser = AppUser().apply {
            email = "user@example.com"
            this.mfaType = mfaType
        },
    ): MfaRecord = MfaRecord().apply {
        this.appUser = appUser
        this.mfaToken = "hashed-token"
        this.expiryDateTime = Timestamp.from(Instant.now().minusSeconds(60))
        this.mfaType = mfaType
        this.status = PENDING
        this.sessionId = "session-id"
    }
}
