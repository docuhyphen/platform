package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.MultifactorAuthenticationType.GOOGLE_AUTHENTICATOR
import com.docuhyphen.app.api.repository.auth.AuthenticatorEnrollmentRepository
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthenticatorMfaServiceTest
{
    private val enrollmentRepository = mock<AuthenticatorEnrollmentRepository>()
    private val appUserService = mock<AppUserService>()
    private val configurationService = mock<ConfigurationService>().also {
        whenever(it.getJwtSecret()).thenReturn("test-secret-that-is-at-least-32-characters")
    }
    private val service = AuthenticatorMfaService(
        enrollmentRepository,
        appUserService,
        configurationService,
    )

    @Test
    fun `generates the RFC 6238 SHA1 code`()
    {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

        assertEquals("287082", service.generateCode(secret, 59L / 30L))
    }

    @Test
    fun `email is the default and fallback is disabled`()
    {
        val configuration = service.getConfiguration(AppUser())

        assertEquals("EMAIL", configuration.method)
        assertFalse(configuration.authenticatorConfigured)
        assertFalse(configuration.emailFallbackEnabled)
    }

    @Test
    fun `email fallback requires an authenticator configuration`()
    {
        assertThrows(IllegalArgumentException::class.java) {
            service.updateEmailFallback(AppUser(), true)
        }
    }

    @Test
    fun `email fallback can be explicitly enabled for authenticator MFA`()
    {
        val appUser = AppUser().apply {
            mfaType = GOOGLE_AUTHENTICATOR
            authenticatorSecretEncrypted = "encrypted"
        }

        val configuration = service.updateEmailFallback(appUser, true)

        assertTrue(configuration.emailFallbackEnabled)
        verify(appUserService).update(appUser)
    }
}
