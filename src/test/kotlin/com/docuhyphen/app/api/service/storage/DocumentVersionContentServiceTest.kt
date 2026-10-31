package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentIntegrityException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.model.document.*
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class DocumentVersionContentServiceTest
{
    @TempDir
    lateinit var root: Path

    @TempDir
    lateinit var uploads: Path

    @TempDir
    lateinit var copies: Path

    @Test
    fun `stored content states the digest and length of the bytes that were written`()
    {
        val service = service()

        val stored = service.store(
            DocumentVersionObjectKeys.allocate(UUID.randomUUID(), UUID.randomUUID(), "record_v1.pdf"),
            upload("abc"),
        )

        assertEquals(DocumentVersionContentHashAlgorithm.SHA_256, stored.digest.algorithm)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", stored.digest.value)
        assertEquals(3L, stored.digest.length)
        assertEquals("abc", root.resolve(stored.locator.value).toFile().readText())
    }

    @Test
    fun `content whose stored bytes still match the recorded digest is opened`()
    {
        val service = service()
        val version = recordedVersion(service, "abc")

        val content = service.open(version)

        assertEquals("abc", content.file.readText())
        assertEquals("record_v1.pdf", content.fileName)
    }

    @Test
    fun `content whose stored bytes changed after they were written is refused`()
    {
        val service = service()
        val version = recordedVersion(service, "abc")
        root.resolve(version.storageLocator).toFile().writeText("abd")

        assertThrows(DocumentVersionContentIntegrityException::class.java) { service.open(version) }
    }

    @Test
    fun `content whose stored length changed after it was written is refused`()
    {
        val service = service()
        val version = recordedVersion(service, "abc")
        root.resolve(version.storageLocator).toFile().appendText("d")

        assertThrows(DocumentVersionContentIntegrityException::class.java) { service.open(version) }
    }

    @Test
    fun `a version whose content is missing is refused`()
    {
        val service = service()
        val version = recordedVersion(service, "abc")
        root.resolve(version.storageLocator).toFile().delete()

        assertThrows(DocumentVersionContentNotFoundException::class.java) { service.open(version) }
    }

    @Test
    fun `content refused because its bytes changed releases the copy that was opened to check it`() {
        val service =
            DocumentVersionContentService(CopyingStorage(LocalDocumentVersionStorageService(root.toString()), copies))
        val version = recordedVersion(service, "abc")
        root.resolve(version.storageLocator).toFile().writeText("abd")

        assertThrows(DocumentVersionContentIntegrityException::class.java) { service.open(version) }

        assertEquals(emptyList<Path>(), Files.list(copies).use { it.toList() })
    }

    @Test
    fun `closing opened content releases the copy it was read from`() {
        val service =
            DocumentVersionContentService(CopyingStorage(LocalDocumentVersionStorageService(root.toString()), copies))
        val version = recordedVersion(service, "abc")

        val content = service.open(version)
        assertEquals("abc", content.file.readText())
        content.close()

        assertFalse(content.file.exists())
        assertEquals(emptyList<Path>(), Files.list(copies).use { it.toList() })
        assertEquals("abc", root.resolve(version.storageLocator).toFile().readText())
    }

    private fun service() = DocumentVersionContentService(LocalDocumentVersionStorageService(root.toString()))

    private class CopyingStorage(
        private val stored: LocalDocumentVersionStorageService,
        private val copies: Path,
    ) : DocumentVersionStorageService by stored {
        override fun openVersion(locator: ObjectStoreDocumentVersionLocator): DocumentVersionReadHandle {
            val source = stored.openVersion(locator).file
            val directory = Files.createTempDirectory(copies, "opened-version")
            val copy = directory.resolve(source.name)
            Files.copy(source.toPath(), copy)
            return DocumentVersionReadHandle.temporaryCopy(copy.toFile(), directory)
        }
    }

    private fun recordedVersion(service: DocumentVersionContentService, content: String): DocumentVersion
    {
        val documentId = UUID.randomUUID()
        val version = DocumentVersion().apply { fileName = "record_v1.pdf" }
        val stored =
            service.store(DocumentVersionObjectKeys.allocate(documentId, version.id, version.fileName), upload(content))

        assertEquals(DocumentVersionStorageProvider.OBJECT_STORE, stored.locator.provider)
        assertEquals(DocumentVersionLocatorKind.OBJECT_KEY, stored.locator.kind)

        DocumentVersionContentIdentityMapper.recordOn(
            version,
            stored,
            DocumentVersionContentVerification.forEncryptionMode(DocumentEncryptionMode.INTERNAL),
        )
        return version
    }

    private fun upload(content: String): File =
        File.createTempFile("uploaded-version", ".pdf", uploads.toFile()).apply { writeText(content) }
}
