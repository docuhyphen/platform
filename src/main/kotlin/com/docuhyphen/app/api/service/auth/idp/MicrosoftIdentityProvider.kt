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
) : IdentityProviderStrategy
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(MicrosoftIdentityProvider::class.java)
    }

    override fun getProviderType(): IdentityProviderType = MICROSOFT

    override fun buildAuthorizationUrl(state: String, redirectUri: String): String
    {
        val tenantId = configurationService.microsoftOAuthTenantId
        val clientId = configurationService.microsoftOAuthClientId
        val encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        val encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8)

        return "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/authorize" +
                "?client_id=$clientId" +
                "&response_type=code" +
                "&redirect_uri=$encodedRedirectUri" +
                "&response_mode=query" +
                "&scope=${URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)}" +
                "&state=$encodedState"
    }

    override fun exchangeCodeForTokens(code: String, redirectUri: String): OAuthTokenResponse
    {
        val tenantId = configurationService.microsoftOAuthTenantId
        val clientId = configurationService.microsoftOAuthClientId
        val clientSecret = configurationService.microsoftOAuthClientSecret

        val tokenUrl = "https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token"

        val body = "grant_type=authorization_code" +
                "&client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8)}" +
                "&client_secret=${URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)}" +
                "&code=${URLEncoder.encode(code, StandardCharsets.UTF_8)}" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)}" +
                "&scope=${URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)}"

        val httpClient = HttpClient.newHttpClient()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200)
        {
            logger.error("Microsoft token exchange failed: ${response.body()}")
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

    override fun validateIdToken(idToken: String): OAuthUserInfo = parseIdTokenPayload(idToken)

    private fun parseIdTokenPayload(idToken: String): OAuthUserInfo
    {
        val parts = idToken.split(".")
        if (parts.size != 3) throw RuntimeException("Invalid Microsoft ID token format")

        val payloadJson = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        val claims = OAuthJsonParser.parseJsonToMap(payloadJson)

        return OAuthUserInfo(
            email = (claims[EMAIL.claimName] as? String)
                ?: (claims[PREFERRED_USERNAME.claimName] as? String)
                ?: throw RuntimeException("No email claim in Microsoft ID token"),
            subjectId = (claims[SUB.claimName] as? String)
                ?: (claims[OID.claimName] as? String)
                ?: throw RuntimeException("No sub/oid claim in Microsoft ID token"),
            firstName = claims[GIVEN_NAME.claimName] as? String,
            lastName = claims[FAMILY_NAME.claimName] as? String,
        )
    }
}
