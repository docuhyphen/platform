package com.docuhyphen.app.api.service.auth

import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.Base64

/**
 * Issues, peeks, and consumes opaque single-use confirmation tokens used in
 * the sign-up email verification link.
 *
 * Each token is a 32-byte cryptographically random value, base64url-encoded
 * (43 ASCII chars, no padding). Tokens map to the user's email address and
 * are stored in Redis with a TTL matching the OTP expiry window.
 *
 * Replaces the previous design of passing `?email=&otp=` in the URL — those
 * values leaked through browser history, Referer headers, and proxy access
 * logs. An opaque token has no exploitable structure and is consumed atomically
 * on the first valid verification (via GETDEL), so even a replay from a
 * leaked URL won't help an attacker once the original user has clicked it.
 *
 * The 6-digit OTP remains in the DB as a fallback for users who type the
 * code by hand instead of clicking the email link.
 */
@ApplicationScoped
class SignUpEmailConfirmationTokenService @Inject constructor(
    private val redis: Redis,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SignUpEmailConfirmationTokenService::class.java)
        private const val KEY_PREFIX = "signup_confirm:"
        private const val TOKEN_BYTES = 32
        private val secureRandom = SecureRandom()
        private val base64UrlEncoder = Base64.getUrlEncoder().withoutPadding()
    }

    /**
     * Generate a fresh confirmation token for [email] and store it in Redis with
     * the given TTL. Any previous token for the same email is left alone (it
     * will simply expire on its own; the email address is recoverable from
     * whichever token the user actually clicks).
     */
    fun issueToken(email: String, ttlMinutes: Long): String
    {
        val token = generateToken()
        val ttlSeconds = ttlMinutes * 60L

        redis.send(
            Request.cmd(Command.SET)
                .arg("$KEY_PREFIX$token")
                .arg(email)
                .arg("EX")
                .arg(ttlSeconds.toString())
        ).await().indefinitely()

        return token
    }

    /**
     * Look up the email address associated with [token] without consuming it.
     * Returns null if the token is missing/expired/malformed.
     *
     * Used by the GET introspection endpoint so the frontend can render
     * "verifying user@example.com" before the user types a password.
     */
    fun peekToken(token: String): String?
    {
        if (!isWellFormed(token)) return null

        val response = redis.send(
            Request.cmd(Command.GET).arg("$KEY_PREFIX$token")
        ).await().indefinitely()

        return response?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * Atomically read & delete the email mapping for [token].
     * Returns null if the token was already consumed, expired, or never existed.
     *
     * GETDEL is single-round-trip atomic in Redis 6.2+; we rely on that to
     * make consumption single-use across concurrent clicks.
     */
    fun consumeToken(token: String): String?
    {
        if (!isWellFormed(token))
        {
            logger.debug("Sign-up confirmation token rejected: malformed input")
            return null
        }

        val response = redis.send(
            Request.cmd(Command.GETDEL).arg("$KEY_PREFIX$token")
        ).await().indefinitely()

        return response?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun generateToken(): String
    {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return base64UrlEncoder.encodeToString(bytes)
    }

    /**
     * Quick syntactic sanity check before hitting Redis. Real validation is
     * "does this exist in Redis"; this just avoids round-trips on obviously
     * bogus input and helps defend against log spam from scanners.
     */
    private fun isWellFormed(token: String): Boolean
    {
        // 32 bytes base64url-encoded without padding = 43 chars from the set [A-Za-z0-9_-].
        if (token.length !in 16..128) return false
        return token.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' }
    }
}
