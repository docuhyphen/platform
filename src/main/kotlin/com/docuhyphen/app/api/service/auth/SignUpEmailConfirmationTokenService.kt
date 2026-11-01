package com.docuhyphen.app.api.service.auth

import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.*

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

    fun peekToken(token: String): String?
    {
        if (!isWellFormed(token)) return null

        val response = redis.send(
            Request.cmd(Command.GET).arg("$KEY_PREFIX$token")
        ).await().indefinitely()

        return response?.toString()?.takeIf { it.isNotBlank() }
    }

    fun revokeToken(token: String)
    {
        if (!isWellFormed(token))
        {
            logger.debug("Sign-up confirmation token revocation skipped: malformed input")
            return
        }

        redis.send(
            Request.cmd(Command.DEL).arg("$KEY_PREFIX$token")
        ).await().indefinitely()
    }

    private fun generateToken(): String
    {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return base64UrlEncoder.encodeToString(bytes)
    }

    private fun isWellFormed(token: String): Boolean
    {
        if (token.length !in 16..128) return false
        return token.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' }
    }
}
