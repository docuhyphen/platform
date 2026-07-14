package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.IdentityProviderType.MICROSOFT
import com.docuhyphen.app.api.service.auth.idp.OAuthClaimField.*
import com.docuhyphen.app.api.service.auth.idp.OAuthTokenField.*
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

@ApplicationScoped
class MicrosoftIdentityProvider @Inject constructor(
    private val configurationService: ConfigurationService,
    private val oidcJwksService: OidcJwksService,
) : IdentityProviderStrategy
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(MicrosoftIdentityProvider::class.java)
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
        val tenantId = runtimeCredentials?.tenantId?.takeIf { it.isNotBlank() } ?: configurationService.microsoftOAuthTenantId
        val clientId = runtimeCredentials?.clientId?.takeIf { it.isNotBlank() } ?: configurationService.microsoftOAuthClientId
        val scopes = runtimeCredentials?.scopes?.takeIf { it.isNotBlank() } ?: "openid email profile"
        val encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        val encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8)
        val encodedNonce = URLEncoder.encode(nonce, StandardCharsets.UTF_8)
        val pkcePart = codeChallenge?.takeIf { it.isNotBlank() }
            ?.let { "&code_challenge=${URLEncoder.encode(it, StandardCharsets.UTF_8)}&code_challenge_method=S256" }
            .orEmpty()
        val promptPart = prompt?.takeIf { it.isNotBlank() }
            ?.let { "&prompt=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }
            .orEmpty()

        return "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/authorize" +
                "?client_id=$clientId" +
                "&response_type=code" +
                "&redirect_uri=$encodedRedirectUri" +
                "&response_mode=query" +
                "&scope=${URLEncoder.encode(scopes, StandardCharsets.UTF_8)}" +
                "&state=$encodedState" +
                "&nonce=$encodedNonce" +
                pkcePart +
                promptPart
    }

    override fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        runtimeCredentials: RuntimeIdpCredentials?,
        codeVerifier: String?,
    ): OAuthTokenResponse
    {
        val tenantId = runtimeCredentials?.tenantId?.takeIf { it.isNotBlank() } ?: configurationService.microsoftOAuthTenantId
        val clientId = runtimeCredentials?.clientId?.takeIf { it.isNotBlank() } ?: configurationService.microsoftOAuthClientId
        val clientSecret = runtimeCredentials?.clientSecret?.takeIf { it.isNotBlank() } ?: configurationService.microsoftOAuthClientSecret
        val scopes = runtimeCredentials?.scopes?.takeIf { it.isNotBlank() } ?: "openid email profile"

        val tokenUrl = "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token"

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

        val httpClient = HttpClient.newHttpClient()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200)
        {
            logger.error("Microsoft token exchange failed with status={}", response.statusCode())
            throw RuntimeException("Failed to exchange code with Microsoft")
        }

        val json = OAuthJsonParser.parseJsonToMap(response.body())
        val idTokenRaw = json[ID_TOKEN.fieldName] as? String
        val accessTokenRaw = json[ACCESS_TOKEN.fieldName] as? String

        val userInfo = idTokenRaw?.let { parseIdTokenPayload(it) }

        return OAuthTokenResponse(
            idToken = idTokenRaw,
            accessToken = accessTokenRaw,
            email = userInfo?.email ?: throw RuntimeException("No email in Microsoft ID token"),
            subjectId = userInfo.subjectId,
            name = "${userInfo.firstName ?: ""} ${userInfo.lastName ?: ""}".trim(),
        )
    }

    override fun validateIdToken(idToken: String, expectedNonce: String, runtimeCredentials: RuntimeIdpCredentials?): OAuthUserInfo =
        parseIdTokenPayload(idToken, expectedNonce, runtimeCredentials)

    private fun parseIdTokenPayload(
        idToken: String,
        expectedNonce: String? = null,
        runtimeCredentials: RuntimeIdpCredentials? = null,
    ): OAuthUserInfo
    {
        val parts = idToken.split(".")
        if (parts.size != 3) throw RuntimeException("Invalid Microsoft ID token format")

        val headerJson = String(java.util.Base64.getUrlDecoder().decode(parts[0]))
        val header = OAuthJsonParser.parseJsonToMap(headerJson)
        val alg = header["alg"] as? String ?: throw RuntimeException("Missing alg in Microsoft ID token")
        val kid = header["kid"] as? String ?: throw RuntimeException("Missing kid in Microsoft ID token")
        val allowedAlgs = runtimeCredentials?.allowedAlgs?.ifEmpty { setOf("RS256") } ?: setOf("RS256")
        if (!allowedAlgs.contains(alg) || kid.isBlank())
        {
            throw RuntimeException("Unsupported Microsoft ID token header")
        }

        val tenantId = runtimeCredentials?.tenantId?.takeIf { it.isNotBlank() } ?: configurationService.microsoftOAuthTenantId
        oidcJwksService.verifySignature(
            jwt = idToken,
            jwksUrl = "https://login.microsoftonline.com/$tenantId/discovery/v2.0/keys",
            expectedKid = kid,
        )

        val payloadJson = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        val claims = OAuthJsonParser.parseJsonToMap(payloadJson)

        validateRequiredClaims(claims, runtimeCredentials)

        val issuer = claims["iss"] as? String ?: throw RuntimeException("Missing iss in Microsoft ID token")
        val tokenTenantId = claims["tid"] as? String ?: throw RuntimeException("Missing tid in Microsoft ID token")
        val tokenObjectId = claims["oid"] as? String ?: throw RuntimeException("Missing oid in Microsoft ID token")
        val expectedIssuer = runtimeCredentials?.oidcIssuer?.takeIf { it.isNotBlank() }
        if (expectedIssuer != null)
        {
            if (issuer != expectedIssuer)
            {
                throw RuntimeException("Invalid Microsoft issuer")
            }
        }
        else if (issuer != "https://login.microsoftonline.com/$tokenTenantId/v2.0")
        {
            throw RuntimeException("Invalid Microsoft issuer")
        }

        val acceptedAudiences = (runtimeCredentials?.allowedAudiences ?: emptySet()).ifEmpty {
            setOf(runtimeCredentials?.clientId?.takeIf { it.isNotBlank() } ?: configurationService.microsoftOAuthClientId)
        }
        val audience = claims["aud"] as? String ?: throw RuntimeException("Missing aud in Microsoft ID token")
        if (!acceptedAudiences.contains(audience))
        {
            throw RuntimeException("Invalid Microsoft audience")
        }

        val azp = claims["azp"] as? String
        if (configurationService.isOidcRequireAzpWhenMultiAudEnabled())
        {
            val audList = claims["aud"] as? List<*>
            if (audList != null && audList.size > 1)
            {
                if (azp.isNullOrBlank() || !acceptedAudiences.contains(azp))
                {
                    throw RuntimeException("Invalid Microsoft azp for multi-audience token")
                }
            }
        }

        expectedNonce?.let { nonce ->
            val tokenNonce = claims["nonce"] as? String ?: throw RuntimeException("Missing nonce in Microsoft ID token")
            if (tokenNonce != nonce)
            {
                throw RuntimeException("Microsoft nonce mismatch")
            }
        }

        validateTemporalClaims(claims)

        // Tenant claim check: when a tenantId is configured, the token's tid must match.
        // "common"/"organizations" are multi-tenant placeholders,  accept any tid in that case.
        val configuredTenantId = runtimeCredentials?.tenantId?.takeIf { it.isNotBlank() } ?: configurationService.microsoftOAuthTenantId
        if (!configuredTenantId.isNullOrBlank() && configuredTenantId !in setOf("common", "organizations", "consumers"))
        {
            if (tokenTenantId != configuredTenantId)
            {
                throw RuntimeException("Microsoft tid claim does not match configured tenant")
            }
        }

        return OAuthUserInfo(
            email = (claims[EMAIL.claimName] as? String)
                ?: (claims[PREFERRED_USERNAME.claimName] as? String)
                ?: throw RuntimeException("No email claim in Microsoft ID token"),
            subjectId = "$tokenTenantId:$tokenObjectId",
            firstName = claims[GIVEN_NAME.claimName] as? String,
            lastName = claims[FAMILY_NAME.claimName] as? String,
            legacySubjectId = claims[SUB.claimName] as? String,
        )
    }

    private fun validateTemporalClaims(claims: Map<String, Any?>)
    {
        val nowEpochSeconds = System.currentTimeMillis() / 1000
        val skew = configurationService.getOidcAllowedClockSkewSeconds()

        val exp = (claims["exp"] as? Number)?.toLong() ?: throw RuntimeException("Missing exp in Microsoft ID token")
        val iat = (claims["iat"] as? Number)?.toLong() ?: throw RuntimeException("Missing iat in Microsoft ID token")
        val nbf = (claims["nbf"] as? Number)?.toLong()

        if (exp + skew < nowEpochSeconds)
        {
            throw RuntimeException("Microsoft ID token expired")
        }

        if (iat - skew > nowEpochSeconds)
        {
            throw RuntimeException("Microsoft ID token issued in the future")
        }

        if (nbf != null && nbf - skew > nowEpochSeconds)
        {
            throw RuntimeException("Microsoft ID token not valid yet")
        }
    }

    private fun validateRequiredClaims(claims: Map<String, Any?>, runtimeCredentials: RuntimeIdpCredentials?)
    {
        val requiredClaims = runtimeCredentials?.requiredClaims?.ifEmpty { configurationService.getOidcRequiredClaimsMicrosoft() }
            ?: configurationService.getOidcRequiredClaimsMicrosoft()
        requiredClaims.forEach { claimName ->
            val value = claims[claimName]
            if (value == null || (value is String && value.isBlank()))
            {
                throw RuntimeException("Missing required Microsoft claim: $claimName")
            }
        }
    }
}
