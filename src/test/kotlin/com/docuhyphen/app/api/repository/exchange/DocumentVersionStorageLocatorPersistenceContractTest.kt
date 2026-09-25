package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.document.DocumentVersionLocatorKind
import com.docuhyphen.app.api.model.document.DocumentVersionStorageLocatorMapper
import com.docuhyphen.app.api.model.document.DocumentVersionStorageProvider
import com.docuhyphen.app.api.model.document.ObjectStoreDocumentVersionLocator
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.PrincipalKind
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

private class DocumentVersionLocatorPostgreSQLContainer(imageName: String) :
    PostgreSQLContainer<DocumentVersionLocatorPostgreSQLContainer>(imageName)

class DocumentVersionLocatorPostgreSQLResource : QuarkusTestResourceLifecycleManager
{
    private val postgres = DocumentVersionLocatorPostgreSQLContainer("postgres:17")
        .withDatabaseName("docuhyphen_document_version_locator_test")
        .withUsername("docuhyphen")
        .withPassword("docuhyphen")

    override fun start(): Map<String, String>
    {
        postgres.start()
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "file.storage.service" to "local",
            "app.secrets.rotation.enabled" to "false",
            "quarkus.kafka.devservices.enabled" to "false",
        )
    }

    override fun stop()
    {
        postgres.stop()
    }
}

/**
 * A stored version's typed locator survives a real round trip through the mapped entity and comes
 * back as the exact object key it was written with.
 */
@QuarkusTest
@QuarkusTestResource(DocumentVersionLocatorPostgreSQLResource::class)
class DocumentVersionStorageLocatorPersistenceContractTest
{
    @Inject
    lateinit var documentVersionRepository: DocumentVersionRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    fun `a version stored with an object key resolves through that key`()
    {
        val documentId = storedDocument()
        val versionId = UUID.randomUUID()

        QuarkusTransaction.requiringNew().run {
            documentVersionRepository.save(
                version(versionId, documentId, "document-versions/$documentId/$versionId/record_v1.pdf"),
            )
        }

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(versionId))

            assertEquals(DocumentVersionStorageProvider.OBJECT_STORE, stored.storageProvider)
            assertEquals(DocumentVersionLocatorKind.OBJECT_KEY, stored.storageLocatorKind)
            assertEquals("document-versions/$documentId/$versionId/record_v1.pdf", stored.storageLocator)
            assertEquals(
                ObjectStoreDocumentVersionLocator("document-versions/$documentId/$versionId/record_v1.pdf"),
                DocumentVersionStorageLocatorMapper.read(stored),
            )
        }
    }

    private fun storedDocument(): UUID
    {
        val document = Document().apply {
            title = "Process record"
            createdDate = Timestamp.from(Instant.now())
            updateDate = Timestamp.from(Instant.now())
        }

        QuarkusTransaction.requiringNew().run { entityManager.persist(document) }

        return document.id
    }

    private fun version(id: UUID, documentId: UUID, locator: String): DocumentVersion =
        DocumentVersion().apply {
            this.id = id
            this.document = entityManager.getReference(Document::class.java, documentId)
            this.fileName = "record.pdf"
            this.storageProvider = DocumentVersionStorageProvider.OBJECT_STORE
            this.storageLocatorKind = DocumentVersionLocatorKind.OBJECT_KEY
            this.storageLocator = locator
            this.version = "1"
            this.createdDate = Timestamp.from(Instant.now())
            this.createdByPrincipalKind = PrincipalKind.USER
            this.createdByPrincipalId = UUID.randomUUID()
            this.contentLength = 3
            this.contentHashAlgorithm = DocumentVersionContentHashAlgorithm.SHA_256
            this.contentHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            this.contentVerification = DocumentVersionContentVerification.VERIFIED
        }
}
