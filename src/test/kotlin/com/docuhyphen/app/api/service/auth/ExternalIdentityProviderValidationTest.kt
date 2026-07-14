package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.auth.idp.GoogleIdentityProvider
import com.docuhyphen.app.api.service.auth.idp.MicrosoftIdentityProvider
import com.docuhyphen.app.api.service.auth.idp.OidcJwksService
import com.docuhyphen.app.api.service.auth.idp.RuntimeIdpCredentials
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Base64

class ExternalIdentityProviderValidationTest
{
    private val configurationService = mock<ConfigurationService>()
    private val oidcJwksService = mock<OidcJwksService>()

    @Test
    fun `Google rejects an unverified email claim`()
    {
        configureCommonOidc()
        whenever(configurationService.googleOAuthClientId).thenReturn("google-client")
        whenever(configurationService.getOidcRequiredClaimsGoogle()).thenReturn(
            setOf("sub", "email", "email_verified", "iss", "aud", "exp", "iat", "nonce")
        )
        val provider = GoogleIdentityProvider(configurationService, oidcJwksService)
        val token = jwt(
            """{"iss":"https://accounts.google.com","aud":"google-client","sub":"subject-1","email":"user@example.com","email_verified":false,"nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        assertThrows<RuntimeException> { provider.validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Google accepts a verified email claim`()
    {
        configureCommonOidc()
        whenever(configurationService.googleOAuthClientId).thenReturn("google-client")
        whenever(configurationService.getOidcRequiredClaimsGoogle()).thenReturn(
            setOf("sub", "email", "email_verified", "iss", "aud", "exp", "iat", "nonce")
        )
        val provider = GoogleIdentityProvider(configurationService, oidcJwksService)
        val token = jwt(
            """{"iss":"https://accounts.google.com","aud":"google-client","sub":"subject-1","email":"user@example.com","email_verified":true,"nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        assertEquals("subject-1", provider.validateIdToken(token, "nonce-1").subjectId)
    }

    @Test
    fun `Microsoft identity key uses tenant and immutable object ID`()
    {
        configureCommonOidc()
        configureMicrosoft()
        val provider = MicrosoftIdentityProvider(configurationService, oidcJwksService)
        val token = microsoftToken("https://login.microsoftonline.com/tenant-1/v2.0")

        val userInfo = provider.validateIdToken(token, "nonce-1")

        assertEquals("tenant-1:object-1", userInfo.subjectId)
    }

    @Test
    fun `Microsoft rejects an issuer that only contains the trusted hostname`()
    {
        configureCommonOidc()
        configureMicrosoft()
        val provider = MicrosoftIdentityProvider(configurationService, oidcJwksService)
        val token = microsoftToken("https://example.test/login.microsoftonline.com/tenant-1/v2.0")

        assertThrows<RuntimeException> { provider.validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Google authorization URL uses organization client credentials`()
    {
        val provider = GoogleIdentityProvider(configurationService, oidcJwksService)

        val url = provider.buildAuthorizationUrl(
            state = "state",
            nonce = "nonce",
            redirectUri = "https://app.example.com/auth/oauth/google/callback",
            runtimeCredentials = RuntimeIdpCredentials("org-google-client", "secret"),
        )

        assertTrue(url.contains("client_id=org-google-client"))
    }

    @Test
    fun `Microsoft authorization URL uses organization tenant and client credentials`()
    {
        val provider = MicrosoftIdentityProvider(configurationService, oidcJwksService)

        val url = provider.buildAuthorizationUrl(
            state = "state",
            nonce = "nonce",
            redirectUri = "https://app.example.com/auth/oauth/microsoft/callback",
            runtimeCredentials = RuntimeIdpCredentials(
                clientId = "org-microsoft-client",
                clientSecret = "secret",
                tenantId = "tenant-1",
            ),
        )

        assertTrue(url.startsWith("https://login.microsoftonline.com/tenant-1/"))
        assertTrue(url.contains("client_id=org-microsoft-client"))
    }

    private fun configureCommonOidc()
    {
        whenever(configurationService.getOidcAllowedClockSkewSeconds()).thenReturn(0)
        whenever(configurationService.isOidcRequireAzpWhenMultiAudEnabled()).thenReturn(false)
    }

    private fun configureMicrosoft()
    {
        whenever(configurationService.microsoftOAuthClientId).thenReturn("microsoft-client")
        whenever(configurationService.microsoftOAuthTenantId).thenReturn("common")
        whenever(configurationService.getOidcRequiredClaimsMicrosoft()).thenReturn(
            setOf("sub", "oid", "tid", "iss", "aud", "exp", "iat", "nonce")
        )
    }

    private fun microsoftToken(issuer: String): String = jwt(
        """{"iss":"$issuer","aud":"microsoft-client","sub":"subject-1","oid":"object-1","tid":"tenant-1","preferred_username":"user@example.com","nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
    )

    private fun jwt(payload: String): String
    {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","kid":"key-1"}""".toByteArray())
        return "$header.${encoder.encodeToString(payload.toByteArray())}.signature"
    }

    private fun now(): Long = Instant.now().epochSecond
}
