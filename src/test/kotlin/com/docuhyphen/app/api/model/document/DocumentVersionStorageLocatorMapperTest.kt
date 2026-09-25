package com.docuhyphen.app.api.model.document

import com.docuhyphen.app.api.model.entity.DocumentVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * A stored document version is located through the one typed locator it states, and a stored value
 * that is not a valid object key is refused rather than used as one.
 */
class DocumentVersionStorageLocatorMapperTest
{
    @Test
    fun `a version is read as the object key it states`()
    {
        val version = version("document-versions/9f1/record-1")

        val locator = DocumentVersionStorageLocatorMapper.read(version)

        assertEquals(ObjectStoreDocumentVersionLocator("document-versions/9f1/record-1"), locator)
        assertEquals(DocumentVersionStorageProvider.OBJECT_STORE, locator.provider)
        assertEquals(DocumentVersionLocatorKind.OBJECT_KEY, locator.kind)
    }

    @Test
    fun `a stored value that is not a valid object key is refused`()
    {
        val version = version("C:/storage/versions/9f1/record_v1.pdf")

        assertThrows<IllegalArgumentException> { DocumentVersionStorageLocatorMapper.read(version) }
    }

    private fun version(locator: String): DocumentVersion =
        DocumentVersion().apply {
            this.fileName = "record.pdf"
            this.version = "1"
            this.storageProvider = DocumentVersionStorageProvider.OBJECT_STORE
            this.storageLocatorKind = DocumentVersionLocatorKind.OBJECT_KEY
            this.storageLocator = locator
        }
}
