package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.net.InetAddress

@ApplicationScoped
class ClientIpResolver @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ClientIpResolver::class.java)
        const val UNKNOWN_IP = "0.0.0.0"
    }

    private val trustedRanges: List<CidrRange> by lazy {
        configurationService.getTrustedProxyCidrs().mapNotNull { raw ->
            val parsed = CidrRange.parse(raw)
            if (parsed == null)
            {
                logger.warn("Ignoring malformed trusted proxy CIDR: {}", raw)
            }
            parsed
        }
    }

    fun resolve(request: io.vertx.core.http.HttpServerRequest): String
    {
        return resolve(
            peerAddress = request.remoteAddress()?.host(),
            forwardedForHeader = request.getHeader("X-Forwarded-For"),
            realIpHeader = request.getHeader("X-Real-IP"),
        )
    }

    fun resolve(peerAddress: String?, forwardedForHeader: String?, realIpHeader: String?): String
    {
        val peer = normalize(peerAddress) ?: UNKNOWN_IP

        if (!configurationService.isForwardedHeadersEnabled() || !isTrustedProxy(peer))
        {
            return peer
        }

        val forwardedChain = forwardedForHeader
            ?.split(',')
            ?.mapNotNull { normalize(it) }
            .orEmpty()

        forwardedChain.lastOrNull { !isTrustedProxy(it) }?.let { return it }

        normalize(realIpHeader)?.let { return it }

        return peer
    }

    fun isTrustedProxy(address: String): Boolean = trustedRanges.any { it.contains(address) }

    private fun normalize(raw: String?): String?
    {
        val value = raw?.trim()?.removeSurrounding("\"") ?: return null
        if (value.isBlank() || value.equals("unknown", ignoreCase = true))
        {
            return null
        }

        val withoutBrackets = if (value.startsWith("[")) value.substringAfter('[').substringBefore(']') else value
        val hostOnly =
            if (withoutBrackets.count { it == ':' } == 1) withoutBrackets.substringBefore(':') else withoutBrackets
        val zoneStripped = hostOnly.substringBefore('%')

        return zoneStripped.takeIf { IpLiterals.toBytes(it) != null }
    }
}

internal object IpLiterals
{
    private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$")
    private val IPV6_CHARS = Regex("^[0-9A-Fa-f:.]+$")

    fun toBytes(value: String): ByteArray?
    {
        if (value.isBlank()) return null

        val isLiteral = when
        {
            value.contains(':') -> IPV6_CHARS.matches(value) && value.count { it == ':' } >= 2
            else -> IPV4.matches(value)
        }
        if (!isLiteral) return null

        return runCatching { InetAddress.getByName(value).address }.getOrNull()
    }
}

internal class CidrRange private constructor(
    private val networkBytes: ByteArray,
    private val prefixLength: Int,
)
{
    companion object
    {
        fun parse(raw: String): CidrRange?
        {
            val value = raw.trim()
            if (value.isBlank()) return null

            val addressPart = value.substringBefore('/')
            val prefixPart = value.substringAfter('/', "")

            val networkBytes = IpLiterals.toBytes(addressPart) ?: return null
            val addressBits = networkBytes.size * 8
            val prefixLength = if (prefixPart.isBlank()) addressBits else prefixPart.toIntOrNull() ?: return null

            if (prefixLength < 0 || prefixLength > addressBits) return null

            return CidrRange(networkBytes, prefixLength)
        }
    }

    fun contains(candidate: String): Boolean
    {
        val candidateBytes = IpLiterals.toBytes(candidate) ?: return false
        if (candidateBytes.size != networkBytes.size) return false

        var remainingBits = prefixLength
        var index = 0

        while (remainingBits >= 8)
        {
            if (candidateBytes[index] != networkBytes[index]) return false
            remainingBits -= 8
            index++
        }

        if (remainingBits == 0) return true

        val mask = (0xFF shl (8 - remainingBits)) and 0xFF
        return (candidateBytes[index].toInt() and mask) == (networkBytes[index].toInt() and mask)
    }
}

