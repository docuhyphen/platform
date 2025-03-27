package com.dochyphen.app.api.service.sharingsession

import com.dochyphen.app.api.exception.SharingSessionDocumentNotFoundException
import com.dochyphen.app.api.exception.SharingSessionNotFoundException
import com.dochyphen.app.api.interceptor.AuthTokenContext
import com.dochyphen.app.api.model.entity.*
import com.dochyphen.app.api.repository.SharingSessionRepository
import com.dochyphen.app.api.service.communication.AppNotificationService
import com.dochyphen.app.api.service.communication.EmailService
import com.dochyphen.app.api.service.storage.FileStorageService
import io.quarkus.security.ForbiddenException
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
    private val fileStorageService: FileStorageService,
    private val appNotificationService: AppNotificationService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SharingSessionDocumentService::class.java)
    }

    @Transactional
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
    ): Document
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

        return document
    }

    @Transactional
    fun uploadNoAuthDocument(
        file: File?,
        extension: String?,
        sessionId: String?,
        documentId: String?,
        encryptionMode: DocumentEncryptionMode?
    ): Document
    {
        if (sessionId == null) throw IllegalArgumentException("Session ID cannot be null")
        if (documentId == null) throw IllegalArgumentException("Document ID cannot be null")

        val sharingSession = getSharingSession(sessionId)
        val document = getDocument(sharingSession, documentId)

        if (sharingSession.requireRecipientSignIn)
        {
            logger.error("Attempted to access a sharing session that requires recipient sign-in")
            throw ForbiddenException("Sharing session not found")
        }

        if (sharingSession.status != SharingSessionStatus.ACCEPTED_STARTED && sharingSession.status != SharingSessionStatus.INITIATED)
        {
            logger.error("Attempted to update a sharing session with an invalid status")
            throw IllegalArgumentException("Sharing session not found")
        }

        if (sharingSession.status == SharingSessionStatus.ENDED || sharingSession.status == SharingSessionStatus.REJECTED)
        {
            logger.error("Attempted to update a sharing session that has ended or rejected: ${sharingSession.status}")
            throw IllegalArgumentException("Sharing session not found")
        }

        if (document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }

        validateFileAndExtension(file, extension)


        document.hash = "hash"
        document.type = DocumentType.fromFileExtension(extension!!)
        document.uploadDate = Timestamp.from(Instant.now())
        sessionRepo.update(sharingSession)

        fileStorageService.uploadDocument(file!!, "${document.id}$extension")
        sharingSession.recipient?.email?.let { auditService.logAction(document, DocumentAuditLogAction.UPLOAD, it) }
//        sendUploadNotification(sharingSession, appUser, document.title)

        return document
    }

    @Transactional
    fun downloadDocument(sessionId: String, documentId: String): File
    {
        val sharingSession = getSharingSession(sessionId)
        val document = getDocument(sharingSession, documentId)

        val fileKey = "${document.id}${DocumentType.toFileExtension(document.type!!)}"
        return fileStorageService.downloadDocument(fileKey)
    }

    @Transactional
    fun downloadNoAuthSessionDocument(sessionId: String?, documentId: String?): File
    {
        if (sessionId == null) throw IllegalArgumentException("Session ID cannot be null")
        if (documentId == null) throw IllegalArgumentException("Document ID cannot be null")

        val sharingSession = getSharingSession(sessionId)
        val document = getDocument(sharingSession, documentId)

        if (sharingSession.requireRecipientSignIn)
        {
            logger.error("Attempted to access a sharing session that requires recipient sign-in")
            throw ForbiddenException("Sharing session not found")
        }

        if (sharingSession.status != SharingSessionStatus.ACCEPTED_STARTED && sharingSession.status != SharingSessionStatus.INITIATED)
        {
            logger.error("Attempted to update a sharing session with an invalid status")
            throw IllegalArgumentException("Sharing session not found")
        }

        if (sharingSession.status == SharingSessionStatus.ENDED || sharingSession.status == SharingSessionStatus.REJECTED)
        {
            logger.error("Attempted to update a sharing session that has ended or rejected: ${sharingSession.status}")
            throw IllegalArgumentException("Sharing session not found")
        }

        if (document.isDeleted)
        {
            throw SharingSessionDocumentNotFoundException("Document not found")
        }

        val fileKey = "${document.id}${DocumentType.toFileExtension(document.type!!)}"
        return fileStorageService.downloadDocument(fileKey)
    }

    @Transactional
    fun downloadDocumentsAsZip(sessionId: String, documentIds: List<String>): File
    {
        val sharingSession = getSharingSession(sessionId)
        val documents = documentIds.map { getDocument(sharingSession, it) }

        val fileKeys = documents.map { "${it.id}${DocumentType.toFileExtension(it.type!!)}" }
        return fileStorageService.downloadDocumentsAsZip(fileKeys)
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
        // Validate null checks
        file ?: throw IllegalArgumentException("File cannot be null")
        extension ?: throw IllegalArgumentException("Extension cannot be null")

        // Clean up the extension format (remove leading dots if present)
        val cleanExtension = if (extension.startsWith(".")) extension else ".$extension"

        // Check if extension is supported using the DocumentType enum
        val documentType = DocumentType.fromFileExtension(cleanExtension)
            ?: throw IllegalArgumentException("Unsupported file extension: $cleanExtension")

        // Check file size (10MB limit)
        val maxSizeBytes = 10_485_760L // 10MB
        if (file.length() > maxSizeBytes)
        {
            throw IllegalArgumentException("File is too large. Maximum size allowed is 10MB")
        }

        // Check if file is not empty
        if (file.length() == 0L)
        {
            throw IllegalArgumentException("File cannot be empty")
        }

        // Minimum size check - prevent fake/corrupted files
        val minSizeBytes = 100L
        if (file.length() < minSizeBytes)
        {
            throw IllegalArgumentException("File is too small. Minimum size required is 100 bytes")
        }

        // Validate file content type matches extension using Apache Tika
//        validateFileContentType(file, documentType)
    }

    private fun validateFileContentType(file: File, expectedType: DocumentType)
    {
        try
        {
            val tika = org.apache.tika.Tika()
            val detectedMimeType = tika.detect(file)

            val validMimeType = when (expectedType)
            {
                DocumentType.PDF -> "application/pdf"
                DocumentType.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                DocumentType.DOC -> "application/msword"
                DocumentType.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                DocumentType.XLS -> "application/vnd.ms-excel"
                DocumentType.PPTX -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                DocumentType.PPT -> "application/vnd.ms-powerpoint"
                DocumentType.PNG -> "image/png"
                DocumentType.JPG -> "image/jpeg"
            }

            if (!detectedMimeType.startsWith(validMimeType))
            {
                throw IllegalArgumentException("File content doesn't match the extension. Expected $validMimeType but found $detectedMimeType")
            }
        }
        catch (e: Exception)
        {
            when (e)
            {
                is IllegalArgumentException -> throw e
                else -> throw IllegalArgumentException("Error validating file content: ${e.message}")
            }
        }
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

        appNotificationService.sendNotification(
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

    private fun convertToPdf(originalFile: File): File
    {
        logger.info("CONVERTING....")

        val pdfFile = File(originalFile.parent, originalFile.nameWithoutExtension + ".pdf")
        val command = listOf(
            "soffice",
            "--headless",
            "--convert-to",
            "pdf",
            originalFile.absolutePath,
            "--outdir",
            originalFile.parent
        )

        val process = ProcessBuilder(command).start()
        val exitCode = process.waitFor()

        if (exitCode != 0 || !pdfFile.exists())
        {
            throw IllegalStateException("PDF conversion failed for file: ${originalFile.name}")
        }

        return pdfFile
    }
}