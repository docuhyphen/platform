package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.service.auth.idp.OAuthClaimField.*
import com.docuhyphen.app.api.service.auth.idp.OAuthTokenField.*
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApplicationScoped
class GoogleIdentityProvider @Inject constructor(
    private val configurationService: ConfigurationService,
    private val oidcJwksService: OidcJwksService,
    private val oidcTokenValidator: OidcTokenValidator,
    private val oidcHttpClient: OidcHttpClient,
) : IdentityProviderStrategy
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(GoogleIdentityProvider::class.java)
        private const val PROVIDER_LABEL = "Google"
        private const val JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private val ACCEPTED_ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")
    }

    override fun getProviderType(): IdentityProviderType = IdentityProviderType.GOOGLE

    override fun buildAuthorizationUrl(
        state: String,
        nonce: String,
        redirectUri: String,
        runtimeCredentials: RuntimeIdpCredentials?,
        codeChallenge: String?,
        prompt: String?,
    ): String
    {
        val clientId = runtimeCredentials?.clientId?.takeIf { it.isNotBlank() } ?: configurationService.googleOAuthClientId
        val scopes = runtimeCredentials?.scopes?.takeIf { it.isNotBlank() } ?: "openid email profile"
        val encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        val encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8)
        val encodedNonce = URLEncoder.encode(nonce, StandardCharsets.UTF_8)
        val pkcePart = codeChallenge?.takeIf { it.isNotBlank() }
            ?.let { "&code_challenge=${URLEncoder.encode(it, StandardCharsets.UTF_8)}&code_challenge_method=S256" }
            .orEmpty()

        // select_account lets the user pick an identity without re-granting scopes on every
        // sign-in. Callers that genuinely need consent (first-time authorization, scope change)
        // pass their own prompt value.
        val effectivePrompt = prompt?.takeIf { it.isNotBlank() } ?: "select_account"

        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8)}" +
                "&response_type=code" +
                "&redirect_uri=$encodedRedirectUri" +
                "&scope=${URLEncoder.encode(scopes, StandardCharsets.UTF_8)}" +
                "&state=$encodedState" +
                "&nonce=$encodedNonce" +
                "&prompt=${URLEncoder.encode(effectivePrompt, StandardCharsets.UTF_8)}" +
                pkcePart
    }

    /**
     * Exchanges the authorization code for tokens.
     *
     * The ID token is deliberately not inspected here. Validation needs the request nonce and
     * the organization's runtime credentials, neither of which belongs to a transport-level
     * exchange, so the caller validates the returned token through [validateIdToken] and uses
     * only that result to make an identity decision.
     */
    override fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        runtimeCredentials: RuntimeIdpCredentials?,
        codeVerifier: String?,
    ): OAuthTokenResponse
    {
        val clientId = runtimeCredentials?.clientId?.takeIf { it.isNotBlank() } ?: configurationService.googleOAuthClientId
        val clientSecret = runtimeCredentials?.clientSecret?.takeIf { it.isNotBlank() } ?: configurationService.googleOAuthClientSecret

        val verifierPart = codeVerifier?.takeIf { it.isNotBlank() }
            ?.let { "&code_verifier=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }
            .orEmpty()

        val body = "grant_type=authorization_code" +
                "&client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8)}" +
                "&client_secret=${URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)}" +
                "&code=${URLEncoder.encode(code, StandardCharsets.UTF_8)}" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)}" +
                verifierPart

        val response = oidcHttpClient.postForm(TOKEN_URL, body)

        if (response.statusCode() != 200)
        {
            logger.error("Google token exchange failed with status={}", response.statusCode())
            throw OidcValidationException("Failed to exchange code with Google")
        }

        val json = OAuthJsonParser.parseJsonToMap(response.body())
        val idTokenRaw = json[ID_TOKEN.fieldName] as? String
            ?: throw OidcValidationException("Google token response did not contain an ID token")

        return OAuthTokenResponse(
            idToken = idTokenRaw,
            accessToken = json[ACCESS_TOKEN.fieldName] as? String,
            email = "",
            subjectId = "",
            name = null,
        )
    }

    override fun validateIdToken(idToken: String, expectedNonce: String, runtimeCredentials: RuntimeIdpCredentials?): OAuthUserInfo
    {
        val decoded = oidcTokenValidator.decode(idToken, PROVIDER_LABEL)
        val kid = oidcTokenValidator.requireSupportedHeader(
            header = decoded.header,
            allowedAlgs = runtimeCredentials?.allowedAlgs.orEmpty(),
            provider = PROVIDER_LABEL,
        )

        oidcJwksService.verifySignature(jwt = idToken, jwksUrl = JWKS_URL, expectedKid = kid)

        val claims = decoded.claims

        oidcTokenValidator.requireClaims(
            claims = claims,
            requiredClaims = runtimeCredentials?.requiredClaims?.ifEmpty { null }
                ?: configurationService.getOidcRequiredClaimsGoogle(),
            provider = PROVIDER_LABEL,
        )

        oidcTokenValidator.requireIssuer(
            claims = claims,
            acceptedIssuers = runtimeCredentials?.oidcIssuer?.takeIf { it.isNotBlank() }?.let { setOf(it) } ?: ACCEPTED_ISSUERS,
            provider = PROVIDER_LABEL,
        )

        oidcTokenValidator.requireAudience(
            claims = claims,
            acceptedAudiences = resolveAcceptedAudiences(runtimeCredentials),
            provider = PROVIDER_LABEL,
        )

        oidcTokenValidator.requireNonce(claims, expectedNonce, PROVIDER_LABEL)
        oidcTokenValidator.requireValidTemporalClaims(claims, PROVIDER_LABEL)

        requireHostedDomain(claims, runtimeCredentials)

        if (claims["email_verified"] as? Boolean != true)
        {
            throw OidcValidationException("Google email is not verified")
        }

        return OAuthUserInfo(
            email = claims[EMAIL.claimName] as? String
                ?: throw OidcValidationException("No email claim in Google ID token"),
            subjectId = claims[SUB.claimName] as? String
                ?: throw OidcValidationException("No sub claim in Google ID token"),
            firstName = claims[GIVEN_NAME.claimName] as? String,
            lastName = claims[FAMILY_NAME.claimName] as? String,
            emailVerified = true,
        )
    }

    private fun resolveAcceptedAudiences(runtimeCredentials: RuntimeIdpCredentials?): Set<String>
    {
        val configured = runtimeCredentials?.allowedAudiences.orEmpty()
        if (configured.isNotEmpty())
        {
            return configured
        }

        val clientId = runtimeCredentials?.clientId?.takeIf { it.isNotBlank() } ?: configurationService.googleOAuthClientId
        return setOf(clientId).filter { it.isNotBlank() }.toSet()
    }

    /**
     * Optional Google Workspace tenancy pin. When the organization config supplies a workspace
     * domain, the token's `hd` claim must match it. Personal Google accounts never carry `hd`,
     * so they fail closed against a workspace-scoped configuration.
     */
    private fun requireHostedDomain(claims: Map<String, Any?>, runtimeCredentials: RuntimeIdpCredentials?)
    {
        val expectedHostedDomain = runtimeCredentials?.workspaceDomain?.takeIf { it.isNotBlank() } ?: return
        val hd = claims[HD.claimName] as? String

        if (hd.isNullOrBlank() || !hd.equals(expectedHostedDomain, ignoreCase = true))
        {
            throw OidcValidationException("Google hd claim does not match the configured workspace domain")
        }
    }
}
