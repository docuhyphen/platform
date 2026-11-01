package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import io.jsonwebtoken.security.Keys
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

enum class TokenPurpose(val label: String)
{
    USER_TOKEN("docuhyphen/token/user/v1"),

    LINK_TOKEN("docuhyphen/token/link/v1"),

    OAUTH_STATE("docuhyphen/token/oauth-state/v1"),
}

@ApplicationScoped
class TokenSigningKeyProvider @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val DERIVED_KEY_BYTES = 32
    }

    private val derivedKeys: ConcurrentHashMap<TokenPurpose, SecretKey> = ConcurrentHashMap()

    fun keyFor(purpose: TokenPurpose): SecretKey =
        derivedKeys.computeIfAbsent(purpose) { derive(it) }

    private fun derive(purpose: TokenPurpose): SecretKey
    {
        val rootSecret = configurationService.getJwtSecret().toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(rootSecret, HMAC_ALGORITHM))

        mac.update(purpose.label.toByteArray(Charsets.UTF_8))
        mac.update(0x01)
        val derived = mac.doFinal().copyOf(DERIVED_KEY_BYTES)

        return Keys.hmacShaKeyFor(derived)
    }
}

