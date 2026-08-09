package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.auth.idp.GoogleIdentityProvider
import com.docuhyphen.app.api.service.auth.idp.MicrosoftIdentityProvider
import com.docuhyphen.app.api.service.auth.idp.OidcHttpClient
import com.docuhyphen.app.api.service.auth.idp.OidcJwksService
import com.docuhyphen.app.api.service.auth.idp.OidcTokenValidator
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
    private val oidcHttpClient = mock<OidcHttpClient>()
    private val oidcTokenValidator = OidcTokenValidator(configurationService)

    private fun googleProvider() =
        GoogleIdentityProvider(configurationService, oidcJwksService, oidcTokenValidator, oidcHttpClient)

    private fun microsoftProvider() =
        MicrosoftIdentityProvider(configurationService, oidcJwksService, oidcTokenValidator, oidcHttpClient)

    @Test
    fun `Google rejects an unverified email claim`()
    {
        configureCommonOidc()
        configureGoogle()
        val token = jwt(
            """{"iss":"https://accounts.google.com","aud":"google-client","sub":"subject-1","email":"user@example.com","email_verified":false,"nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        assertThrows<RuntimeException> { googleProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Google accepts a verified email claim`()
    {
        configureCommonOidc()
        configureGoogle()
        val token = jwt(
            """{"iss":"https://accounts.google.com","aud":"google-client","sub":"subject-1","email":"user@example.com","email_verified":true,"nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        val userInfo = googleProvider().validateIdToken(token, "nonce-1")

        assertEquals("subject-1", userInfo.subjectId)
        assertTrue(userInfo.emailVerified)
    }

    @Test
    fun `Google rejects a token whose audience array excludes this client`()
    {
        configureCommonOidc()
        configureGoogle()
        val token = jwt(
            """{"iss":"https://accounts.google.com","aud":["other-client","third-client"],"azp":"other-client","sub":"subject-1","email":"user@example.com","email_verified":true,"nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        assertThrows<RuntimeException> { googleProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Google requires azp to name this client on a multi-audience token`()
    {
        configureCommonOidc()
        configureGoogle()
        whenever(configurationService.isOidcRequireAzpWhenMultiAudEnabled()).thenReturn(true)
        val token = jwt(
            """{"iss":"https://accounts.google.com","aud":["google-client","other-client"],"azp":"other-client","sub":"subject-1","email":"user@example.com","email_verified":true,"nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        assertThrows<RuntimeException> { googleProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Google rejects a mismatched nonce`()
    {
        configureCommonOidc()
        configureGoogle()
        val token = jwt(
            """{"iss":"https://accounts.google.com","aud":"google-client","sub":"subject-1","email":"user@example.com","email_verified":true,"nonce":"attacker-nonce","iat":${now()},"exp":${now() + 300}}"""
        )

        assertThrows<RuntimeException> { googleProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Microsoft identity key uses tenant and immutable object ID`()
    {
        configureCommonOidc()
        configureMicrosoft(tenantId = "tenant-1")
        val token = microsoftToken("https://login.microsoftonline.com/tenant-1/v2.0")

        val userInfo = microsoftProvider().validateIdToken(token, "nonce-1")

        assertEquals("tenant-1:object-1", userInfo.subjectId)
    }

    @Test
    fun `Microsoft rejects an issuer that only contains the trusted hostname`()
    {
        configureCommonOidc()
        configureMicrosoft(tenantId = "tenant-1")
        val token = microsoftToken("https://example.test/login.microsoftonline.com/tenant-1/v2.0")

        assertThrows<RuntimeException> { microsoftProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Microsoft rejects a token from an unpinned tenant`()
    {
        configureCommonOidc()
        configureMicrosoft(tenantId = "common", allowMultiTenant = false)
        val token = microsoftToken("https://login.microsoftonline.com/attacker-tenant/v2.0", tenantId = "attacker-tenant")

        assertThrows<RuntimeException> { microsoftProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Microsoft rejects a token whose tenant does not match the pinned tenant`()
    {
        configureCommonOidc()
        configureMicrosoft(tenantId = "tenant-1")
        val token = microsoftToken("https://login.microsoftonline.com/attacker-tenant/v2.0", tenantId = "attacker-tenant")

        assertThrows<RuntimeException> { microsoftProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Microsoft rejects preferred_username as an email source by default`()
    {
        configureCommonOidc()
        configureMicrosoft(tenantId = "tenant-1", requireEmailDomainOwnerVerified = false)
        val token = jwt(
            """{"iss":"https://login.microsoftonline.com/tenant-1/v2.0","aud":"microsoft-client","sub":"subject-1","oid":"object-1","tid":"tenant-1","preferred_username":"ceo@victim.example","nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        assertThrows<RuntimeException> { microsoftProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Microsoft rejects an email that the tenant has not proven it owns`()
    {
        configureCommonOidc()
        configureMicrosoft(tenantId = "tenant-1")
        val token = jwt(
            """{"iss":"https://login.microsoftonline.com/tenant-1/v2.0","aud":"microsoft-client","sub":"subject-1","oid":"object-1","tid":"tenant-1","email":"ceo@victim.example","nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        assertThrows<RuntimeException> { microsoftProvider().validateIdToken(token, "nonce-1") }
    }

    @Test
    fun `Microsoft accepts an email backed by the domain owner verified claim`()
    {
        configureCommonOidc()
        configureMicrosoft(tenantId = "tenant-1")
        val token = jwt(
            """{"iss":"https://login.microsoftonline.com/tenant-1/v2.0","aud":"microsoft-client","sub":"subject-1","oid":"object-1","tid":"tenant-1","email":"user@example.com","xms_edov":true,"nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
        )

        val userInfo = microsoftProvider().validateIdToken(token, "nonce-1")

        assertEquals("user@example.com", userInfo.email)
        assertTrue(userInfo.emailVerified)
    }

    @Test
    fun `Google authorization URL uses organization client credentials`()
    {
        val url = googleProvider().buildAuthorizationUrl(
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
        val url = microsoftProvider().buildAuthorizationUrl(
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

    private fun configureGoogle()
    {
        whenever(configurationService.googleOAuthClientId).thenReturn("google-client")
        whenever(configurationService.getOidcRequiredClaimsGoogle()).thenReturn(
            setOf("sub", "email", "email_verified", "iss", "aud", "exp", "iat", "nonce")
        )
    }

    private fun configureMicrosoft(
        tenantId: String,
        allowMultiTenant: Boolean = false,
        requireEmailDomainOwnerVerified: Boolean = true,
    )
    {
        whenever(configurationService.microsoftOAuthClientId).thenReturn("microsoft-client")
        whenever(configurationService.microsoftOAuthTenantId).thenReturn(tenantId)
        whenever(configurationService.isMicrosoftMultiTenantAllowed()).thenReturn(allowMultiTenant)
        whenever(configurationService.isMicrosoftEmailDomainOwnerVerifiedRequired())
            .thenReturn(requireEmailDomainOwnerVerified)
        whenever(configurationService.isMicrosoftPreferredUsernameAsEmailAllowed()).thenReturn(false)
        whenever(configurationService.getOidcRequiredClaimsMicrosoft()).thenReturn(
            setOf("sub", "oid", "tid", "iss", "aud", "exp", "iat", "nonce")
        )
    }

    private fun microsoftToken(issuer: String, tenantId: String = "tenant-1"): String = jwt(
        """{"iss":"$issuer","aud":"microsoft-client","sub":"subject-1","oid":"object-1","tid":"$tenantId","email":"user@example.com","xms_edov":true,"nonce":"nonce-1","iat":${now()},"exp":${now() + 300}}"""
    )

    private fun jwt(payload: String): String
    {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","kid":"key-1"}""".toByteArray())
        return "$header.${encoder.encodeToString(payload.toByteArray())}.signature"
    }

    private fun now(): Long = Instant.now().epochSecond
}
