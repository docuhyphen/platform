package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.resource.model.OAuthTokenExchangeRequest
import com.docuhyphen.app.api.resource.model.OAuthTokenExchangeResponse
import com.docuhyphen.app.api.service.auth.ClientIpResolver
import com.docuhyphen.app.api.service.auth.OAuthStateService
import com.docuhyphen.app.api.service.auth.OAuthTokenHandoff
import com.docuhyphen.app.api.service.auth.OAuthTokenHandoffService
import com.docuhyphen.app.api.service.identity.OrganizationIdpRuntimeCredentialService
import com.docuhyphen.app.api.service.auth.SignedOAuthState
import com.docuhyphen.app.api.service.auth.idp.GoogleIdentityProvider
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.auth.idp.RuntimeIdpCredentials
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.vertx.core.http.HttpServerRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OAuthResourceSecurityTest
{
    private val identityProviderRegistry = mock<IdentityProviderRegistry>()
    private val configurationService = mock<ConfigurationService>()
    private val oauthStateService = mock<OAuthStateService>()
    private val runtimeCredentialService = mock<OrganizationIdpRuntimeCredentialService>()
    private val handoffService = mock<OAuthTokenHandoffService>()
    private val clientIpResolver = mock<ClientIpResolver>()
    private val resource = OAuthResource(
        identityProviderRegistry = identityProviderRegistry,
        oauthUserLinkingService = mock(),
        tokenIssuanceService = mock(),
        authenticationService = mock(),
        appUserService = mock(),
        configurationService = configurationService,
        oauthStateService = oauthStateService,
        authAuditService = mock(),
        authRateLimitService = mock(),
        securityIncidentService = mock(),
        organizationIdentityPolicyService = mock(),
        organizationIdpRuntimeCredentialService = runtimeCredentialService,
        stepUpAuthService = mock(),
        oauthTokenHandoffService = handoffService,
        clientIpResolver = clientIpResolver,
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

        val response = resource.authorize(
            providerName = "google",
            flow = "signin",
            orgIdpConfigId = configId.toString(),
            requestId = null,
            request = mock<HttpServerRequest>(),
        )

        assertEquals(307, response.status)
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
    fun `token exchange consumes one-time handoff without tokens in the URL`()
    {
        whenever(handoffService.consume("one-time-code")).thenReturn(
            OAuthTokenHandoff("access-token", "id-token", false)
        )

        val response = resource.exchangeTokenHandoff(OAuthTokenExchangeRequest("one-time-code"))
        val payload = response.entity as OAuthTokenExchangeResponse

        assertEquals(200, response.status)
        assertEquals("access-token", payload.accessToken)
        assertEquals("id-token", payload.idToken)
    }
}
