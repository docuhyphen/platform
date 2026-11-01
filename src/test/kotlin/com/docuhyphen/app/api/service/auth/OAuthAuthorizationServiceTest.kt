package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.auth.SignedOAuthState
import com.docuhyphen.app.api.model.auth.idp.RuntimeIdpCredentials
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.auth.idp.GoogleIdentityProvider
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.identity.OrganizationIdpRuntimeCredentialService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class OAuthAuthorizationServiceTest
{
    private val identityProviderRegistry = mock<IdentityProviderRegistry>()
    private val configurationService = mock<ConfigurationService>()
    private val oauthStateService = mock<OAuthStateService>()
    private val runtimeCredentialService = mock<OrganizationIdpRuntimeCredentialService>()
    private val authRateLimitService = mock<AuthRateLimitService>()
    private val securityIncidentService = mock<SecurityIncidentService>()
    private val service = OAuthAuthorizationService(
        identityProviderRegistry = identityProviderRegistry,
        configurationService = configurationService,
        oauthStateService = oauthStateService,
        authAuditService = mock(),
        authRateLimitService = authRateLimitService,
        securityIncidentService = securityIncidentService,
        organizationIdpRuntimeCredentialService = runtimeCredentialService,
        oauthRedirectUriResolver = OAuthRedirectUriResolver(configurationService),
    )

    @Test
    fun `authorize uses the selected organization runtime credentials`()
    {
        val configId = UUID.randomUUID()
        val state = SignedOAuthState("state-token", "nonce", "challenge")
        val runtimeCredentials = RuntimeIdpCredentials("org-client", "org-secret")
        val provider = mock<GoogleIdentityProvider>()
        whenever(identityProviderRegistry.getProvider(IdentityProviderType.GOOGLE)).thenReturn(provider)
        whenever(configurationService.googleOAuthRedirectUri).thenReturn("https://api.example.com/auth/oauth/google/callback")
        whenever(oauthStateService.createSignedState("signin", IdentityProviderType.GOOGLE, configId)).thenReturn(state)
        whenever(runtimeCredentialService.resolve(IdentityProviderType.GOOGLE, configId)).thenReturn(runtimeCredentials)
        whenever(
            provider.buildAuthorizationUrl(
                state.token,
                state.nonce,
                "https://api.example.com/auth/oauth/google/callback",
                runtimeCredentials,
                state.codeChallenge,
                null,
            )
        ).thenReturn("https://accounts.google.com/authorize")

        val location = service.authorize(
            providerName = "google",
            flow = "signin",
            orgIdpConfigId = configId.toString(),
            requestId = null,
            clientIp = "203.0.113.10",
        )

        assertEquals("https://accounts.google.com/authorize", location.toString())
        verify(provider).buildAuthorizationUrl(
            state.token,
            state.nonce,
            "https://api.example.com/auth/oauth/google/callback",
            runtimeCredentials,
            state.codeChallenge,
            null,
        )
    }

    @Test
    fun `authorize refuses a rate limited client before touching the provider`()
    {
        whenever(authRateLimitService.isLimited(any(), any())).thenReturn(true)

        assertThrows(AuthRequestRateLimitedException::class.java) {
            service.authorize(
                providerName = "google",
                flow = "signin",
                orgIdpConfigId = null,
                requestId = "req-1",
                clientIp = "203.0.113.10",
            )
        }

        verifyNoInteractions(identityProviderRegistry)
        verify(securityIncidentService).record(
            eq(SecurityIncidentType.AUTH_RATE_LIMIT_OAUTH_AUTHORIZE),
            eq(SecurityIncidentSeverity.MEDIUM),
            anyOrNull(),
            eq("req-1"),
            eq("ip=203.0.113.10;provider=google"),
            any(),
        )
    }
}
