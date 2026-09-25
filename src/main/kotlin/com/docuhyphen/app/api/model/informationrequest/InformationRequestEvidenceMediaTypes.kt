package com.docuhyphen.app.api.model.informationrequest

import org.apache.tika.Tika
import java.io.File

object InformationRequestEvidenceMediaTypes
{
    private val detector = Tika()

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
}
