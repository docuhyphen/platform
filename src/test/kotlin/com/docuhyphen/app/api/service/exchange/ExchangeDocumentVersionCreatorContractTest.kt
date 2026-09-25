package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.document.DocumentVersionCreatorMapper
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentEncryptionMode
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.exchange.DocumentVersionRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * A stored version states, as one canonical principal, the principal its upload was authorized as.
 * Every kind of principal is stored the same way, and what is stored is exactly what reads back.
 */
@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class ExchangeDocumentVersionCreatorContractTest
{
    @Inject
    lateinit var exchangeDocumentVersionService: ExchangeDocumentVersionService

    @Inject
    lateinit var documentVersionRepository: DocumentVersionRepository

    @Inject
    lateinit var entityManager: EntityManager

    @Test
    fun `a recorded version states the registered user its upload was authorized as`()
    {
        val documentId = storedDocument()
        val creator = PrincipalRef.user(UUID.randomUUID())

        val created = recordedVersion(documentId, creator)

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(created.id))

            assertEquals(PrincipalKind.USER, stored.createdByPrincipalKind)
            assertEquals(creator.id, stored.createdByPrincipalId)
            assertEquals(creator, DocumentVersionCreatorMapper.read(stored))
        }
    }

    @Test
    fun `a recorded version states a group its upload was authorized as`()
    {
        val documentId = storedDocument()
        val creator = PrincipalRef.group(UUID.randomUUID())

        val created = recordedVersion(documentId, creator)

        QuarkusTransaction.requiringNew().run {
            val stored = requireNotNull(documentVersionRepository.findById(created.id))

            assertEquals(creator, DocumentVersionCreatorMapper.read(stored))
        }
    }

    private fun recordedVersion(documentId: UUID, creator: PrincipalRef) =
        QuarkusTransaction.requiringNew().call {
            exchangeDocumentVersionService.recordUploadedFileAsVersion(
                entityManager.getReference(Document::class.java, documentId),
                uploadedFile(),
                creator,
                DocumentEncryptionMode.INTERNAL,
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

    private fun uploadedFile(): File
    {
        val file = File.createTempFile("uploaded-document", ".pdf")
        file.deleteOnExit()
        file.writeText("first content")
        return file
    }
}
