package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.model.auth.idp.DecodedIdToken
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

class OidcValidationException(message: String) : RuntimeException(message)

@ApplicationScoped
class OidcTokenValidator @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val DEFAULT_ALLOWED_ALGS = setOf("RS256", "RS384", "RS512")
    }

    fun decode(idToken: String, provider: String): DecodedIdToken
    {
        val parts = idToken.split(".")
        if (parts.size != 3)
        {
            throw OidcValidationException("Invalid $provider ID token format")
        }

        val header = runCatching { OAuthJsonParser.parseJsonToMap(String(Base64.getUrlDecoder().decode(parts[0]))) }
            .getOrElse { throw OidcValidationException("Invalid $provider ID token header encoding") }
        val claims = runCatching { OAuthJsonParser.parseJsonToMap(String(Base64.getUrlDecoder().decode(parts[1]))) }
            .getOrElse { throw OidcValidationException("Invalid $provider ID token payload encoding") }

        return DecodedIdToken(header = header, claims = claims)
    }

    fun requireSupportedHeader(header: Map<String, Any?>, allowedAlgs: Set<String>, provider: String): String
    {
        val alg = header["alg"] as? String ?: throw OidcValidationException("Missing alg in $provider ID token")
        val kid = header["kid"] as? String ?: throw OidcValidationException("Missing kid in $provider ID token")
        val accepted = allowedAlgs.ifEmpty { DEFAULT_ALLOWED_ALGS }

        if (!accepted.contains(alg) || !DEFAULT_ALLOWED_ALGS.contains(alg) || kid.isBlank())
        {
            throw OidcValidationException("Unsupported $provider ID token header")
        }

        return kid
    }

    fun requireClaims(claims: Map<String, Any?>, requiredClaims: Set<String>, provider: String)
    {
        requiredClaims.forEach { claimName ->
            val value = claims[claimName]
            if (value == null || (value is String && value.isBlank()))
            {
                throw OidcValidationException("Missing required $provider claim: $claimName")
            }
        }
    }

    fun requireIssuer(claims: Map<String, Any?>, acceptedIssuers: Set<String>, provider: String): String
    {
        val issuer = claims["iss"] as? String ?: throw OidcValidationException("Missing iss in $provider ID token")
        if (!acceptedIssuers.contains(issuer))
        {
            throw OidcValidationException("Invalid $provider issuer")
        }
        return issuer
    }

    fun requireAudience(claims: Map<String, Any?>, acceptedAudiences: Set<String>, provider: String)
    {
        if (acceptedAudiences.isEmpty())
        {
            throw OidcValidationException("No accepted audience configured for $provider")
        }

        val audiences = readAudiences(claims)
        if (audiences.isEmpty())
        {
            throw OidcValidationException("Missing aud in $provider ID token")
        }

        if (audiences.none { acceptedAudiences.contains(it) })
        {
            throw OidcValidationException("Invalid $provider audience")
        }

        if (audiences.size > 1 && configurationService.isOidcRequireAzpWhenMultiAudEnabled())
        {
            val azp = claims["azp"] as? String
            if (azp.isNullOrBlank() || !acceptedAudiences.contains(azp))
            {
                throw OidcValidationException("Invalid $provider azp for multi-audience token")
            }
        }
    }

    fun requireNonce(claims: Map<String, Any?>, expectedNonce: String, provider: String)
    {
        val tokenNonce = claims["nonce"] as? String
            ?: throw OidcValidationException("Missing nonce in $provider ID token")

        val expected = expectedNonce.toByteArray(Charsets.UTF_8)
        val presented = tokenNonce.toByteArray(Charsets.UTF_8)
        if (!java.security.MessageDigest.isEqual(expected, presented))
        {
            throw OidcValidationException("$provider nonce mismatch")
        }
    }

    fun requireValidTemporalClaims(claims: Map<String, Any?>, provider: String)
    {
        val nowEpochSeconds = System.currentTimeMillis() / 1000
        val skew = configurationService.getOidcAllowedClockSkewSeconds()

        val exp = (claims["exp"] as? Number)?.toLong()
            ?: throw OidcValidationException("Missing exp in $provider ID token")
        val iat = (claims["iat"] as? Number)?.toLong()
            ?: throw OidcValidationException("Missing iat in $provider ID token")
        val nbf = (claims["nbf"] as? Number)?.toLong()

        if (exp + skew < nowEpochSeconds)
        {
            throw OidcValidationException("$provider ID token expired")
        }

        if (iat - skew > nowEpochSeconds)
        {
            throw OidcValidationException("$provider ID token issued in the future")
        }

        if (nbf != null && nbf - skew > nowEpochSeconds)
        {
            throw OidcValidationException("$provider ID token not valid yet")
        }
    }

    private fun readAudiences(claims: Map<String, Any?>): List<String>
    {
        return when (val aud = claims["aud"])
        {
            is String -> listOf(aud).filter { it.isNotBlank() }
            is Collection<*> -> aud.mapNotNull { it as? String }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }
}
