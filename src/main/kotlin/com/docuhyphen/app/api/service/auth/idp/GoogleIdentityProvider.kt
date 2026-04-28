package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.entity.IdentityProviderType
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
class GoogleIdentityProvider @Inject constructor(
    private val configurationService: ConfigurationService,
) : IdentityProviderStrategy
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(GoogleIdentityProvider::class.java)
    }

    override fun getProviderType(): IdentityProviderType = IdentityProviderType.GOOGLE

    override fun buildAuthorizationUrl(state: String, redirectUri: String): String
    {
        val clientId = configurationService.googleOAuthClientId
        val encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        val encodedState = URLEncoder.encode(state, StandardCharsets.UTF_8)

        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=$clientId" +
                "&response_type=code" +
                "&redirect_uri=$encodedRedirectUri" +
                "&scope=${URLEncoder.encode("openid email profile", StandardCharsets.UTF_8)}" +
                "&state=$encodedState" +
                "&access_type=offline" +
                "&prompt=consent"
    }

    override fun exchangeCodeForTokens(code: String, redirectUri: String): OAuthTokenResponse
    {
        val clientId = configurationService.googleOAuthClientId
        val clientSecret = configurationService.googleOAuthClientSecret

        val tokenUrl = "https://oauth2.googleapis.com/token"

        val body = "grant_type=authorization_code" +
                "&client_id=${URLEncoder.encode(clientId, StandardCharsets.UTF_8)}" +
                "&client_secret=${URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)}" +
                "&code=${URLEncoder.encode(code, StandardCharsets.UTF_8)}" +
                "&redirect_uri=${URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)}"

        val httpClient = HttpClient.newHttpClient()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200)
        {
            logger.error("Google token exchange failed: ${response.body()}")
            throw RuntimeException("Failed to exchange code with Google")
        }

        val json = OAuthJsonParser.parseJsonToMap(response.body())
        val idTokenRaw = json[ID_TOKEN.fieldName] as? String
        val accessTokenRaw = json[ACCESS_TOKEN.fieldName] as? String

        val userInfo = idTokenRaw?.let { parseIdTokenPayload(it) }

        return OAuthTokenResponse(
            idToken = idTokenRaw,
            accessToken = accessTokenRaw,
            email = userInfo?.email ?: throw RuntimeException("No email in Google ID token"),
            subjectId = userInfo.subjectId,
            name = "${userInfo.firstName ?: ""} ${userInfo.lastName ?: ""}".trim(),
        )
    }

    override fun validateIdToken(idToken: String): OAuthUserInfo = parseIdTokenPayload(idToken)

    private fun parseIdTokenPayload(idToken: String): OAuthUserInfo
    {
        val parts = idToken.split(".")
        if (parts.size != 3) throw RuntimeException("Invalid Google ID token format")

        val payloadJson = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
        val claims = OAuthJsonParser.parseJsonToMap(payloadJson)

        return OAuthUserInfo(
            email = claims[EMAIL.claimName] as? String
                ?: throw RuntimeException("No email claim in Google ID token"),
            subjectId = claims[SUB.claimName] as? String
                ?: throw RuntimeException("No sub claim in Google ID token"),
            firstName = claims[GIVEN_NAME.claimName] as? String,
            lastName = claims[FAMILY_NAME.claimName] as? String,
        )
    }
}
