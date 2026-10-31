package com.docuhyphen.app.api.service.informationrequest.evidence

import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.filter.DecodeOptions
import org.apache.pdfbox.filter.DecodeResult
import org.apache.pdfbox.filter.Filter
import org.apache.pdfbox.filter.FilterFactory
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

object InformationRequestEvidencePdfDecoding {
    private const val REGISTRY_FIELD = "filters"

    private val IMAGE_FILTERS = setOf(
        COSName.DCT_DECODE,
        COSName.DCT_DECODE_ABBREVIATION,
        COSName.JPX_DECODE,
        COSName.JBIG2_DECODE,
        COSName.CCITTFAX_DECODE,
        COSName.CCITTFAX_DECODE_ABBREVIATION,
    )

    private val current = ThreadLocal<InformationRequestEvidenceDocumentUsage>()

    private val registryMetered: Unit by lazy { meterRegisteredFilters() }

    fun <T> metered(usage: InformationRequestEvidenceDocumentUsage, inspection: () -> T): T {
        registryMetered
        current.set(usage)
        try {
            return inspection()
        } finally {
            current.remove()
        }
    }

    private fun meterRegisteredFilters() {
        val registry = try {
            val field = FilterFactory::class.java.getDeclaredField(REGISTRY_FIELD)
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(FilterFactory.INSTANCE) as MutableMap<COSName, Filter>
        } catch (failure: ReflectiveOperationException) {
            throw IllegalStateException("PDF decoding filters cannot be metered for evidence inspection", failure)
        }
        val metered = IdentityHashMap<Filter, Filter>()
        registry.entries.forEach { entry ->
            val registered = entry.value
            if (registered !is MeteredFilter) {
                entry.setValue(metered.getOrPut(registered) { MeteredFilter(registered, entry.key in IMAGE_FILTERS) })
            }
        }
    }

    private class MeteredFilter(private val delegate: Filter, private val decodesImages: Boolean) : Filter() {
        override fun decode(
            encoded: InputStream,
            decoded: OutputStream,
            parameters: COSDictionary?,
            index: Int
        ): DecodeResult =
            delegate.decode(encoded, meteredOutput(decoded), parameters, index)

        override fun decode(
            encoded: InputStream,
            decoded: OutputStream,
            parameters: COSDictionary?,
            index: Int,
            options: DecodeOptions?,
        ): DecodeResult = delegate.decode(encoded, meteredOutput(decoded), parameters, index, options)

        override fun encode(input: InputStream, encoded: OutputStream, parameters: COSDictionary?) {
            delegate.encode(input, encoded, parameters, 0)
        }

        private fun meteredOutput(decoded: OutputStream): OutputStream {
            val usage = current.get() ?: return decoded
            if (decodesImages) {
                usage.refuseImageDecoding()
            }
            return MeteredOutputStream(decoded, usage)
        }
    }

    private class MeteredOutputStream(
        decoded: OutputStream,
        private val usage: InformationRequestEvidenceDocumentUsage,
    ) : FilterOutputStream(decoded) {
        override fun write(b: Int) {
            usage.chargeDecodedBytes(1)
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            usage.chargeDecodedBytes(len.toLong())
            out.write(b, off, len)
        }
    }
}
