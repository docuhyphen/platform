package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.DocumentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Typed view of a [com.docuhyphen.app.api.model.entity.Share]'s `constraints_json` blob.
 *
 * Replaces the old substring-matching hack in [DefaultAuthorizationService]. Two parse
 * modes:
 *  - [parse]  — lenient, never throws; used on the *read* / authorize path where a
 *               malformed blob must degrade to "permissive defaults" rather than fail a
 *               live request.
 *  - [parseStrict] — throws on malformed JSON; used on the *write* path
 *               ([com.docuhyphen.app.api.service.sharingsession.SessionAccessManagementService])
 *               so we reject garbage at the source and store a canonical form.
 *
 * Field semantics (see plan 01-participant-limited-access.md):
 *  - [canDownload] tri-state. `null` = "inherit from role" (so VIEWER/PARTICIPANT, whose
 *    base role carries no DOCUMENT_DOWNLOAD, stay download-less unless explicitly opted in
 *    with `true`; EDITOR keeps download unless explicitly `false`). See [adjustCapabilities].
 *  - [canReshare] tri-state. Only an explicit `false` strips SESSION_SHARE; `null` leaves the
 *    base untouched so owners/managers keep their reshare power.
 *  - [watermark] / [maxViews] are non-blocking *obligations* surfaced on the [Decision].
 *  - [requireMfa] denies when the context's MFA isn't satisfied.
 */
@Serializable
data class ShareConstraints(
    @SerialName("can_download") val canDownload: Boolean? = null,
    @SerialName("can_reshare") val canReshare: Boolean? = null,
    val watermark: Boolean = false,
    @SerialName("max_views") val maxViews: Int? = null,
    @SerialName("require_mfa") val requireMfa: Boolean = false,
    @SerialName("allow_document_upload") val allowDocumentUpload: Boolean? = null,
    @SerialName("allow_document_addition") val allowDocumentAddition: Boolean? = null,
    @SerialName("allow_document_update") val allowDocumentUpdate: Boolean? = null,
    @SerialName("allow_document_deletion") val allowDocumentDeletion: Boolean? = null,
    @SerialName("allowed_download_formats") val allowedDownloadFormats: List<String>? = null,
)
{
    /**
     * Apply this share's constraints to a role's base capability set.
     *
     * download: opt-in / opt-out, defaulting to whatever the base role already grants;
     *           reshare: only an explicit `false` removes SESSION_SHARE.
     */
    fun adjustCapabilities(base: Set<Capability>): Set<Capability>
    {
        val caps = base.toMutableSet()

        val effectiveDownload = canDownload ?: (Capability.DOCUMENT_DOWNLOAD in base)
        if (effectiveDownload) caps += Capability.DOCUMENT_DOWNLOAD else caps -= Capability.DOCUMENT_DOWNLOAD

        if (canReshare == false) caps -= Capability.SESSION_SHARE

        // Document write permissions: when any of upload/addition/update is explicitly true
        // in the constraints, grant DOCUMENT_WRITE even if the base role (e.g. VIEWER) lacks it.
        if (allowDocumentUpload == true || allowDocumentAddition == true || allowDocumentUpdate == true)
        {
            caps += Capability.DOCUMENT_WRITE
        }

        // Document deletion: grant DOCUMENT_DELETE when explicitly allowed.
        if (allowDocumentDeletion == true)
        {
            caps += Capability.DOCUMENT_DELETE
        }

        return caps
    }

    /** Reject self-contradictory / nonsensical constraints at write time. */
    fun validate()
    {
        if (maxViews != null && maxViews <= 0)
        {
            throw IllegalArgumentException("max_views must be a positive integer")
        }
        if (allowedDownloadFormats != null)
        {
            for (format in allowedDownloadFormats)
            {
                runCatching { DocumentType.valueOf(format) }.getOrElse {
                    throw IllegalArgumentException("Invalid download format: '$format'. Must be one of ${DocumentType.entries.map { it.name }}")
                }
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

        /** Lenient parse for the authorize/read path — malformed input ⇒ [PERMISSIVE]. */
        fun parse(json: String?): ShareConstraints
        {
            if (json.isNullOrBlank()) return PERMISSIVE
            return runCatching { JSON.decodeFromString<ShareConstraints>(json) }
                .getOrElse {
                    logger.warn("Ignoring malformed constraints_json ({}): {}", it.javaClass.simpleName, it.message)
                    PERMISSIVE
                }
        }

        /** Strict parse for the write path — throws on malformed JSON. */
        fun parseStrict(json: String): ShareConstraints = JSON.decodeFromString(json)

        /**
         * Validate + canonicalise a constraints blob for storage. Returns null when the
         * blob is blank or reduces to the permissive default (so we don't persist `{}`).
         * Throws [IllegalArgumentException] on malformed or contradictory input.
         */
        fun normalizeForStorage(json: String?): String?
        {
            if (json.isNullOrBlank()) return null
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
    }
}
