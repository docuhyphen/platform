package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.model.entity.DocumentAuditLogAction
import com.dochyphen.app.api.model.entity.DocumentType
import com.dochyphen.app.api.model.entity.DocumentVersion
import com.dochyphen.app.api.repository.DocumentVersionRepository
import com.dochyphen.app.api.repository.SharingSessionDocumentRepository
import com.dochyphen.app.api.repository.SharingSessionRepository
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
class SharingSessionDocumentVersionService @Inject constructor(
    private val sharingSessionRepository: SharingSessionRepository,
    private val sharingSessionDocumentRepository: SharingSessionDocumentRepository,
    private val documentVersionRepository: DocumentVersionRepository,
    private val documentAuditService: SharingSessionDocumentAuditService,
    private val entityManager: EntityManager
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentVersionService::class.java)
        private const val VERSIONS_STORAGE_PATH = "document-versions"
    }

    @Transactional
    fun createVersion(sessionId: String, documentId: String, file: File?, currentUserEmail: String?): DocumentVersion
    {
        val session = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val document = sharingSessionDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw SharingSessionDocumentNotFoundException("Document not found")

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

    fun getDocumentVersions(sessionId: String, documentId: String): List<DocumentVersion>
    {
        sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val document = sharingSessionDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw SharingSessionDocumentNotFoundException("Document not found")

        return documentVersionRepository.findByDocumentId(document.id)
    }

    fun getVersionFile(sessionId: String, documentId: String, versionId: String): File
    {
        sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        sharingSessionDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw SharingSessionDocumentNotFoundException("Document not found")

        val version = documentVersionRepository.findById(UUID.fromString(versionId))
            ?: throw IllegalArgumentException("Version not found")

        return File(version.storagePath)
    }

    fun getLatestVersion(sessionId: String, documentId: String): DocumentVersion?
    {
        sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val document = sharingSessionDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw SharingSessionDocumentNotFoundException("Document not found")

        return documentVersionRepository.findLatestByDocumentId(document.id)
    }
}