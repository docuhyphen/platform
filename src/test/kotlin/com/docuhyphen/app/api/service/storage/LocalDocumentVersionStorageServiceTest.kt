package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentDigestMismatchException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.exception.DocumentVersionObjectKeyInUseException
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.DocumentVersionLocatorKind
import com.docuhyphen.app.api.model.document.DocumentVersionStorageProvider
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LocalDocumentVersionStorageServiceTest
{
    @TempDir
    lateinit var root: Path

    @Test
    fun `writes the content at the key and reports an object store locator`()
    {
        val storage = LocalDocumentVersionStorageService(root.toString())
        val source = sourceFile("first content")

        val locator = storage.writeNewVersion("document-versions/a/b/record_v1.pdf", source, digestOf(source))

        assertEquals(ObjectStoreDocumentVersionLocator("document-versions/a/b/record_v1.pdf"), locator)
        assertEquals(DocumentVersionStorageProvider.OBJECT_STORE, locator.provider)
        assertEquals(DocumentVersionLocatorKind.OBJECT_KEY, locator.kind)
        assertEquals(
            "first content",
            root.resolve("document-versions/a/b/record_v1.pdf").toFile().readText(),
        )
    }

    @Test
    fun `refuses to write a key that already holds content and preserves the stored bytes`()
    {
        val storage = LocalDocumentVersionStorageService(root.toString())
        val first = sourceFile("first content")
        storage.writeNewVersion("document-versions/a/b/record_v1.pdf", first, digestOf(first))
        val second = sourceFile("second content")

        assertThrows(DocumentVersionObjectKeyInUseException::class.java)
        {
            storage.writeNewVersion("document-versions/a/b/record_v1.pdf", second, digestOf(second))
        }

        assertEquals(
            "first content",
            root.resolve("document-versions/a/b/record_v1.pdf").toFile().readText(),
        )
    }

    @Test
    fun `opens stored content by its key with the key's final segment as the file name`()
    {
        val storage = LocalDocumentVersionStorageService(root.toString())
        val source = sourceFile("stored content")
        val locator = storage.writeNewVersion("document-versions/a/b/record_v1.pdf", source, digestOf(source))

        val opened = storage.openVersion(locator)

        assertEquals("record_v1.pdf", opened.name)
        assertArrayEquals(source.readBytes(), opened.readBytes())
    }

    @Test
    fun `refuses to open a key that holds no content`()
    {
        val storage = LocalDocumentVersionStorageService(root.toString())

        assertThrows(DocumentVersionContentNotFoundException::class.java)
        {
            storage.openVersion(ObjectStoreDocumentVersionLocator("document-versions/a/b/record_v1.pdf"))
        }
    }

    @Test
    fun `refuses content whose written bytes do not match the expected digest and leaves nothing at the key`()
    {
        val storage = LocalDocumentVersionStorageService(root.toString())
        val expected = digestOf(sourceFile("expected content"))

        assertThrows(DocumentVersionContentDigestMismatchException::class.java)
        {
            storage.writeNewVersion("document-versions/a/b/record_v1.pdf", sourceFile("received content"), expected)
        }

        assertFalse(root.resolve("document-versions/a/b/record_v1.pdf").toFile().exists())
    }

    private fun digestOf(file: File) = DocumentVersionContentDigests.of(file)

    private fun sourceFile(content: String): File
    {
        val file = File.createTempFile("document-version-source", ".pdf")
        file.deleteOnExit()
        file.writeText(content)
        return file
    }
}
