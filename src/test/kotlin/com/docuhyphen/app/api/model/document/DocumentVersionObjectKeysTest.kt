package com.docuhyphen.app.api.model.document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DocumentVersionObjectKeysTest
{
    @Test
    fun `allocates a key that names the prefix, the document, the version and the file`()
    {
        val documentId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val versionId = UUID.fromString("22222222-2222-2222-2222-222222222222")

        val locator = DocumentVersionObjectKeys.allocate(documentId, versionId, "record_v1.pdf")

        assertEquals(
            "document-versions/$documentId/$versionId/record_v1.pdf",
            locator.value,
        )
        assertEquals(DocumentVersionStorageProvider.OBJECT_STORE, locator.provider)
        assertEquals(DocumentVersionLocatorKind.OBJECT_KEY, locator.kind)
    }

    @Test
    fun `allocates a different key for every version of one document`()
    {
        val documentId = UUID.randomUUID()

        val first = DocumentVersionObjectKeys.allocate(documentId, UUID.randomUUID(), "record_v1.pdf")
        val second = DocumentVersionObjectKeys.allocate(documentId, UUID.randomUUID(), "record_v1.pdf")

        assertNotEquals(first.value, second.value)
    }

    @Test
    fun `reduces a file name carrying separators to a single key segment`()
    {
        val documentId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        val locator = DocumentVersionObjectKeys.allocate(documentId, versionId, "reports/2026\\record_v1.pdf")

        assertEquals(
            "document-versions/$documentId/$versionId/reports_2026_record_v1.pdf",
            locator.value,
        )
    }

    @Test
    fun `reduces a file name carrying a drive marker and surrounding whitespace to a single segment`()
    {
        val documentId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        val locator = DocumentVersionObjectKeys.allocate(documentId, versionId, "  C:record v1.pdf  ")

        assertEquals(
            "document-versions/$documentId/$versionId/C_record_v1.pdf",
            locator.value,
        )
    }

    @Test
    fun `falls back to a stable name when the file name carries nothing usable`()
    {
        val documentId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        val traversal = DocumentVersionObjectKeys.allocate(documentId, versionId, "..")
        val blank = DocumentVersionObjectKeys.allocate(documentId, versionId, "   ")
        val separators = DocumentVersionObjectKeys.allocate(documentId, versionId, "///")

        assertEquals("document-versions/$documentId/$versionId/version-content", traversal.value)
        assertEquals("document-versions/$documentId/$versionId/version-content", blank.value)
        assertEquals("document-versions/$documentId/$versionId/version-content", separators.value)
    }

    @Test
    fun `keeps the allocated key valid for an object store locator`()
    {
        val locator = DocumentVersionObjectKeys.allocate(UUID.randomUUID(), UUID.randomUUID(), "récord v1.pdf")

        assertEquals(ObjectStoreDocumentVersionLocator(locator.value), locator)
        assertTrue(locator.value.endsWith("/r_cord_v1.pdf"))
    }
}
