package com.docuhyphen.app.api.model.document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DocumentVersionStorageLocatorTest
{
    @Test
    fun `an object store is the only provider a version can be held by`()
    {
        assertEquals(listOf(DocumentVersionStorageProvider.OBJECT_STORE), DocumentVersionStorageProvider.entries)
    }

    @Test
    fun `an object key is the only kind of locator a version can state`()
    {
        assertEquals(listOf(DocumentVersionLocatorKind.OBJECT_KEY), DocumentVersionLocatorKind.entries)
    }

    @Test
    fun `an object locator states its provider, kind, and exact key`()
    {
        val locator = ObjectStoreDocumentVersionLocator("document-versions/9f1/record-1")

        assertEquals(DocumentVersionStorageProvider.OBJECT_STORE, locator.provider)
        assertEquals(DocumentVersionLocatorKind.OBJECT_KEY, locator.kind)
        assertEquals("document-versions/9f1/record-1", locator.value)
        assertEquals(locator, ObjectStoreDocumentVersionLocator("document-versions/9f1/record-1"))
        assertNotEquals(locator, ObjectStoreDocumentVersionLocator("document-versions/9f1/record-2"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `a locator rejects a blank value`(value: String)
    {
        assertThrows<IllegalArgumentException> { ObjectStoreDocumentVersionLocator(value) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/document-versions/9f1/record-1",
            "document-versions\\9f1\\record-1",
            "C:/document-versions/9f1/record-1",
            "document-versions/../9f1/record-1",
            "document-versions/./record-1",
            "document-versions//record-1",
            " document-versions/9f1/record-1",
            "document-versions/9f1/record-1 ",
        ],
    )
    fun `an object key cannot carry filesystem or traversal syntax`(key: String)
    {
        assertThrows<IllegalArgumentException> { ObjectStoreDocumentVersionLocator(key) }
    }

    @Test
    fun `copying a locator cannot bypass its validation`()
    {
        val locator = ObjectStoreDocumentVersionLocator("document-versions/9f1/record-1")

        assertThrows<IllegalArgumentException> { locator.copy(value = "") }
        assertThrows<IllegalArgumentException> { locator.copy(value = "/absolute") }
    }

    @Test
    fun `a recorded kind resolves to its own typed locator`()
    {
        val resolved = DocumentVersionStorageLocators.resolve(
            DocumentVersionLocatorKind.OBJECT_KEY,
            "document-versions/9f1/record-1",
        )

        assertTrue(resolved is ObjectStoreDocumentVersionLocator)
        assertEquals("document-versions/9f1/record-1", resolved.value)
    }

    @Test
    fun `a value recorded as an object key must satisfy object key rules`()
    {
        assertThrows<IllegalArgumentException> {
            DocumentVersionStorageLocators.resolve(
                DocumentVersionLocatorKind.OBJECT_KEY,
                "C:/uploads/versions/9f1/record_v1.pdf",
            )
        }
    }
}
