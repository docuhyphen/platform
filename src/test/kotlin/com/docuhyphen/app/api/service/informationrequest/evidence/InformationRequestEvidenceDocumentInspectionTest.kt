package com.docuhyphen.app.api.service.informationrequest.evidence

import com.docuhyphen.app.api.model.informationrequest.evidence.InformationRequestEvidenceDocumentLimits
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.filter.FilterFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.imageio.ImageIO

class InformationRequestEvidenceDocumentInspectionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `a document whose cross-reference stream decodes past the decoding limit is reported as corrupt`() {
        val file =
            write("expanding.pdf", InformationRequestEvidencePdfFixtures.expandingCrossReferenceStream(4 * MEBIBYTE))

        val facts = inspection(maximumDecodedBytes = MEBIBYTE.toLong()).inspect(file, PDF)

        assertTrue(facts.corrupt)
        assertFalse(facts.encrypted)
        assertNull(facts.pageCount)
    }

    @Test
    fun `the same expanding document is readable within a larger decoding limit`() {
        val file =
            write("expanding.pdf", InformationRequestEvidencePdfFixtures.expandingCrossReferenceStream(4 * MEBIBYTE))

        val facts = inspection(maximumDecodedBytes = 8L * MEBIBYTE).inspect(file, PDF)

        assertFalse(facts.corrupt)
        assertEquals(1, facts.pageCount)
    }

    @Test
    fun `a small compressed document that expands to hundreds of mebibytes is refused under the default limits`() {
        val file =
            write("expanding.pdf", InformationRequestEvidencePdfFixtures.expandingCrossReferenceStream(256 * MEBIBYTE))
        assertTrue(file.length() < 512 * 1024)

        val facts = InformationRequestEvidenceDocumentInspection().inspect(file, PDF)

        assertTrue(facts.corrupt)
        assertNull(facts.pageCount)
    }

    @Test
    fun `a cross-reference table listing more entries than the limit is reported as corrupt`() {
        val file = write("listed.pdf", InformationRequestEvidencePdfFixtures.crossReferenceTable(extraObjects = 5_000))

        assertTrue(inspection(maximumCrossReferenceEntries = 1_000).inspect(file, PDF).corrupt)
        assertEquals(1, inspection(maximumCrossReferenceEntries = 10_000).inspect(file, PDF).pageCount)
    }

    @Test
    fun `an uncompressed cross-reference stream listing more entries than the limit is reported as corrupt`() {
        val file = write(
            "streamed.pdf",
            InformationRequestEvidencePdfFixtures.uncompressedCrossReferenceStream(extraObjects = 5_000)
        )

        assertTrue(inspection(maximumCrossReferenceEntries = 1_000).inspect(file, PDF).corrupt)
        assertEquals(1, inspection(maximumCrossReferenceEntries = 10_000).inspect(file, PDF).pageCount)
    }

    @Test
    fun `a document with more cross-reference sections than the limit is reported as corrupt`() {
        val file = write("updated.pdf", InformationRequestEvidencePdfFixtures.incrementallyUpdated(updates = 60))

        assertTrue(inspection(maximumCrossReferenceSections = 50).inspect(file, PDF).corrupt)
        assertEquals(1, inspection(maximumCrossReferenceSections = 100).inspect(file, PDF).pageCount)
    }

    @Test
    fun `an object stream declaring more objects than the limit is reported as corrupt`() {
        val file =
            InformationRequestEvidencePdfFixtures.compressed(file("grouped.pdf"), pages = 600, objectStreamSize = 500)

        assertTrue(inspection(maximumStreamObjects = 100).inspect(file, PDF).corrupt)
        assertEquals(600, inspection(maximumStreamObjects = 10_000).inspect(file, PDF).pageCount)
    }

    @Test
    fun `ordinary compressed, restricted, protected and damaged documents keep their results under the default limits`() {
        val inspection = InformationRequestEvidenceDocumentInspection()
        val compressed = InformationRequestEvidencePdfFixtures.compressed(file("compressed.pdf"), pages = 3_000)
        val restricted =
            InformationRequestEvidencePdfFixtures.ownerRestrictedCompressed(file("restricted.pdf"), pages = 40)
        val protected = InformationRequestEvidencePdfFixtures.passwordProtected(file("protected.pdf"))
        val damaged = write("damaged.pdf", "%PDF-1.7\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R".toByteArray())

        val compressedFacts = inspection.inspect(compressed, PDF)
        val restrictedFacts = inspection.inspect(restricted, PDF)
        val protectedFacts = inspection.inspect(protected, PDF)
        val damagedFacts = inspection.inspect(damaged, PDF)

        assertEquals(3_000, compressedFacts.pageCount)
        assertFalse(compressedFacts.corrupt)
        assertEquals(40, restrictedFacts.pageCount)
        assertFalse(restrictedFacts.encrypted)
        assertFalse(restrictedFacts.corrupt)
        assertTrue(protectedFacts.encrypted)
        assertFalse(protectedFacts.corrupt)
        assertNull(protectedFacts.pageCount)
        assertTrue(damagedFacts.corrupt)
        assertFalse(damagedFacts.encrypted)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 0, -1, 7])
    fun `the page count is the number of pages the document holds, whatever its page tree declares`(declared: Int) {
        val file = InformationRequestEvidencePdfFixtures.declaringPageCount(
            file("declared-$declared.pdf"),
            pages = 3,
            declared = declared
        )

        val facts = InformationRequestEvidenceDocumentInspection().inspect(file, PDF)

        assertFalse(facts.corrupt)
        assertEquals(3, facts.pageCount)
    }

    @Test
    fun `every inspection starts with its own full allowance`() {
        val inspection = inspection(maximumDecodedBytes = 3L * MEBIBYTE)
        val file =
            write("expanding.pdf", InformationRequestEvidencePdfFixtures.expandingCrossReferenceStream(2 * MEBIBYTE))

        repeat(3) {
            val facts = inspection.inspect(file, PDF)
            assertFalse(facts.corrupt)
            assertEquals(1, facts.pageCount)
        }
    }

    @Test
    fun `a document refused on another thread does not limit a concurrent inspection`() {
        val inspection = inspection(maximumDecodedBytes = 3L * MEBIBYTE)
        val refused =
            write("refused.pdf", InformationRequestEvidencePdfFixtures.expandingCrossReferenceStream(8 * MEBIBYTE))
        val readable =
            write("readable.pdf", InformationRequestEvidencePdfFixtures.expandingCrossReferenceStream(2 * MEBIBYTE))
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<Boolean> { inspection.inspect(refused, PDF).corrupt }
            val second = executor.submit<Int?> { inspection.inspect(readable, PDF).pageCount }

            assertTrue(first.get(60, TimeUnit.SECONDS))
            assertEquals(1, second.get(60, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `an inspection waits for a free place and gives it back when it finishes`() {
        val places = Semaphore(0, true)
        val inspection = InformationRequestEvidenceContentInspector(inspections = places)
        val file = write("listed.pdf", InformationRequestEvidencePdfFixtures.crossReferenceTable(extraObjects = 1))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val waiting = executor.submit<Int?> { inspection.inspect(file).pageCount }

            assertThrows<TimeoutException> { waiting.get(300, TimeUnit.MILLISECONDS) }
            places.release()
            assertEquals(1, waiting.get(60, TimeUnit.SECONDS))
            assertEquals(1, places.availablePermits())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a refused inspection gives its place back`() {
        val places = Semaphore(1, true)
        val inspection = InformationRequestEvidenceContentInspector(
            documents = InformationRequestEvidenceDocumentInspection(
                InformationRequestEvidenceDocumentLimits(maximumDecodedBytes = MEBIBYTE.toLong()),
            ),
            inspections = places,
        )
        val file =
            write("expanding.pdf", InformationRequestEvidencePdfFixtures.expandingCrossReferenceStream(4 * MEBIBYTE))

        assertTrue(inspection.inspect(file).corrupt)
        assertEquals(1, places.availablePermits())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "FlateDecode", "Fl", "LZWDecode", "LZW", "ASCIIHexDecode", "AHx", "ASCII85Decode", "A85",
            "RunLengthDecode", "RL",
        ]
    )
    fun `every registered decoding filter is metered while a document is inspected and only then`(name: String) {
        val filter = FilterFactory.INSTANCE.getFilter(name)
        val content = "record value ".repeat(64).toByteArray()
        val encoded = ByteArrayOutputStream().also { output ->
            filter.encode(
                ByteArrayInputStream(content),
                output,
                COSDictionary(),
                0
            )
        }
        val usage =
            InformationRequestEvidenceDocumentUsage(InformationRequestEvidenceDocumentLimits(maximumDecodedBytes = 16))

        assertThrows<RuntimeException> {
            InformationRequestEvidencePdfDecoding.metered(usage) {
                FilterFactory.INSTANCE.getFilter(name)
                    .decode(ByteArrayInputStream(encoded.toByteArray()), ByteArrayOutputStream(), COSDictionary(), 0)
            }
        }
        assertTrue(usage.exceeded)
        val decoded = ByteArrayOutputStream()
        FilterFactory.INSTANCE.getFilter(name)
            .decode(ByteArrayInputStream(encoded.toByteArray()), decoded, COSDictionary(), 0)
        assertArrayEquals(content, decoded.toByteArray())
    }

    @Test
    fun `the identity crypt filter is metered while a document is inspected`() {
        val usage =
            InformationRequestEvidenceDocumentUsage(InformationRequestEvidenceDocumentLimits(maximumDecodedBytes = 16))

        assertThrows<RuntimeException> {
            InformationRequestEvidencePdfDecoding.metered(usage) {
                FilterFactory.INSTANCE.getFilter(COSName.CRYPT)
                    .decode(ByteArrayInputStream(ByteArray(64) { 7 }), ByteArrayOutputStream(), COSDictionary(), 0)
            }
        }
        assertTrue(usage.exceeded)
    }

    @ParameterizedTest
    @ValueSource(strings = ["DCTDecode", "DCT", "JPXDecode", "JBIG2Decode", "CCITTFaxDecode", "CCF"])
    fun `image decoding filters are refused while a document is inspected`(name: String) {
        val usage = InformationRequestEvidenceDocumentUsage(InformationRequestEvidenceDocumentLimits())

        assertThrows<RuntimeException> {
            InformationRequestEvidencePdfDecoding.metered(usage) {
                FilterFactory.INSTANCE.getFilter(name)
                    .decode(ByteArrayInputStream(jpeg()), ByteArrayOutputStream(), COSDictionary(), 0)
            }
        }
        assertTrue(usage.exceeded)
    }

    @Test
    fun `an image decoding filter still decodes outside an inspection`() {
        val decoded = ByteArrayOutputStream()

        FilterFactory.INSTANCE.getFilter(COSName.DCT_DECODE)
            .decode(ByteArrayInputStream(jpeg()), decoded, COSDictionary(), 0)

        assertTrue(decoded.size() > 0)
    }

    private fun inspection(
        maximumDecodedBytes: Long = InformationRequestEvidenceDocumentLimits.MAXIMUM_DECODED_BYTES,
        maximumCrossReferenceEntries: Long = InformationRequestEvidenceDocumentLimits.MAXIMUM_CROSS_REFERENCE_ENTRIES,
        maximumCrossReferenceSections: Long = InformationRequestEvidenceDocumentLimits.MAXIMUM_CROSS_REFERENCE_SECTIONS,
        maximumStreamObjects: Long = InformationRequestEvidenceDocumentLimits.MAXIMUM_STREAM_OBJECTS,
    ) = InformationRequestEvidenceDocumentInspection(
        InformationRequestEvidenceDocumentLimits(
            maximumDecodedBytes,
            maximumCrossReferenceEntries,
            maximumCrossReferenceSections,
            maximumStreamObjects,
        )
    )

    private fun jpeg(): ByteArray {
        val output = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", output)
        return output.toByteArray()
    }

    private fun file(name: String): File = directory.resolve(name).toFile()

    private fun write(name: String, bytes: ByteArray): File = file(name).apply { writeBytes(bytes) }

    private companion object {
        const val PDF = "application/pdf"
        const val MEBIBYTE = 1024 * 1024
    }
}
