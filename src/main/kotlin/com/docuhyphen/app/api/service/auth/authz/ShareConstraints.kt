package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.DocumentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Typed view of a [com.docuhyphen.app.api.model.entity.Share]'s `constraints_json` blob.
 *
 * Replaces the old substring-matching hack in [DefaultAuthorizationService]. Two parse
 * modes:
 *  - [parse], lenient, never throws; used on the *read* / authorize path where a
 *               malformed blob must degrade to "permissive defaults" rather than fail a
 *               live request.
 *  - [parseStrict], throws on malformed JSON; used on the *write* path
 *               ([com.docuhyphen.app.api.service.exchange.ExchangeAccessManagementService])
 *               so we reject garbage at the source and store a canonical form.
 *
 * Field semantics:
 *  - [canDownload] tri-state. `null` = "inherit from role". See [adjustCapabilities].
 *  - [canReshare] tri-state. Only an explicit `false` strips EXCHANGE_SHARE.
 *  - [watermark] non-blocking obligation surfaced on the [Decision].
 *  - [requireMfa] denies when the context's MFA is not satisfied.
 *  - [allowedIpRanges] CIDR blocks (IPv4 or IPv6). When non-empty, denies access if the
 *    client IP is absent or not in any listed range. Null / empty list means no restriction.
 *  - [allowedDownloadFormats] non-blocking obligation returned in [ShareObligations] for the
 *    download layer to enforce. Does not shape the DOCUMENT_DOWNLOAD capability.
 *
 * `max_views` has been removed from the supported contract. Server-side transactional view
 * counting is not implemented; the field is rejected at write time to prevent silent
 * no-enforcement. See Phase 7 of the pre-fields authorization hardening plan.
 */
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
    /**
     * Apply this share's constraints to a role's base capability set.
     *
     * Download: opt-in / opt-out, defaulting to whatever the base role already grants.
     * Reshare: only an explicit `false` removes EXCHANGE_SHARE.
     * allowedIpRanges and allowedDownloadFormats are enforced as constraints / obligations,
     * not by capability shaping.
     */
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

    /** Reject self-contradictory or unsupported constraints at write time. */
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

    /** Canonical JSON for storage (default-valued fields are omitted). */
    fun toCanonicalJson(): String = JSON.encodeToString(this)

    companion object
    {
        private val logger = LoggerFactory.getLogger(ShareConstraints::class.java)

        private val JSON = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
        }

        /** Fully permissive baseline (no constraints set). */
        val PERMISSIVE = ShareConstraints()

        /**
         * Fail-closed parse for the authorize/read path.
         * Returns null when the JSON is malformed — callers must treat null as a deny.
         * Blank/null input returns [PERMISSIVE] (no constraints set).
         */
        fun parse(json: String?): ShareConstraints?
        {
            if (json.isNullOrBlank()) return PERMISSIVE
            return runCatching { JSON.decodeFromString<ShareConstraints>(json) }
                .getOrElse {
                    logger.warn("Malformed constraints_json denies access ({}): {}", it.javaClass.simpleName, it.message)
                    null
                }
        }

        /** Strict parse for the write path, throws on malformed JSON. */
        fun parseStrict(json: String): ShareConstraints = JSON.decodeFromString(json)

        /**
         * Validate + canonicalise a constraints blob for storage. Returns null when the
         * blob is blank or reduces to the permissive default (so we don't persist `{}`).
         * Throws [IllegalArgumentException] on malformed or contradictory input.
         *
         * `max_views` is no longer supported. Any input specifying it is rejected.
         */
        fun normalizeForStorage(json: String?): String?
        {
            if (json.isNullOrBlank()) return null
            // Reject legacy max_views key before parsing to give a clear error.
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

        /**
         * Returns true when [clientIp] is in at least one of [ranges].
         * An empty range list means no restriction (returns true).
         * A null IP with a non-empty list fails closed (returns false).
         */
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
