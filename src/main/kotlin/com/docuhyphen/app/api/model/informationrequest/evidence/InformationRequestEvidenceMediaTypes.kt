package com.docuhyphen.app.api.model.informationrequest.evidence

import org.apache.tika.Tika
import org.apache.tika.mime.MediaType
import org.apache.tika.mime.MimeTypes
import java.io.File

object InformationRequestEvidenceMediaTypes
{
    private val detector = Tika()
    private val registry = MimeTypes.getDefaultMimeTypes().mediaTypeRegistry

    private val TEXT_FAMILIES = setOf("text/plain", "application/xml")

    private val INLINE_SAFE = setOf(
        "application/pdf",
        "image/png",
        "image/jpeg",
        "image/gif",
        "image/webp",
    )

    fun detect(file: File): String =
        file.inputStream().buffered().use { input -> detector.detect(input) }

    fun isInlineSafe(mediaType: String): Boolean = canonical(mediaType) in INLINE_SAFE

    fun canonical(mediaType: String): String = mediaType.substringBefore(';').trim().lowercase()

    fun mediaTypeOrNull(value: String): String? =
        value.trim().lowercase().takeIf { MEDIA_TYPE.matches(it) && MediaType.parse(it) != null }

    fun judgedType(detected: String?, declaredMediaType: String?, declaredFileName: String?): String? {
        val found = detected?.let(::normalized) ?: return null
        if (found !in TEXT_FAMILIES) return found
        return listOfNotNull(declared(declaredMediaType), named(declaredFileName))
            .firstOrNull { claim -> claim != found && specializes(claim, found) }
            ?: found
    }

    fun contradictsDeclaration(detected: String?, declaredMediaType: String?, declaredFileName: String?): Boolean {
        val found = detected?.let(::normalized) ?: return false
        val declared = declared(declaredMediaType) ?: return false
        if (declared == found || (found in TEXT_FAMILIES && specializes(declared, found))) return false
        val named = named(declaredFileName)
        return !(found in TEXT_FAMILIES && named != null && named != found && specializes(named, found))
    }

    fun accepts(
        accepted: Collection<String>,
        detected: String?,
        declaredMediaType: String?,
        declaredFileName: String?
    ): Boolean {
        val judged = judgedType(detected, declaredMediaType, declaredFileName) ?: return false
        return accepted.map(::normalized)
            .any { acceptedType -> judged == acceptedType || specializes(judged, acceptedType) }
    }

    private fun declared(mediaType: String?): String? = mediaType?.let(::normalized)?.takeIf { it != UNDECLARED }

    private fun named(fileName: String?): String? =
        fileName?.takeIf { it.isNotBlank() }?.let { name -> normalized(detector.detect(name)) }
            ?.takeIf { it != UNDECLARED }

    private fun specializes(type: String, base: String): Boolean {
        val specific = MediaType.parse(type) ?: return false
        val general = MediaType.parse(base) ?: return false
        return registry.isSpecializationOf(specific, general)
    }

    private fun normalized(mediaType: String): String {
        val canonical = canonical(mediaType)
        return MediaType.parse(canonical)?.let { parsed -> registry.normalize(parsed).toString() } ?: canonical
    }

    private const val UNDECLARED = "application/octet-stream"

    private val MEDIA_TYPE = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,126}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}$")
}
