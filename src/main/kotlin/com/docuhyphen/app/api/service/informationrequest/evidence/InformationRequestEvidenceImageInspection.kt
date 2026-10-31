package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceInspectionFacts
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.ceil
import kotlin.math.sqrt

class InformationRequestEvidenceImageInspection(
    private val maximumProgressivePixels: Long = MAX_PROGRESSIVE_PIXELS,
) {
    fun recognizes(detected: String): Boolean = detected in DECODED_TYPES

    fun inspect(file: File, detected: String): InformationRequestEvidenceInspectionFacts =
        InformationRequestEvidenceInspectionFacts(
            detected,
            pageCount = null,
            encrypted = false,
            corrupt = !decodes(file)
        )

    private fun decodes(file: File): Boolean =
        try {
            ImageIO.createImageInputStream(file).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return false
                try {
                    var warned = false
                    reader.addIIOReadWarningListener { _, _ -> warned = true }
                    reader.setInput(input, true, true)
                    val pixels = reader.getWidth(0).toLong() * reader.getHeight(0)
                    if (pixels <= 0 || pixels > MAX_INSPECTED_PIXELS) return false
                    if (pixels > maximumProgressivePixels && progressive(reader)) return false
                    reader.read(0, subsampled(reader, pixels))
                    !warned
                } finally {
                    reader.dispose()
                }
            }
        } catch (_: IOException) {
            false
        } catch (_: RuntimeException) {
            false
        }

    private fun progressive(reader: ImageReader): Boolean {
        if (!reader.formatName.equals(JPEG_FORMAT, ignoreCase = true)) return false
        val tree = reader.getImageMetadata(0)?.getAsTree(JPEG_METADATA) as? IIOMetadataNode ?: return true
        val frames = tree.getElementsByTagName(FRAME_ELEMENT)
        return (0 until frames.length).any { (frames.item(it) as IIOMetadataNode).getAttribute(PROCESS_ATTRIBUTE) == PROGRESSIVE_PROCESS }
    }

    private fun subsampled(reader: ImageReader, pixels: Long): ImageReadParam =
        reader.defaultReadParam.apply {
            val step = ceil(sqrt(pixels.toDouble() / MAX_DECODED_PIXELS)).toInt().coerceAtLeast(1)
            setSourceSubsampling(step, step, 0, 0)
        }

    private companion object {
        val DECODED_TYPES = setOf("image/png", "image/jpeg", "image/gif")
        const val MAX_INSPECTED_PIXELS = 100_000_000L
        const val MAX_PROGRESSIVE_PIXELS = 25_000_000L
        const val MAX_DECODED_PIXELS = 4_000_000.0
        const val JPEG_FORMAT = "jpeg"
        const val JPEG_METADATA = "javax_imageio_jpeg_image_1.0"
        const val FRAME_ELEMENT = "sof"
        const val PROCESS_ATTRIBUTE = "process"
        const val PROGRESSIVE_PROCESS = "2"
    }
}
