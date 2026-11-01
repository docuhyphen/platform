package com.docuhyphen.app.api.model.auth.authz

import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.service.auth.authz.Capability
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.UnknownHostException

@Serializable
data class ShareConstraints(
    @SerialName("can_download") val canDownload: Boolean? = null,
    @SerialName("can_reshare") val canReshare: Boolean? = null,
    val watermark: Boolean = false,
    @SerialName("require_mfa") val requireMfa: Boolean = false,
    @SerialName("allow_document_upload") val allowDocumentUpload: Boolean? = null,
    @SerialName("allow_document_addition") val allowDocumentAddition: Boolean? = null,
    @SerialName("allow_document_update") val allowDocumentUpdate: Boolean? = null,
    @SerialName("allow_document_deletion") val allowDocumentDeletion: Boolean? = null,
    @SerialName("allowed_download_formats") val allowedDownloadFormats: List<String>? = null,
    @SerialName("allowed_ip_ranges") val allowedIpRanges: List<String>? = null,
)
{
    fun adjustCapabilities(base: Set<Capability>): Set<Capability>
    {
        val caps = base.toMutableSet()

        val effectiveDownload = canDownload ?: (Capability.DOCUMENT_DOWNLOAD in base)
        if (effectiveDownload) caps += Capability.DOCUMENT_DOWNLOAD else caps -= Capability.DOCUMENT_DOWNLOAD

        if (canReshare == false) caps -= Capability.EXCHANGE_SHARE

        if (allowDocumentUpload == true || allowDocumentAddition == true || allowDocumentUpdate == true)
        {
            caps += Capability.DOCUMENT_WRITE
        }

        if (allowDocumentDeletion == true)
        {
            caps += Capability.DOCUMENT_DELETE
        }

        return caps
    }

    fun validate()
    {
        if (allowedDownloadFormats != null)
        {
            for (format in allowedDownloadFormats)
            {
                runCatching { DocumentType.valueOf(format) }.getOrElse {
                    throw IllegalArgumentException("Invalid download format: '$format'. Must be one of ${DocumentType.entries.map { it.name }}")
                }
            }
        }
        if (allowedIpRanges != null)
        {
            for (range in allowedIpRanges)
            {
                validateIpOrCidr(range)
            }
        }
    }

    fun toCanonicalJson(): String = JSON.encodeToString(this)

    companion object
    {
        private val logger = LoggerFactory.getLogger(ShareConstraints::class.java)

        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
        }

        val PERMISSIVE = ShareConstraints()

        fun parse(json: String?): ShareConstraints?
        {
            if (json.isNullOrBlank()) return PERMISSIVE
            return runCatching { JSON.decodeFromString<ShareConstraints>(json) }
                .getOrElse {
                    logger.warn(
                        "Malformed constraints_json denies access ({}): {}",
                        it.javaClass.simpleName,
                        it.message
                    )
                    null
                }
        }

        fun parseStrict(json: String): ShareConstraints = JSON.decodeFromString(json)

        fun normalizeForStorage(json: String?): String?
        {
            if (json.isNullOrBlank()) return null
            if (json.contains("max_views"))
            {
                throw IllegalArgumentException(
                    "max_views is not a supported constraint. " +
                            "Server-side view counting is not implemented. Remove max_views from your request."
                )
            }
            val parsed = try
            {
                parseStrict(json)
            }
            catch (e: Exception)
            {
                throw IllegalArgumentException("Invalid constraints JSON: ${e.message}")
            }
            parsed.validate()
            if (parsed == PERMISSIVE) return null
            return parsed.toCanonicalJson()
        }

        fun isIpAllowed(clientIp: String?, ranges: List<String>): Boolean
        {
            if (ranges.isEmpty()) return true
            val ip = clientIp ?: return false
            return ranges.any { isIpInCidr(ip, it) }
        }

        private fun isIpInCidr(clientIp: String, cidr: String): Boolean
        {
            return try
            {
                if (!cidr.contains('/'))
                {
                    return InetAddress.getByName(clientIp) == InetAddress.getByName(cidr)
                }
                val parts = cidr.split('/', limit = 2)
                val networkAddr = InetAddress.getByName(parts[0])
                val prefixLen = parts[1].toInt()
                val clientAddr = InetAddress.getByName(clientIp)

                val clientBytes = clientAddr.address
                val networkBytes = networkAddr.address
                if (clientBytes.size != networkBytes.size) return false

                val fullBytes = prefixLen / 8
                val remainingBits = prefixLen % 8
                for (i in 0 until fullBytes)
                {
                    if (clientBytes[i] != networkBytes[i]) return false
                }
                if (remainingBits > 0 && fullBytes < clientBytes.size)
                {
                    val mask = (0xFF shl (8 - remainingBits)).toByte()
                    if ((clientBytes[fullBytes].toInt() and mask.toInt() and 0xFF) !=
                        (networkBytes[fullBytes].toInt() and mask.toInt() and 0xFF)
                    ) return false
                }
                true
            }
            catch (_: Exception)
            {
                false
            }
        }

        private fun validateIpOrCidr(s: String)
        {
            try
            {
                if (s.contains('/'))
                {
                    val parts = s.split('/', limit = 2)
                    if (parts.size != 2) throw IllegalArgumentException("Invalid CIDR: '$s'")
                    val addr = InetAddress.getByName(parts[0])
                    val prefix = parts[1].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid prefix length in '$s'")
                    val maxPrefix = if (addr.address.size == 4) 32 else 128
                    if (prefix < 0 || prefix > maxPrefix)
                    {
                        throw IllegalArgumentException("Prefix length $prefix out of range [0..$maxPrefix] in '$s'")
                    }
                }
                else
                {
                    InetAddress.getByName(s)
                }
            }
            catch (e: UnknownHostException)
            {
                throw IllegalArgumentException("Invalid IP address or CIDR notation: '$s'")
            }
        }
    }
}
