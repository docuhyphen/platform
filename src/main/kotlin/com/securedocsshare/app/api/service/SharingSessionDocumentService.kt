package com.securedocsshare.app.api.service

import com.securedocsshare.app.api.annotation.DocumentAuditRequired
import com.securedocsshare.app.api.exception.SessionNotFoundException
import com.securedocsshare.app.api.exception.UserNotFoundException
import com.securedocsshare.app.api.interceptor.AuthTokenContext
import com.securedocsshare.app.api.model.Document
import com.securedocsshare.app.api.model.DocumentAuditLogAction
import com.securedocsshare.app.api.model.DocumentComment
import com.securedocsshare.app.api.model.DocumentType
import com.securedocsshare.app.api.repository.AppUserRepository
import com.securedocsshare.app.api.repository.DocumentCommentRepository
import com.securedocsshare.app.api.repository.SharingSessionRepository
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
    private val documentAuditService: DocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val documentCommentRepository: DocumentCommentRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentService::class.java)
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
            ?: throw SessionNotFoundException("Sharing session not found")

        //ToDo: check if the uploader is in the session

        //ToDo: End To End encryption

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw IllegalArgumentException("Session document not found")

//        val encryptionKey = awsS3Service.uploadDocument(file, bucketName, key)

        //ToDo: Encrypt the document
        //ToDo: Save the encryption key in the database
        //ToDo: Save the document in the database
        //ToDo: Save the document in the S3 bucket
        //ToDo: Log the action in the audit log
        //ToDo: Send an email to the receiver

        document.hash = "hash" //ToDo: Create a hash for the document

        sharingSessionRepository.update(sharingSession)
        documentAuditService.logAction(document, DocumentAuditLogAction.UPLOAD, appUser)
    }

    @DocumentAuditRequired
    @Transactional
    fun deleteDocument(
        sessionId: String,
        documentId: String
    )
    {
        val performedBy = authTokenContext.authToken.appUser!!.id.toString()

        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SessionNotFoundException("Sharing session not found")

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw SessionNotFoundException("Document not found")

        document.deleted = true
        document.updateDate = Timestamp.from(Instant.now())

        sharingSessionRepository.update(sharingSession)

        documentAuditService.logAction(document, DocumentAuditLogAction.DELETE, authTokenContext.authToken.appUser!!)
    }

    @Transactional
    fun addDocument(
        sessionId: String,
        documentType: DocumentType?,
        restrictedType: DocumentType?
    )
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SessionNotFoundException("Sharing session not found")

        val document = Document().apply {
            this.createdDate = Timestamp.from(Instant.now())
            this.updateDate = Timestamp.from(Instant.now())
            this.deleted = false
            this.type = documentType

            this.restrictedType = restrictedType
        }

        sharingSession.documents.add(document)

        sharingSessionRepository.update(sharingSession)
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

    @DocumentAuditRequired
    @Transactional
    fun updateDocument(
        sessionId: String,
        documentId: String,
        title: String?,
        type: DocumentType?,
        restrictedType: DocumentType?
    )
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(sessionId))
            ?: throw SessionNotFoundException("Sharing session not found")

        val document = sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw SessionNotFoundException("Document not found")

        title ?: throw IllegalArgumentException("Title cannot be null")

        document.title = title
        document.type = type
        document.restrictedType = restrictedType

        sharingSessionRepository.update(sharingSession)

        documentAuditService.logAction(
            document,
            DocumentAuditLogAction.UPDATE,
            authTokenContext.authToken.appUser!!
        )
    }

    @Transactional
    fun addDocumentComment(
        documentId: String,
        commentText: String,
        commentedBy: String
    ): DocumentComment
    {
        val sharingSession = sharingSessionRepository.findById(UUID.fromString(documentId))
            ?: throw SessionNotFoundException("Document not found")

        val user = appUserRepository.findByEmail(commentedBy)
            ?: throw UserNotFoundException("User not found")

        val comment = DocumentComment().apply {
            this.commentText = commentText
            this.document = document //TODO: Add the document entity
            this.commentedBy = user
            this.createdDate = Timestamp.from(Instant.now())
        }

        documentCommentRepository.save(comment)
        return comment
    }

    fun getDocumentComments(documentId: String): List<DocumentComment>
    {
        return documentCommentRepository.findByDocumentId(UUID.fromString(documentId))
    }
}