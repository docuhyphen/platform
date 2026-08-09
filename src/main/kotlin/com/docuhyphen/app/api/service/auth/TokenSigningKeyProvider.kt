package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import io.jsonwebtoken.security.Keys
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** The distinct kinds of token this service issues, each with its own signing key. */
enum class TokenPurpose(val label: String)
{
    /** Access, ID, and application tokens presented on the Authorization header. */
    USER_TOKEN("docuhyphen/token/user/v1"),

    /** Short-lived tokens that authorize binding an external identity to an account. */
    LINK_TOKEN("docuhyphen/token/link/v1"),

    /** Signed OAuth `state` parameters. */
    OAUTH_STATE("docuhyphen/token/oauth-state/v1"),
}

/**
 * Derives a separate signing key per [TokenPurpose] from the single configured root secret.
 *
 * Signing every token class with one key means the only thing standing between an OAuth state
 * token and an access token is a string comparison on a claim. Deriving per-purpose keys makes
 * cross-purpose replay a cryptographic failure rather than a validation-order bug: a token
 * signed for one purpose simply will not verify under another purpose's key.
 *
 * Derivation is HKDF-Expand (RFC 5869) over HMAC-SHA-256 with the purpose label as `info`,
 * which needs no extra configuration and no key distribution changes.
 */
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

        // Single-block HKDF-Expand: T(1) = HMAC(PRK, info || 0x01)
        mac.update(purpose.label.toByteArray(Charsets.UTF_8))
        mac.update(0x01)
        val derived = mac.doFinal().copyOf(DERIVED_KEY_BYTES)

        return Keys.hmacShaKeyFor(derived)
    }
}

