package com.docuhyphen.app.api.service.auth
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

data class VerifiedOAuthState(
    val flow: String,
    val provider: IdentityProviderType,
    val nonce: String,
    val orgIdpConfigId: UUID? = null,
    val codeVerifier: String? = null,
    val stepUpSessionId: UUID? = null,
    val stepUpAppUserId: UUID? = null,
    val stepUpExpectedSubjectId: String? = null,
    val stepUpReturnTo: String? = null,
    val linkAppUserId: UUID? = null,
)

data class SignedOAuthState(
    val token: String,
    val nonce: String,
    val codeChallenge: String,
)

@RequestScoped
class OAuthStateService @Inject constructor(
    private val configurationService: ConfigurationService,
    private val redis: Redis,
)
{
    companion object
    {
        private const val OAUTH_NONCE_PREFIX = "oauth_state_nonce:"
        private const val OAUTH_PKCE_PREFIX = "oauth_state_pkce:"
        private val secureRandom = SecureRandom()
    }

    private val stateSigningKey: SecretKey = Keys.hmacShaKeyFor(configurationService.getJwtSecret().toByteArray())

    fun createSignedState(
        flow: String,
        provider: IdentityProviderType,
        orgIdpConfigId: UUID? = null,
        stepUpSessionId: UUID? = null,
        stepUpAppUserId: UUID? = null,
        stepUpExpectedSubjectId: String? = null,
        stepUpReturnTo: String? = null,
        linkAppUserId: UUID? = null,
    ): SignedOAuthState
    {
        val normalizedFlow = flow.ifBlank { "signin" }
        val nonce = UUID.randomUUID().toString()
        val ttlSeconds = configurationService.getOauthStateTtlSeconds()
        val expiration = Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ttlSeconds))

        // PKCE: 32-byte random verifier → S256 challenge (RFC 7636)
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = deriveS256Challenge(codeVerifier)

        redis.send(
            Request.cmd(Command.SET)
                .arg("$OAUTH_NONCE_PREFIX$nonce")
                .arg("1")
                .arg("EX")
                .arg(ttlSeconds.toString())
        ).await().indefinitely()

        redis.send(
            Request.cmd(Command.SET)
                .arg("$OAUTH_PKCE_PREFIX$nonce")
                .arg(codeVerifier)
                .arg("EX")
                .arg(ttlSeconds.toString())
        ).await().indefinitely()

        val stateToken = Jwts.builder()
            .claim("flow", normalizedFlow)
            .claim("provider", provider.name)
            .claim("nonce", nonce)
            .claim("orgIdpConfigId", orgIdpConfigId?.toString())
            .claim("stepUpSessionId", stepUpSessionId?.toString())
            .claim("stepUpAppUserId", stepUpAppUserId?.toString())
            .claim("stepUpExpectedSubjectId", stepUpExpectedSubjectId)
            .claim("stepUpReturnTo", stepUpReturnTo)
            .claim("linkAppUserId", linkAppUserId?.toString())
            .issuedAt(Date())
            .expiration(expiration)
            .signWith(stateSigningKey)
            .compact()

        return SignedOAuthState(token = stateToken, nonce = nonce, codeChallenge = codeChallenge)
    }

    fun verifyAndConsumeState(stateToken: String, expectedProvider: IdentityProviderType): VerifiedOAuthState?
    {
        val claims = try
        {
            Jwts.parser().verifyWith(stateSigningKey).build().parseSignedClaims(stateToken).payload
        }
        catch (_: Exception)
        {
            return null
        }
        val providerRaw = claims["provider"] as? String ?: return null
        val nonce = claims["nonce"] as? String ?: return null
        val flow = (claims["flow"] as? String).orEmpty().ifBlank { "signin" }
        val orgIdpConfigId = (claims["orgIdpConfigId"] as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val provider = runCatching { IdentityProviderType.valueOf(providerRaw) }.getOrNull() ?: return null
        if (provider != expectedProvider)
        {
            return null
        }

        val deleted = redis.send(
            Request.cmd(Command.DEL).arg("$OAUTH_NONCE_PREFIX$nonce")
        ).await().indefinitely()?.toLong() ?: 0L
        if (deleted != 1L)
        {
            return null
        }

        // Pop the PKCE verifier atomically (GETDEL is the cleanest way; fall back to GET+DEL)
        val verifierResponse = redis.send(
            Request.cmd(Command.GETDEL).arg("$OAUTH_PKCE_PREFIX$nonce")
        ).await().indefinitely()
        val codeVerifier = verifierResponse?.toString()?.ifBlank { null }

        val stepUpSessionId = (claims["stepUpSessionId"] as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val stepUpAppUserId = (claims["stepUpAppUserId"] as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val stepUpExpectedSubjectId = (claims["stepUpExpectedSubjectId"] as? String)
            ?.takeIf { it.isNotBlank() }
        val stepUpReturnTo = (claims["stepUpReturnTo"] as? String)
            ?.takeIf { it.isNotBlank() }
        val linkAppUserId = (claims["linkAppUserId"] as? String)
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        return VerifiedOAuthState(
            flow = flow,
            provider = provider,
            nonce = nonce,
            orgIdpConfigId = orgIdpConfigId,
            codeVerifier = codeVerifier,
            stepUpSessionId = stepUpSessionId,
            stepUpAppUserId = stepUpAppUserId,
            stepUpExpectedSubjectId = stepUpExpectedSubjectId,
            stepUpReturnTo = stepUpReturnTo,
            linkAppUserId = linkAppUserId,
        )
    }

    private fun generateCodeVerifier(): String
    {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun deriveS256Challenge(verifier: String): String
    {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256)
    }
}
