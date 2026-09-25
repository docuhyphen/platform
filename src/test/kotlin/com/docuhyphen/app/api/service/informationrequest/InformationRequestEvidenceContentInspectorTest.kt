package com.docuhyphen.app.api.service.informationrequest

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class InformationRequestEvidenceContentInspectorTest
{
    @TempDir
    lateinit var directory: Path

    private val inspector = InformationRequestEvidenceContentInspector()

    @Test
    fun `a readable document reports its detected type and page count`()
    {
        val facts = inspector.inspect(pdf("record.pdf", pages = 3))

        assertEquals("application/pdf", facts.detectedMediaType)
        assertEquals(3, facts.pageCount)
        assertFalse(facts.encrypted)
        assertFalse(facts.corrupt)
    }

    @Test
    fun `detection reads the bytes, never the declared file name`()
    {
        val facts = inspector.inspect(pdf("record.png", pages = 1))

        assertEquals("application/pdf", facts.detectedMediaType)
    }

    @Test
    fun `a document that needs a password to open is reported as encrypted with no page count`()
    {
        val file = directory.resolve("protected.pdf").toFile()
        PDDocument().use { document ->
            document.addPage(PDPage())
            document.protect(StandardProtectionPolicy("owner-secret", "user-secret", AccessPermission()).apply {
                encryptionKeyLength = 128
            })
            document.save(file)
        }

        val facts = inspector.inspect(file)

        assertTrue(facts.encrypted)
        assertFalse(facts.corrupt)
        assertNull(facts.pageCount)
    }

    @Test
    fun `a document that cannot be parsed is reported as corrupt`()
    {
        val file = directory.resolve("broken.pdf").toFile().apply { writeText("%PDF-1.7\nthis is not a document body") }

        val facts = inspector.inspect(file)

        assertEquals("application/pdf", facts.detectedMediaType)
        assertTrue(facts.corrupt)
        assertNull(facts.pageCount)
    }

    @Test
    fun `an image reports its detected type and no page count`()
    {
        val file = directory.resolve("image.bin").toFile().apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(24))
        }

        val facts = inspector.inspect(file)

        assertEquals("image/png", facts.detectedMediaType)
        assertNull(facts.pageCount)
        assertFalse(facts.corrupt)
    }

    private fun pdf(name: String, pages: Int): File
    {
        val file = directory.resolve(name).toFile()
        PDDocument().use { document ->
            repeat(pages) { document.addPage(PDPage()) }
            document.save(file)
        }
        return file
    }
}
