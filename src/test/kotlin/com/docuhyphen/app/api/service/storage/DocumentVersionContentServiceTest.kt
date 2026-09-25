package com.docuhyphen.app.api.service.storage

import com.docuhyphen.app.api.exception.DocumentVersionContentIntegrityException
import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionContentIdentityMapper
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.document.DocumentVersionLocatorKind
import com.docuhyphen.app.api.model.document.DocumentVersionStorageProvider
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.UUID

class DocumentVersionContentServiceTest
{
    @TempDir
    lateinit var root: Path

    @TempDir
    lateinit var uploads: Path

    @Test
    fun `stored content states the digest and length of the bytes that were written`()
    {
        val service = service()

        val stored = service.store(UUID.randomUUID(), UUID.randomUUID(), "record_v1.pdf", upload("abc"))

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

    private fun service() = DocumentVersionContentService(LocalDocumentVersionStorageService(root.toString()))

    private fun recordedVersion(service: DocumentVersionContentService, content: String): DocumentVersion
    {
        val documentId = UUID.randomUUID()
        val version = DocumentVersion().apply { fileName = "record_v1.pdf" }
        val stored = service.store(documentId, version.id, version.fileName, upload(content))

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
