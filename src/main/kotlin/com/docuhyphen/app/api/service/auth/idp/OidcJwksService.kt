package com.docuhyphen.app.api.service.auth.idp

import io.jsonwebtoken.Jwts
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class OidcJwksService
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OidcJwksService::class.java)
        private const val CACHE_TTL_SECONDS = 3600L
    }

    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private val cache: ConcurrentHashMap<String, CachedJwks> = ConcurrentHashMap()

    fun verifySignature(jwt: String, jwksUrl: String, expectedKid: String)
    {
        val key = getKey(jwksUrl, expectedKid)
            ?: throw RuntimeException("No JWKS key found for kid=$expectedKid")

        try
        {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt)
        }
        catch (e: Exception)
        {
            throw RuntimeException("OIDC signature verification failed", e)
        }
    }

    private fun getKey(jwksUrl: String, kid: String): RSAPublicKey?
    {
        val cached = cache[jwksUrl]
        val now = Instant.now().epochSecond

        if (cached != null && now - cached.loadedAtEpochSeconds <= CACHE_TTL_SECONDS)
        {
            cached.keysByKid[kid]?.let { return it }
        }

        val refreshed = fetchKeys(jwksUrl)
        cache[jwksUrl] = refreshed

        return refreshed.keysByKid[kid]
    }

    private fun fetchKeys(jwksUrl: String): CachedJwks
    {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(jwksUrl))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200)
        {
            logger.error("JWKS fetch failed: status={} url={}", response.statusCode(), jwksUrl)
            throw RuntimeException("Failed to fetch JWKS")
        }

        val payload = OAuthJsonParser.parseJsonToMap(response.body())
        val keys = payload["keys"] as? List<*>
            ?: throw RuntimeException("Invalid JWKS payload: missing keys")

        val byKid = mutableMapOf<String, RSAPublicKey>()

        keys.forEach { raw ->
            val item = raw as? Map<*, *> ?: return@forEach
            val kty = item["kty"] as? String ?: return@forEach
            val kid = item["kid"] as? String ?: return@forEach
            val n = item["n"] as? String ?: return@forEach
            val e = item["e"] as? String ?: return@forEach

            if (kty != "RSA") return@forEach

            runCatching { buildRsaPublicKey(n, e) }
                .onSuccess { byKid[kid] = it }
                .onFailure { ex -> logger.warn("Failed building RSA key for kid={} err={}", kid, ex.message) }
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
        val keySpec = RSAPublicKeySpec(modulus, exponent)
        val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
        val key: PublicKey = keyFactory.generatePublic(keySpec)
        return key as RSAPublicKey
    }
}

private data class CachedJwks(
    val loadedAtEpochSeconds: Long,
    val keysByKid: Map<String, RSAPublicKey>,
)

