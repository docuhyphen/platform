package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.entity.DocumentAuditLogAction
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.repository.DocumentVersionRepository
import com.docuhyphen.app.api.repository.ExchangeDocumentRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeDocumentVersionService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val exchangeDocumentRepository: ExchangeDocumentRepository,
    private val documentVersionRepository: DocumentVersionRepository,
    private val documentAuditService: ExchangeDocumentAuditService,
    private val entityManager: EntityManager
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentVersionService::class.java)
        private const val VERSIONS_STORAGE_PATH = "document-versions"
    }

    @Transactional
    fun createVersion(exchangeId: String, documentId: String, file: File?, currentUserEmail: String?): DocumentVersion
    {
        exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val document = exchangeDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw ExchangeDocumentNotFoundException("Document not found")

        if (file == null)
        {
            throw IllegalArgumentException("File is required")
        }

        val versionCount = documentVersionRepository.findByDocumentId(document.id).size
        val versionNumber = "v${versionCount + 1}"

        // Create directory if it doesn't exist
        val storagePath = "$VERSIONS_STORAGE_PATH/${document.id}"
        Files.createDirectories(Paths.get(storagePath))

        // Generate version file name and copy to storage
        val extension = document.type?.let { DocumentType.toFileExtension(it) } ?: ""
        val versionFileName = "${document.title}_$versionNumber$extension"
        val destinationPath = Paths.get("$storagePath/$versionFileName")

        Files.copy(file.toPath(), destinationPath, StandardCopyOption.REPLACE_EXISTING)

        // Create version entity
        val version = DocumentVersion().apply {
            this.document = document
            this.fileName = versionFileName
            this.storagePath = destinationPath.toString()
            this.version = versionNumber
            this.createdDate = Timestamp.from(Instant.now())
            this.createdByEmail = currentUserEmail
        }

        documentVersionRepository.save(version)

        // Log the action
        documentAuditService.logAction(
            document,
            DocumentAuditLogAction.VERSION_CREATED,
            currentUserEmail ?: "System"
        )

        return version
    }

    fun getDocumentVersions(exchangeId: String, documentId: String): List<DocumentVersion>
    {
        exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val document = exchangeDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw ExchangeDocumentNotFoundException("Document not found")

        return documentVersionRepository.findByDocumentId(document.id)
    }

    fun getVersionFile(exchangeId: String, documentId: String, versionId: String): File
    {
        exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        exchangeDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw ExchangeDocumentNotFoundException("Document not found")

        val version = documentVersionRepository.findById(UUID.fromString(versionId))
            ?: throw IllegalArgumentException("Version not found")

        return File(version.storagePath)
    }

    fun getLatestVersion(exchangeId: String, documentId: String): DocumentVersion?
    {
        exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val document = exchangeDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw ExchangeDocumentNotFoundException("Document not found")

        return documentVersionRepository.findLatestByDocumentId(document.id)
    }
}