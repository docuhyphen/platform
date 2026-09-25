package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.DocumentVersionContentNotFoundException
import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.document.DocumentVersionLocatorKind
import com.docuhyphen.app.api.model.document.DocumentVersionStorageProvider
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.exchange.DocumentVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.storage.DocumentVersionContentService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.io.File
import java.nio.file.Files
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class DocumentVersionStoragePostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<DocumentVersionStoragePostgreSQLContainer>(imageName)

class DocumentVersionStoragePostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = DocumentVersionStoragePostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_document_version_storage_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    override fun start(): Map<String, String>
    {
        postgres.start()
        val root = Files.createTempDirectory("document-version-storage-test")
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "file.storage.service" to "local",
            "document.version.storage.local.root-directory" to root.toString(),
            "app.secrets.rotation.enabled" to "false",
            "quarkus.kafka.devservices.enabled" to "false",
        )
    }

    override fun stop()
    {
        postgres.stop()
    }
}

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class ExchangeDocumentVersionStorageContractTest
{
    @Inject
    lateinit var exchangeDocumentVersionService: ExchangeDocumentVersionService

    @Inject
    lateinit var documentVersionContentService: DocumentVersionContentService

    @Inject
    lateinit var documentVersionRepository: DocumentVersionRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    fun `a recorded version states its canonical object key`()
    {
        val documentId = storedDocument()

        val created = recordedVersion(documentId, "first content")

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(created.id))

            assertEquals(DocumentVersionStorageProvider.OBJECT_STORE, stored.storageProvider)
            assertEquals(DocumentVersionLocatorKind.OBJECT_KEY, stored.storageLocatorKind)
            assertEquals(
                "document-versions/$documentId/${stored.id}/Process_record_v1.pdf",
                stored.storageLocator,
            )
            assertEquals("Process record_v1.pdf", stored.fileName)
        }
    }

    @Test
    fun `a second version is stored under its own key and the first version stays readable`()
    {
        val documentId = storedDocument()

        val first = recordedVersion(documentId, "first content")
        val second = recordedVersion(documentId, "second content")

        QuarkusTransaction.requiringNew().run {
            val storedFirst = requireNotNull(documentVersionRepository.findById(first.id))
            val storedSecond = requireNotNull(documentVersionRepository.findById(second.id))

            assertNotEquals(storedFirst.storageLocator, storedSecond.storageLocator)
            assertEquals("first content", documentVersionContentService.open(storedFirst).file.readText())
            assertEquals("second content", documentVersionContentService.open(storedSecond).file.readText())
        }
    }

    @Test
    fun `opened content is named by the recorded version file name`()
    {
        val documentId = storedDocument()

        val created = recordedVersion(documentId, "first content")

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(created.id))
            val content = documentVersionContentService.open(stored)

            assertEquals("Process record_v1.pdf", content.fileName)
            assertEquals("first content", content.file.readText())
        }
    }

    @Test
    fun `a version whose stored content is missing is refused rather than served empty`()
    {
        val documentId = storedDocument()
        val versionId = UUID.randomUUID()
        val key = "document-versions/$documentId/$versionId/Process_record_v1.pdf"

        QuarkusTransaction.requiringNew().run {
            documentVersionRepository.save(
                DocumentVersion().apply {
                    id = versionId
                    document = entityManager.getReference(Document::class.java, documentId)
                    fileName = "Process record_v1.pdf"
                    storageProvider = DocumentVersionStorageProvider.OBJECT_STORE
                    storageLocatorKind = DocumentVersionLocatorKind.OBJECT_KEY
                    storageLocator = key
                    version = "1"
                    createdDate = Timestamp.from(Instant.now())
                    createdByPrincipalKind = PrincipalKind.USER
                    createdByPrincipalId = UUID.randomUUID()
                    contentLength = 13
                    contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
                    contentHash = "0".repeat(64)
                    contentVerification = DocumentVersionContentVerification.VERIFIED
                },
            )
        }

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(versionId))

            assertThrows(DocumentVersionContentNotFoundException::class.java)
            {
                documentVersionContentService.open(stored)
            }
        }
    }

    @Test
    fun `recorded content is written where the configured version storage holds it`()
    {
        val documentId = storedDocument()

        val created = recordedVersion(documentId, "first content")

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(created.id))
            val opened = documentVersionContentService.open(stored).file

            assertTrue(opened.isFile)
            assertTrue(opened.absolutePath.endsWith(stored.storageLocator.replace('/', File.separatorChar)))
        }
    }

    @Test
    fun `a recorded version states the digest and length of exactly the bytes that were written`()
    {
        val documentId = storedDocument()

        val created = recordedVersion(documentId, "abc")

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(created.id))

            assertEquals(3L, stored.contentLength)
            assertEquals(DocumentVersionContentHashAlgorithm.SHA_256, stored.contentHashAlgorithm)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", stored.contentHash)
            assertEquals(DocumentVersionContentVerification.VERIFIED, stored.contentVerification)
        }
    }

    @Test
    fun `a version of end-to-end ciphertext keeps its digest but is not verified`()
    {
        val documentId = storedDocument()

        val created = recordedVersion(documentId, "abc", DocumentEncryptionMode.END_TO_END)

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(created.id))

            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", stored.contentHash)
            assertEquals(DocumentVersionContentVerification.UNVERIFIED, stored.contentVerification)
        }
    }

    private fun recordedVersion(
        documentId: UUID,
        content: String,
        encryptionMode: DocumentEncryptionMode = DocumentEncryptionMode.INTERNAL,
    ): DocumentVersion =
        QuarkusTransaction.requiringNew().call<DocumentVersion> {
            exchangeDocumentVersionService.recordUploadedFileAsVersion(
                entityManager.getReference(Document::class.java, documentId),
                uploadedFile(content),
                PrincipalRef.user(UUID.randomUUID()),
                encryptionMode,
            )
        }

    private fun storedDocument(): UUID
    {
        val document = Document().apply {
            title = "Process record"
            type = DocumentType.PDF
            createdDate = Timestamp.from(Instant.now())
            updateDate = Timestamp.from(Instant.now())
        }

        QuarkusTransaction.requiringNew().run { entityManager.persist(document) }

        return document.id
    }

    private fun uploadedFile(content: String): File
    {
        val file = File.createTempFile("uploaded-document", ".pdf")
        file.deleteOnExit()
        file.writeText(content)
        return file
    }
}
