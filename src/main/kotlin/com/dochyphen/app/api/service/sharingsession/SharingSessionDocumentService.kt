package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.annotation.DocumentAuditRequired
import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.*
import com.dochyphen.app.api.repository.SharingSessionRepository
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.storage.FileStorageService
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
        val sharingSession = getSharingSession(sessionId)
        validateTitle(title)

        val document = createDocument(title!!, documentType, restrictedType)
        sharingSession.documents.add(document)
        sessionRepo.update(sharingSession)

        val savedDocument = sharingSession.documents.last()
        auditService.logAction(savedDocument, DocumentAuditLogAction.CREATED, authTokenContext.authToken.appUser!!)

        return savedDocument
    }

    @DocumentAuditRequired
    @Transactional
    fun deleteDocument(sessionId: String, documentId: String)
    {
        val sharingSession = getSharingSession(sessionId)
        val document = getDocument(sharingSession, documentId)

        if (document.isDeleted)
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
        val sharingSession = getSharingSession(sessionId)
        val document = getDocument(sharingSession, documentId)
        validateTitle(title)

        if (document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }

        document.title = title!!
        document.type = type
        document.restrictedType = restrictedType
        sessionRepo.update(sharingSession)

        auditService.logAction(document, DocumentAuditLogAction.UPDATE, authTokenContext.authToken.appUser!!)
        return document
    }

    @Transactional
    fun uploadDocument(
        file: File?,
        extension: String?,
        sessionId: String?,
        documentId: String?,
        encryptionMode: DocumentEncryptionMode?
    )
    {
        if (sessionId == null) throw IllegalArgumentException("Session ID cannot be null")
        if (documentId == null) throw IllegalArgumentException("Document ID cannot be null")

        val sharingSession = getSharingSession(sessionId)
        val document = getDocument(sharingSession, documentId)

        if (document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }

        validateFileAndExtension(file, extension)

        val appUser = authTokenContext.authToken.appUser!!
        validateUserPermissions(sharingSession, appUser)

        document.hash = "hash"
        document.type = DocumentType.fromFileExtension(extension!!)
        document.uploadDate = Timestamp.from(Instant.now())
        sessionRepo.update(sharingSession)

        fileStorageService.uploadDocument(file!!, "${document.id}$extension")
        auditService.logAction(document, DocumentAuditLogAction.UPLOAD, appUser)

        sendUploadNotification(sharingSession, appUser, document.title)
    }

    @Transactional
    fun downloadDocument(sessionId: String, documentId: String): File
    {
        val sharingSession = getSharingSession(sessionId)
        val document = getDocument(sharingSession, documentId)

        val fileKey = "${document.id}${DocumentType.toFileExtension(document.type!!)}"
        return fileStorageService.downloadDocument(fileKey)
    }

    private fun getSharingSession(sessionId: String): SharingSession
    {
        return sessionRepo.findById(UUID.fromString(sessionId))
            ?: throw SharingSessionNotFoundException("Sharing session not found")
    }

    private fun getDocument(sharingSession: SharingSession, documentId: String): Document
    {
        return sharingSession.documents.find { it.id == UUID.fromString(documentId) }
            ?: throw SharingSessionDocumentNotFoundException("Document not found")
    }

    private fun validateTitle(title: String?)
    {
        title ?: throw IllegalArgumentException("Title cannot be null")
    }

    private fun validateFileAndExtension(file: File?, extension: String?)
    {
        file ?: throw IllegalArgumentException("File cannot be null")
        extension ?: throw IllegalArgumentException("Extension cannot be null")
    }

    private fun validateUserPermissions(sharingSession: SharingSession, appUser: AppUser)
    {
        if (sharingSession.initiator?.id != appUser.id && sharingSession.recipient?.id != appUser.id)
        {
            throw IllegalArgumentException("User does not have permission to upload document")
        }

        if (sharingSession.initiator?.id != appUser.id && !sharingSession.allowDocumentUpload)
        {
            throw IllegalArgumentException("User does not have permission to upload document")
        }
    }

    private fun createDocument(title: String, documentType: DocumentType?, restrictedType: DocumentType?): Document
    {
        return Document().apply {
            this.title = title
            this.createdDate = Timestamp.from(Instant.now())
            this.updateDate = Timestamp.from(Instant.now())
            this.isDeleted = false
            this.type = documentType
            this.restrictedType = restrictedType
        }
    }

    private fun sendUploadNotification(sharingSession: SharingSession, appUser: AppUser, documentTitle: String)
    {
        val recipientEmail = if (sharingSession.initiator?.id == appUser.id)
        {
            sharingSession.recipient?.email!!
        }
        else
        {
            sharingSession.initiator?.email!!
        }

        emailService.sendEmail(
            recipientEmail,
            "Document uploaded",
            "Document titled '$documentTitle' uploaded by ${appUser.email}"
        )
    }

    fun getDocumentFilePreviewAsPdf(sessionId: String, documentId: String): File
    {
        val sharingSession = getSharingSession(sessionId)
        val document = getDocument(sharingSession, documentId)

        val fileKey = "${document.id}${DocumentType.toFileExtension(document.type!!)}"
        val originalFile: File = fileStorageService.downloadDocument(fileKey)

        if (fileKey.endsWith(".pdf"))
        {
            return originalFile
        }

        val pdfFile = convertToPdf(originalFile)

        return pdfFile
    }

    private fun convertToPdf(file: File): File
    {
        return file
    }
}