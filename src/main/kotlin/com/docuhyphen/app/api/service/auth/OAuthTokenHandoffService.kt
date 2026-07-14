package com.docuhyphen.app.api.service.auth

import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.security.SecureRandom
import java.util.Base64

data class OAuthTokenHandoff(
    val accessToken: String,
    val idToken: String,
    val isNewUser: Boolean,
)

@RequestScoped
class OAuthTokenHandoffService @Inject constructor(
    private val redis: Redis,
)
{
    companion object
    {
        private const val KEY_PREFIX = "oauth_token_handoff:"
        private const val TTL_SECONDS = 60
        private val secureRandom = SecureRandom()
    }

    fun create(accessToken: String, idToken: String, isNewUser: Boolean): String
    {
        val codeBytes = ByteArray(32).also(secureRandom::nextBytes)
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes)
        val value = listOf(accessToken, idToken, isNewUser.toString()).joinToString("\n")

        redis.send(
            Request.cmd(Command.SET)
                .arg("$KEY_PREFIX$code")
                .arg(value)
                .arg("EX")
                .arg(TTL_SECONDS.toString())
        ).await().indefinitely()

        return code
    }

    fun consume(code: String): OAuthTokenHandoff?
    {
        if (code.isBlank()) return null

        val raw = redis.send(
            Request.cmd(Command.GETDEL).arg("$KEY_PREFIX$code")
        ).await().indefinitely()?.toString() ?: return null
        val parts = raw.split('\n', limit = 3)
        if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) return null

        return OAuthTokenHandoff(
            accessToken = parts[0],
            idToken = parts[1],
            isNewUser = parts[2].toBooleanStrictOrNull() ?: return null,
        )
    }
}
