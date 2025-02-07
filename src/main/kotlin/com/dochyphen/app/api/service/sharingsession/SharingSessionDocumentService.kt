package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.annotation.DocumentAuditRequired
import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.Document
import com.dochyphen.app.api.model.entity.DocumentAuditLogAction
import com.dochyphen.app.api.model.entity.DocumentEncryptionMode
import com.dochyphen.app.api.model.entity.DocumentType
import com.dochyphen.app.api.repository.SharingSessionRepository
import com.dochyphen.app.api.service.EmailService
import com.dochyphen.app.api.service.FileStorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SharingSessionDocumentService @Inject constructor(
    private val sessionRepo: SharingSessionRepository,
    private val auditService: SharingSessionDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val emailService: EmailService,
    private val fileStorageService: FileStorageService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentService::class.java)
    }

    @Transactional
    @DocumentAuditRequired
    fun addDocument(
        sessionId: String,
        title: String?,
        documentType: DocumentType?,
        restrictedType: DocumentType?
    ): Document
    {
        val sharingSession = sessionRepo.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        title ?: throw IllegalArgumentException("Title cannot be null")

        val document = Document().apply {
            this.title = title
            this.createdDate = Timestamp.from(Instant.now())
            this.updateDate = Timestamp.from(Instant.now())
            this.isDeleted = false
            this.type = documentType
            this.restrictedType = restrictedType
        }

        sharingSession.documents.add(document)
        sessionRepo.update(sharingSession)

        val savedDocument = sharingSession.documents.last()

        auditService.logAction(
            savedDocument,
            DocumentAuditLogAction.CREATED,
            authTokenContext.authToken.appUser!!
        )

        return savedDocument
    }

    @DocumentAuditRequired
    @Transactional
    fun deleteDocument(
        sessionId: String,
        documentId: String
    )
    {
        val sharingSession = sessionRepo.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw SharingSessionNotFoundException("Document not found")

        if(document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }
        document.isDeleted = true
        document.updateDate = Timestamp.from(Instant.now())

        sessionRepo.update(sharingSession)

        auditService.logAction(document, DocumentAuditLogAction.DELETE, authTokenContext.authToken.appUser!!)
    }

    @DocumentAuditRequired
    @Transactional
    fun updateDocument(
        sessionId: String,
        documentId: String,
        title: String?,
        type: DocumentType?,
        restrictedType: DocumentType?
    ): Document
    {
        val sharingSession = sessionRepo.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val document = sharingSession.documents
            .find { it.id == UUID.fromString(documentId) }
            ?: throw SharingSessionDocumentNotFoundException("Document not found")

        if(document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }

        title ?: throw IllegalArgumentException("Title cannot be null")

        document.title = title
        document.type = type
        document.restrictedType = restrictedType

        sessionRepo.update(sharingSession)

        auditService.logAction(
            document,
            DocumentAuditLogAction.UPDATE,
            authTokenContext.authToken.appUser!!
        )

        return document
    }

    @Transactional
//    @DocumentAuditRequired
    fun uploadDocument(
        file: File?,
        extension: String?,
        sessionId: String?,
        documentId: String?,
        encryptionMode: DocumentEncryptionMode?
    )
    {
        val sharingSession = sessionRepo.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw IllegalArgumentException("Sharing Session document not found")

        if (document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }

        file ?: throw IllegalArgumentException("File cannot be null")

        extension ?: throw IllegalArgumentException("Extension cannot be null")


        val appUser = authTokenContext.authToken.appUser!!

        if (sharingSession.initiator?.id != appUser.id && sharingSession.recipient?.id != appUser.id)
        {
            //ToDo: check participant permissions
            throw IllegalArgumentException("User does not have permission to upload document")
        }

        if (sharingSession.initiator?.id != appUser.id && !sharingSession.allowDocumentUpload)
        {
            throw IllegalArgumentException("User does not have permission to upload document")
        }

        document.hash = "hash"
        document.type = DocumentType.fromFileExtension(extension)
        sessionRepo.update(sharingSession)

        fileStorageService.uploadDocument(file, "${document.id}$extension")

        auditService.logAction(
            document,
            DocumentAuditLogAction.UPDATE,
            appUser
        )

        if (sharingSession.initiator?.id == appUser.id)
        {
            emailService.sendEmail(
                sharingSession.recipient?.email!!,
                "Document uploaded",
                "Document uploaded by ${appUser.email}"
            )
        }
        else
        {
            //ToDo: also send to other participants
            emailService.sendEmail(
                sharingSession.initiator?.email!!,
                "Document uploaded",
                "Document uploaded by ${appUser.email}"
            )
        }
    }

    @DocumentAuditRequired
    fun downloadDocument(
        sessionId: String,
        documentId: String
    ): File
    {
        val document = Document() // Retrieve the document entity as needed
//        val file = awsS3Service.downloadDocument(bucketName, key, encryptionKey)
//        documentAuditService.logAction(document, DocumentAuditLogAction.DOWNLOAD, performedBy)
        return File("file")
    }
}