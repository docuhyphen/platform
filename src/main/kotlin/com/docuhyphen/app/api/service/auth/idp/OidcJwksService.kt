package com.docuhyphen.app.api.service.auth.idp

import com.docuhyphen.app.api.service.config.ConfigurationService
import io.jsonwebtoken.Jwts
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches and caches provider JWKS documents, and verifies ID token signatures against them.
 *
 * Two throttles protect the outbound path, because the key id is taken from an unauthenticated
 * token supplied by whoever called the OAuth callback:
 *
 * - a minimum interval between refreshes of the same JWKS URL, so a burst of crafted key ids
 *   cannot be turned into a burst of outbound requests
 * - a negative cache of key ids that were absent from a freshly fetched document, so repeating
 *   the same unknown key id does not force repeated refreshes
 *
 * Both are per JWKS URL and expire, so genuine provider key rotation is still picked up.
 */
@ApplicationScoped
class OidcJwksService @Inject constructor(
    private val oidcHttpClient: OidcHttpClient,
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OidcJwksService::class.java)
        private const val CACHE_TTL_SECONDS = 3600L
        private const val MAX_JWKS_KEYS = 32
        private const val MIN_RSA_MODULUS_BITS = 2048
    }

    private val cache: ConcurrentHashMap<String, CachedJwks> = ConcurrentHashMap()
    private val refreshLocks: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

    fun verifySignature(jwt: String, jwksUrl: String, expectedKid: String)
    {
        val key = getKey(jwksUrl, expectedKid)
            ?: throw OidcValidationException("No JWKS key found for the presented key id")

        try
        {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt)
        }
        catch (e: Exception)
        {
            throw OidcValidationException("OIDC signature verification failed: ${e.message}")
        }
    }

    private fun getKey(jwksUrl: String, kid: String): RSAPublicKey?
    {
        val now = Instant.now().epochSecond
        val cached = cache[jwksUrl]

        if (cached != null && now - cached.loadedAtEpochSeconds <= CACHE_TTL_SECONDS)
        {
            cached.keysByKid[kid]?.let { return it }

            // The key id is unknown. Only pay for a refresh when this key id has not already
            // been proven absent recently, and when the refresh floor has elapsed.
            if (cached.isKnownAbsent(kid, now, configurationService.getOidcJwksUnknownKidNegativeCacheSeconds()))
            {
                return null
            }

            if (now - cached.loadedAtEpochSeconds < configurationService.getOidcJwksMinRefreshIntervalSeconds())
            {
                cached.rememberAbsent(kid, now)
                return null
            }
        }

        return refreshAndLookUp(jwksUrl, kid)
    }

    /**
     * Serialises refreshes per JWKS URL so a burst of concurrent callbacks produces one
     * outbound fetch rather than one per request.
     */
    private fun refreshAndLookUp(jwksUrl: String, kid: String): RSAPublicKey?
    {
        val lock = refreshLocks.computeIfAbsent(jwksUrl) { Any() }

        synchronized(lock) {
            val now = Instant.now().epochSecond
            val current = cache[jwksUrl]

            // Another thread may have refreshed while this one waited on the lock.
            if (current != null && now - current.loadedAtEpochSeconds < configurationService.getOidcJwksMinRefreshIntervalSeconds())
            {
                current.keysByKid[kid]?.let { return it }
                current.rememberAbsent(kid, now)
                return null
            }

            val refreshed = fetchKeys(jwksUrl)
            cache[jwksUrl] = refreshed

            val key = refreshed.keysByKid[kid]
            if (key == null)
            {
                refreshed.rememberAbsent(kid, now)
                logger.warn("JWKS refresh did not contain the requested key id for url={}", jwksUrl)
            }
            return key
        }
    }

    private fun fetchKeys(jwksUrl: String): CachedJwks
    {
        val response = oidcHttpClient.getJson(jwksUrl)
        if (response.statusCode() != 200)
        {
            logger.error("JWKS fetch failed: status={} url={}", response.statusCode(), jwksUrl)
            throw OidcValidationException("Failed to fetch JWKS")
        }

        val payload = OAuthJsonParser.parseJsonToMap(response.body())
        val keys = payload["keys"] as? List<*>
            ?: throw OidcValidationException("Invalid JWKS payload: missing keys")

        val byKid = mutableMapOf<String, RSAPublicKey>()

        keys.take(MAX_JWKS_KEYS).forEach { raw ->
            val item = raw as? Map<*, *> ?: return@forEach
            val kty = item["kty"] as? String ?: return@forEach
            val kid = item["kid"] as? String ?: return@forEach
            val n = item["n"] as? String ?: return@forEach
            val e = item["e"] as? String ?: return@forEach
            val use = item["use"] as? String

            if (kty != "RSA") return@forEach

            // A key published for encryption must never be accepted as a signature key.
            if (use != null && use != "sig") return@forEach

            runCatching { buildRsaPublicKey(n, e) }
                .onSuccess { byKid[kid] = it }
                .onFailure { ex -> logger.warn("Failed building RSA key for a JWKS entry: {}", ex.message) }
        }

        return CachedJwks(
            loadedAtEpochSeconds = Instant.now().epochSecond,
            keysByKid = byKid,
        )
    }

    private fun buildRsaPublicKey(modulusB64Url: String, exponentB64Url: String): RSAPublicKey
    {
        val decoder = Base64.getUrlDecoder()
        val modulus = BigInteger(1, decoder.decode(modulusB64Url))
        val exponent = BigInteger(1, decoder.decode(exponentB64Url))

        // Reject undersized moduli outright rather than relying on the JWT library to notice.
        if (modulus.bitLength() < MIN_RSA_MODULUS_BITS)
        {
            throw IllegalArgumentException("RSA modulus is below the $MIN_RSA_MODULUS_BITS bit minimum")
        }

        val keySpec = RSAPublicKeySpec(modulus, exponent)
        val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
        val key: PublicKey = keyFactory.generatePublic(keySpec)
        return key as RSAPublicKey
    }
}

private class CachedJwks(
    val loadedAtEpochSeconds: Long,
    val keysByKid: Map<String, RSAPublicKey>,
)
{
    private val absentKids: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    fun rememberAbsent(kid: String, nowEpochSeconds: Long)
    {
        if (absentKids.size > 512)
        {
            absentKids.clear()
        }
        absentKids[kid] = nowEpochSeconds
    }

    fun isKnownAbsent(kid: String, nowEpochSeconds: Long, negativeCacheSeconds: Long): Boolean
    {
        val recordedAt = absentKids[kid] ?: return false
        if (nowEpochSeconds - recordedAt > negativeCacheSeconds)
        {
            absentKids.remove(kid)
            return false
        }
        return true
    }
}
