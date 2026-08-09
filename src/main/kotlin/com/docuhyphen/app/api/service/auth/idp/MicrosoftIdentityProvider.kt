package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.IdentityProviderType.MICROSOFT
import com.docuhyphen.app.api.service.auth.idp.OAuthClaimField.*
import com.docuhyphen.app.api.service.auth.idp.OAuthTokenField.*
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@ApplicationScoped
class MicrosoftIdentityProvider @Inject constructor(
    private val configurationService: ConfigurationService,
    private val oidcJwksService: OidcJwksService,
    private val oidcTokenValidator: OidcTokenValidator,
    private val oidcHttpClient: OidcHttpClient,
) : IdentityProviderStrategy
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(MicrosoftIdentityProvider::class.java)
        private const val PROVIDER_LABEL = "Microsoft"

        /**
         * Tenant values that mean "any directory". A token carrying one of these was not
         * issued by a directory we have any relationship with, so nothing in it can be
         * treated as an assertion about who the user is outside that stranger's tenant.
         */
        private val MULTI_TENANT_PLACEHOLDERS = setOf("common", "organizations", "consumers")
    }

    override fun getProviderType(): IdentityProviderType = MICROSOFT

    override fun buildAuthorizationUrl(
        state: String,
        nonce: String,
        redirectUri: String,
        runtimeCredentials: RuntimeIdpCredentials?,
        codeChallenge: String?,
        prompt: String?,
    ): String
    {
        val tenantId = resolveTenantId(runtimeCredentials)
        val clientId = resolveClientId(runtimeCredentials)
        val scopes = runtimeCredentials?.scopes?.takeIf { it.isNotBlank() } ?: "openid email profile"
        val encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        val encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8)
        val encodedNonce = URLEncoder.encode(nonce, StandardCharsets.UTF_8)
        val pkcePart = codeChallenge?.takeIf { it.isNotBlank() }
            ?.let { "&code_challenge=${URLEncoder.encode(it, StandardCharsets.UTF_8)}&code_challenge_method=S256" }
            .orEmpty()
        val effectivePrompt = prompt?.takeIf { it.isNotBlank() } ?: "select_account"

        return "https://login.microsoftonline.com/${URLEncoder.encode(tenantId, StandardCharsets.UTF_8)}/oauth2/v2.0/authorize" +
                "?client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8)}" +
                "&response_type=code" +
                "&redirect_uri=$encodedRedirectUri" +
                "&response_mode=query" +
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
        val tenantId = resolveTenantId(runtimeCredentials)
        val clientId = resolveClientId(runtimeCredentials)
        val clientSecret = runtimeCredentials?.clientSecret?.takeIf { it.isNotBlank() }
            ?: configurationService.microsoftOAuthClientSecret
        val scopes = runtimeCredentials?.scopes?.takeIf { it.isNotBlank() } ?: "openid email profile"

        val tokenUrl = "https://login.microsoftonline.com/${URLEncoder.encode(tenantId, StandardCharsets.UTF_8)}/oauth2/v2.0/token"

        val verifierPart = codeVerifier?.takeIf { it.isNotBlank() }
            ?.let { "&code_verifier=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }
            .orEmpty()

        val body = "grant_type=authorization_code" +
                "&client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8)}" +
                "&client_secret=${URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)}" +
                "&code=${URLEncoder.encode(code, StandardCharsets.UTF_8)}" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)}" +
                "&scope=${URLEncoder.encode(scopes, StandardCharsets.UTF_8)}" +
                verifierPart

        val response = oidcHttpClient.postForm(tokenUrl, body)

        if (response.statusCode() != 200)
        {
            logger.error("Microsoft token exchange failed with status={}", response.statusCode())
            throw OidcValidationException("Failed to exchange code with Microsoft")
        }

        val json = OAuthJsonParser.parseJsonToMap(response.body())
        val idTokenRaw = json[ID_TOKEN.fieldName] as? String
            ?: throw OidcValidationException("Microsoft token response did not contain an ID token")

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

        val claims = decoded.claims
        val tokenTenantId = claims["tid"] as? String
            ?: throw OidcValidationException("Missing tid in Microsoft ID token")
        val tokenObjectId = claims[OID.claimName] as? String
            ?: throw OidcValidationException("Missing oid in Microsoft ID token")

        // The tenant must be pinned before the JWKS URL is derived, otherwise an attacker's
        // tid would select the key set that signs their own token.
        val effectiveTenantId = requirePinnedTenant(tokenTenantId, runtimeCredentials)

        oidcJwksService.verifySignature(
            jwt = idToken,
            jwksUrl = "https://login.microsoftonline.com/$effectiveTenantId/discovery/v2.0/keys",
            expectedKid = kid,
        )

        oidcTokenValidator.requireClaims(
            claims = claims,
            requiredClaims = runtimeCredentials?.requiredClaims?.ifEmpty { null }
                ?: configurationService.getOidcRequiredClaimsMicrosoft(),
            provider = PROVIDER_LABEL,
        )

        oidcTokenValidator.requireIssuer(
            claims = claims,
            acceptedIssuers = runtimeCredentials?.oidcIssuer?.takeIf { it.isNotBlank() }?.let { setOf(it) }
                ?: setOf("https://login.microsoftonline.com/$tokenTenantId/v2.0"),
            provider = PROVIDER_LABEL,
        )

        oidcTokenValidator.requireAudience(
            claims = claims,
            acceptedAudiences = resolveAcceptedAudiences(runtimeCredentials),
            provider = PROVIDER_LABEL,
        )

        oidcTokenValidator.requireNonce(claims, expectedNonce, PROVIDER_LABEL)
        oidcTokenValidator.requireValidTemporalClaims(claims, PROVIDER_LABEL)

        val resolvedEmail = resolveEmail(claims)

        return OAuthUserInfo(
            // tid:oid is the only stable, tenant-scoped identifier Microsoft guarantees.
            // `sub` is pairwise per application and is kept only to migrate historic links.
            subjectId = "$tokenTenantId:$tokenObjectId",
            email = resolvedEmail.address,
            emailVerified = resolvedEmail.verified,
            firstName = claims[GIVEN_NAME.claimName] as? String,
            lastName = claims[FAMILY_NAME.claimName] as? String,
            legacySubjectId = claims[SUB.claimName] as? String,
        )
    }

    /**
     * Confirms that the signing directory is one this deployment actually trusts.
     *
     * Multi-tenant placeholders are only permitted when a deployment has explicitly opted in.
     * Without this, anyone can register a free Microsoft directory, set a user's mail attribute
     * to an address they do not own, and present the resulting token as that person.
     */
    private fun requirePinnedTenant(tokenTenantId: String, runtimeCredentials: RuntimeIdpCredentials?): String
    {
        val configuredTenantId = resolveTenantId(runtimeCredentials).trim()
        val isPlaceholder = configuredTenantId.lowercase() in MULTI_TENANT_PLACEHOLDERS

        if (!isPlaceholder)
        {
            if (!tokenTenantId.equals(configuredTenantId, ignoreCase = true))
            {
                throw OidcValidationException("Microsoft tid claim does not match the configured tenant")
            }
            return configuredTenantId
        }

        if (!configurationService.isMicrosoftMultiTenantAllowed())
        {
            throw OidcValidationException(
                "Microsoft sign-in is not pinned to a directory; multi-tenant tokens are rejected"
            )
        }

        return tokenTenantId
    }

    /**
     * Resolves the mailbox address to attribute this sign-in to.
     *
     * Microsoft only asserts that the address belongs to the signing tenant when `xms_edov`
     * (email domain owner verified) is true. `preferred_username` is a mutable display value,
     * never a verified mailbox, so it is rejected unless a deployment explicitly opts in.
     */
    private fun resolveEmail(claims: Map<String, Any?>): ResolvedEmail
    {
        val emailClaim = (claims[EMAIL.claimName] as? String)?.takeIf { it.isNotBlank() }
        val preferredUsername = (claims[PREFERRED_USERNAME.claimName] as? String)?.takeIf { it.isNotBlank() }

        val address = emailClaim
            ?: preferredUsername?.takeIf { configurationService.isMicrosoftPreferredUsernameAsEmailAllowed() }
            ?: throw OidcValidationException("No usable email claim in Microsoft ID token")

        val domainOwnerVerified = readEmailDomainOwnerVerified(claims)

        if (configurationService.isMicrosoftEmailDomainOwnerVerifiedRequired() && !domainOwnerVerified)
        {
            throw OidcValidationException(
                "Microsoft did not assert email domain ownership (xms_edov) for this account"
            )
        }

        return ResolvedEmail(address = address, verified = domainOwnerVerified)
    }

    /** `xms_edov` is emitted as a boolean by some tenants and as a "1"/"0" string by others. */
    private fun readEmailDomainOwnerVerified(claims: Map<String, Any?>): Boolean
    {
        return when (val raw = claims["xms_edov"])
        {
            is Boolean -> raw
            is Number -> raw.toInt() == 1
            is String -> raw.equals("true", ignoreCase = true) || raw == "1"
            else -> false
        }
    }

    private fun resolveTenantId(runtimeCredentials: RuntimeIdpCredentials?): String =
        runtimeCredentials?.tenantId?.takeIf { it.isNotBlank() }
            ?: configurationService.microsoftOAuthTenantId

    private fun resolveClientId(runtimeCredentials: RuntimeIdpCredentials?): String =
        runtimeCredentials?.clientId?.takeIf { it.isNotBlank() }
            ?: configurationService.microsoftOAuthClientId

    private fun resolveAcceptedAudiences(runtimeCredentials: RuntimeIdpCredentials?): Set<String>
    {
        val configured = runtimeCredentials?.allowedAudiences.orEmpty()
        if (configured.isNotEmpty())
        {
            return configured
        }

        return setOf(resolveClientId(runtimeCredentials)).filter { it.isNotBlank() }.toSet()
    }

    private data class ResolvedEmail(val address: String, val verified: Boolean)
}
