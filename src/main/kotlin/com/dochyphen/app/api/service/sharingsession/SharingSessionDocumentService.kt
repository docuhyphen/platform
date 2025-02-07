package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.annotation.DocumentAuditRequired
import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.exception.UserNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.Document
import com.dochyphen.app.api.model.entity.DocumentAuditLogAction
import com.dochyphen.app.api.model.entity.DocumentType
import com.dochyphen.app.api.repository.AppUserRepository
import com.dochyphen.app.api.repository.DocumentCommentRepository
import com.dochyphen.app.api.repository.SharingSessionRepository
import com.dochyphen.app.api.service.AppUserService
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
    private val sharingSessionRepository: SharingSessionRepository,
    private val appUserRepository: AppUserRepository,
    private val appUserService: AppUserService,
    private val sharingSessionDocumentAuditService: SharingSessionDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val documentCommentRepository: DocumentCommentRepository,
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
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
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
        sharingSessionRepository.update(sharingSession)

        val savedDocument = sharingSession.documents.last()

        sharingSessionDocumentAuditService.logAction(
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
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw SharingSessionNotFoundException("Document not found")

        if(document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }
        document.isDeleted = true
        document.updateDate = Timestamp.from(Instant.now())

        sharingSessionRepository.update(sharingSession)

        sharingSessionDocumentAuditService.logAction(document, DocumentAuditLogAction.DELETE, authTokenContext.authToken.appUser!!)
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
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
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

        sharingSessionRepository.update(sharingSession)

        sharingSessionDocumentAuditService.logAction(
            document,
            DocumentAuditLogAction.UPDATE,
            authTokenContext.authToken.appUser!!
        )

        return document
    }

    @DocumentAuditRequired
    @Transactional
    fun uploadDocument(
        file: File,
        sessionId: String,
        documentId: String,
        performedBy: String
    )
    {
        var appUser = appUserService.findUserByEmail(performedBy)

        if (appUser == null)
        {
            logger.error("Failed to upload document, User not found using email: $performedBy")
        }

        appUser = appUserService.getAppUserById(UUID.fromString(performedBy))

        if (appUser == null)
        {
            logger.error("Failed to upload document, User not found using id: $performedBy")
            throw UserNotFoundException("User not found")
        }

        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")

        //ToDo: check if the uploader is in the session

        //ToDo: End To End encryption

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw IllegalArgumentException("Session document not found")

        if(document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }

//        val encryptionKey = awsS3Service.uploadDocument(file, bucketName, key)

        //ToDo: Encrypt the document
        //ToDo: Save the encryption key in the database
        //ToDo: Save the document in the database
        //ToDo: Save the document in the S3 bucket
        //ToDo: Log the action in the audit log
        //ToDo: Send an email to the recipient

        document.hash = "hash" //ToDo: Create a hash for the document

        sharingSessionRepository.update(sharingSession)
        sharingSessionDocumentAuditService.logAction(document, DocumentAuditLogAction.UPLOAD, appUser)
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