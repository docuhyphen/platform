package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

data class DpopVerifyResult(val valid: Boolean, val jwkThumbprint: String? = null, val reason: String? = null)

/**
 * DPoP (RFC 9449) sender-constraint proof verification.
 *
 * On each protected request the client presents a `DPoP` header,  a JWS signed with the
 * private key whose public key thumbprint is bound to the access token via the `cnf.jkt`
 * claim. This service verifies the proof and returns the JWK thumbprint for the caller
 * to compare against the access token's `cnf.jkt`.
 *
 * Behind [ConfigurationService.isDpopEnabled]. When disabled, callers SHOULD treat absent
 * DPoP as acceptable (graceful rollout). When enabled, the filter rejects requests whose
 * DPoP proof is missing or invalid.
 */
@ApplicationScoped
class DpopValidationService @Inject constructor(
    private val configurationService: ConfigurationService,
    private val redis: Redis,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(DpopValidationService::class.java)
        private const val DPOP_JTI_PREFIX = "dpop_jti:"
        private const val MAX_PROOF_AGE_SECONDS = 60L
        private val base64UrlDecoder = Base64.getUrlDecoder()
    }

    fun verify(dpopHeader: String?, httpMethod: String, requestUrl: String): DpopVerifyResult
    {
        if (dpopHeader.isNullOrBlank()) return DpopVerifyResult(false, reason = "missing")

        val parts = dpopHeader.split('.')
        if (parts.size != 3) return DpopVerifyResult(false, reason = "format")

        val headerJson = String(base64UrlDecoder.decode(parts[0]))
        val payloadJson = String(base64UrlDecoder.decode(parts[1]))
        val header = parseFlatJsonObject(headerJson)
        val payload = parseFlatJsonObject(payloadJson)

        // 1. Header type
        if (header["typ"] != "dpop+jwt") return DpopVerifyResult(false, reason = "typ")
        val alg = header["alg"] ?: return DpopVerifyResult(false, reason = "alg")
        if (alg !in setOf("ES256")) return DpopVerifyResult(false, reason = "alg_not_supported")

        // 2. Extract JWK
        val jwkJson = header["jwk"] ?: return DpopVerifyResult(false, reason = "jwk")
        val jwk = parseFlatJsonObject(jwkJson)
        if (jwk["kty"] != "EC" || jwk["crv"] != "P-256") return DpopVerifyResult(false, reason = "jwk_alg")
        val x = jwk["x"] ?: return DpopVerifyResult(false, reason = "jwk_x")
        val y = jwk["y"] ?: return DpopVerifyResult(false, reason = "jwk_y")

        // 3. Signature check
        if (!verifyEs256Signature(parts, x, y)) return DpopVerifyResult(false, reason = "signature")

        // 4. Payload claims
        val htm = payload["htm"] ?: return DpopVerifyResult(false, reason = "htm")
        if (!htm.equals(httpMethod, ignoreCase = true)) return DpopVerifyResult(false, reason = "htm_mismatch")
        val htu = payload["htu"] ?: return DpopVerifyResult(false, reason = "htu")
        if (!sameOrigin(htu, requestUrl)) return DpopVerifyResult(false, reason = "htu_mismatch")

        val iat = payload["iat"]?.toLongOrNull() ?: return DpopVerifyResult(false, reason = "iat")
        val nowSeconds = System.currentTimeMillis() / 1000
        if (kotlin.math.abs(nowSeconds - iat) > MAX_PROOF_AGE_SECONDS) return DpopVerifyResult(false, reason = "stale")

        val jti = payload["jti"] ?: return DpopVerifyResult(false, reason = "jti")
        if (isReplayed(jti)) return DpopVerifyResult(false, reason = "replay")
        recordJti(jti)

        // 5. JWK thumbprint per RFC 7638 (canonical: crv, kty, x, y)
        val thumbprint = computeJwkThumbprint(jwk["kty"]!!, jwk["crv"]!!, x, y)
        return DpopVerifyResult(true, jwkThumbprint = thumbprint)
    }

    private fun verifyEs256Signature(parts: List<String>, x: String, y: String): Boolean
    {
        return try
        {
            val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)
            val signatureBytes = base64UrlDecoder.decode(parts[2])
            val derSig = jwsConcatToDer(signatureBytes)

            val xBytes = base64UrlDecoder.decode(x)
            val yBytes = base64UrlDecoder.decode(y)
            val ec = java.security.AlgorithmParameters.getInstance("EC").apply {
                init(ECGenParameterSpec("secp256r1"))
            }
            val params = ec.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
            val point = ECPoint(java.math.BigInteger(1, xBytes), java.math.BigInteger(1, yBytes))
            val publicKey = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))

            Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(signingInput)
            }.verify(derSig)
        }
        catch (e: Exception)
        {
            logger.warn("DPoP signature verification failed: ${e.message}")
            false
        }
    }

    /** Convert JWS concat-format (R||S) signature to DER for Java's ECDSA verifier. */
    private fun jwsConcatToDer(concat: ByteArray): ByteArray
    {
        require(concat.size % 2 == 0)
        val half = concat.size / 2
        val r = trimLeadingZeros(concat.copyOfRange(0, half))
        val s = trimLeadingZeros(concat.copyOfRange(half, concat.size))
        val rEnc = if (r[0].toInt() and 0x80 != 0) byteArrayOf(0) + r else r
        val sEnc = if (s[0].toInt() and 0x80 != 0) byteArrayOf(0) + s else s
        val total = 2 + rEnc.size + 2 + sEnc.size
        val out = mutableListOf<Byte>(0x30, total.toByte(), 0x02, rEnc.size.toByte())
        out.addAll(rEnc.toList())
        out.add(0x02); out.add(sEnc.size.toByte()); out.addAll(sEnc.toList())
        return out.toByteArray()
    }

    private fun trimLeadingZeros(b: ByteArray): ByteArray
    {
        var i = 0
        while (i < b.size - 1 && b[i] == 0.toByte()) i++
        return b.copyOfRange(i, b.size)
    }

    private fun sameOrigin(htu: String, requestUrl: String): Boolean
    {
        return try
        {
            val a = java.net.URI(htu)
            val b = java.net.URI(requestUrl)
            a.scheme == b.scheme && a.host == b.host && (a.port == b.port || (a.port == -1 && b.port == -1)) && a.path == b.path
        }
        catch (_: Exception) { false }
    }

    private fun isReplayed(jti: String): Boolean
    {
        val res = redis.send(Request.cmd(Command.EXISTS).arg("$DPOP_JTI_PREFIX$jti")).await().indefinitely()
        return (res?.toLong() ?: 0L) > 0L
    }

    private fun recordJti(jti: String)
    {
        redis.send(
            Request.cmd(Command.SET)
                .arg("$DPOP_JTI_PREFIX$jti")
                .arg("1")
                .arg("EX")
                .arg(MAX_PROOF_AGE_SECONDS.toString())
        ).await().indefinitely()
    }

    private fun computeJwkThumbprint(kty: String, crv: String, x: String, y: String): String
    {
        // RFC 7638: canonical JSON with keys in alphabetical order, no whitespace.
        val canonical = """{"crv":"$crv","kty":"$kty","x":"$x","y":"$y"}"""
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Tiny JSON object parser sufficient for flat header/payload + nested JWK string. */
    private fun parseFlatJsonObject(json: String): Map<String, String>
    {
        val result = mutableMapOf<String, String>()
        // Pull nested objects (jwk) out by tracking braces; everything else is "k":"v" pairs.
        val trimmed = json.trim().trim('{', '}')
        var depth = 0
        var start = 0
        val parts = mutableListOf<String>()
        for (i in trimmed.indices)
        {
            when (trimmed[i])
            {
                '{' -> depth++
                '}' -> depth--
                ',' -> if (depth == 0) { parts += trimmed.substring(start, i); start = i + 1 }
            }
        }
        parts += trimmed.substring(start)
        for (raw in parts)
        {
            val colon = raw.indexOf(':')
            if (colon < 0) continue
            val key = raw.substring(0, colon).trim().trim('"')
            val value = raw.substring(colon + 1).trim().let { if (it.startsWith('"') && it.endsWith('"')) it.trim('"') else it }
            result[key] = value
        }
        return result
    }
}
