package com.docuhyphen.app.api.service.informationrequest.evidence

import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdfwriter.compress.CompressParameters
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.DeflaterOutputStream

object InformationRequestEvidencePdfFixtures {
    private const val PAGE_OBJECTS = 3

    fun expandingCrossReferenceStream(trailingDecodedBytes: Int): ByteArray {
        val pdf = ByteArrayOutputStream()
        pdf.ascii("%PDF-1.7\n")
        val offsets = pageObjects(pdf)
        val streamOffset = pdf.size().toLong()
        val entries = crossReferenceEntries(offsets + streamOffset)
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { stream ->
            stream.write(entries)
            val block = ByteArray(1024 * 1024)
            var remaining = trailingDecodedBytes
            while (remaining > 0) {
                val length = minOf(remaining, block.size)
                stream.write(block, 0, length)
                remaining -= length
            }
        }
        pdf.ascii("${offsets.size + 1} 0 obj\n<< /Type /XRef /Size ${offsets.size + 2} /W [1 4 2] /Root 1 0 R ")
        pdf.ascii("/Filter /FlateDecode /Length ${compressed.size()} >>\nstream\n")
        compressed.writeTo(pdf)
        pdf.ascii("\nendstream\nendobj\nstartxref\n$streamOffset\n%%EOF\n")
        return pdf.toByteArray()
    }

    fun uncompressedCrossReferenceStream(extraObjects: Int): ByteArray {
        val pdf = ByteArrayOutputStream()
        pdf.ascii("%PDF-1.7\n")
        val offsets = pageObjects(pdf) + nullObjects(pdf, PAGE_OBJECTS + 1, extraObjects)
        val streamOffset = pdf.size().toLong()
        val entries = crossReferenceEntries(offsets + streamOffset)
        pdf.ascii("${offsets.size + 1} 0 obj\n<< /Type /XRef /Size ${offsets.size + 2} /W [1 4 2] /Root 1 0 R ")
        pdf.ascii("/Length ${entries.size} >>\nstream\n")
        pdf.write(entries)
        pdf.ascii("\nendstream\nendobj\nstartxref\n$streamOffset\n%%EOF\n")
        return pdf.toByteArray()
    }

    fun crossReferenceTable(extraObjects: Int): ByteArray {
        val pdf = ByteArrayOutputStream()
        pdf.ascii("%PDF-1.4\n")
        val offsets = pageObjects(pdf) + nullObjects(pdf, PAGE_OBJECTS + 1, extraObjects)
        val tableOffset = pdf.size().toLong()
        pdf.ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { offset -> pdf.ascii("%010d 00000 n \n".format(offset)) }
        pdf.ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$tableOffset\n%%EOF\n")
        return pdf.toByteArray()
    }

    fun incrementallyUpdated(updates: Int): ByteArray {
        val pdf = ByteArrayOutputStream()
        pdf.ascii("%PDF-1.4\n")
        val offsets = pageObjects(pdf)
        var previous = pdf.size().toLong()
        pdf.ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { offset -> pdf.ascii("%010d 00000 n \n".format(offset)) }
        pdf.ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$previous\n%%EOF\n")
        repeat(updates) {
            val catalog = pdf.size().toLong()
            pdf.ascii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            val section = pdf.size().toLong()
            pdf.ascii("xref\n1 1\n%010d 00000 n \n".format(catalog))
            pdf.ascii("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R /Prev $previous >>\nstartxref\n$section\n%%EOF\n")
            previous = section
        }
        return pdf.toByteArray()
    }

    fun compressed(
        file: File,
        pages: Int,
        objectStreamSize: Int = CompressParameters.DEFAULT_OBJECT_STREAM_SIZE
    ): File {
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.save(file, CompressParameters(objectStreamSize))
        }
        return file
    }

    fun declaringPageCount(file: File, pages: Int, declared: Int): File {
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.pages.cosObject.setInt(COSName.COUNT, declared)
            document.save(file)
        }
        return file
    }

    fun ownerRestrictedCompressed(file: File, pages: Int): File {
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.protect(
                StandardProtectionPolicy(
                    "owner-secret",
                    "",
                    AccessPermission()
                ).apply { encryptionKeyLength = 256 })
            document.save(file, CompressParameters.DEFAULT_COMPRESSION)
        }
        return file
    }

    fun passwordProtected(file: File): File {
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.protect(
                StandardProtectionPolicy(
                    "owner-secret",
                    "user-secret",
                    AccessPermission()
                ).apply { encryptionKeyLength = 128 })
            document.save(file)
        }
        return file
    }

    private fun pageObjects(pdf: ByteArrayOutputStream): List<Long> {
        val offsets = mutableListOf<Long>()
        offsets += pdf.size().toLong()
        pdf.ascii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        offsets += pdf.size().toLong()
        pdf.ascii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        offsets += pdf.size().toLong()
        pdf.ascii("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>\nendobj\n")
        return offsets
    }

    private fun nullObjects(pdf: ByteArrayOutputStream, firstNumber: Int, count: Int): List<Long> =
        (0 until count).map { index ->
            val offset = pdf.size().toLong()
            pdf.ascii("${firstNumber + index} 0 obj\nnull\nendobj\n")
            offset
        }

    private fun crossReferenceEntries(offsets: List<Long>): ByteArray {
        val entries = ByteBuffer.allocate((offsets.size + 1) * 7)
        entries.put(0).putInt(0).putShort(0xFFFF.toShort())
        offsets.forEach { offset -> entries.put(1).putInt(offset.toInt()).putShort(0) }
        return entries.array()
    }

    private fun ByteArrayOutputStream.ascii(text: String) {
        write(text.toByteArray(Charsets.US_ASCII))
    }
}
