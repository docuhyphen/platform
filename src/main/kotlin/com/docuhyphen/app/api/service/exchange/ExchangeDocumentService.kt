package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.RealtimeMessage
import com.docuhyphen.app.api.realtime.RealtimeMessageType
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.ShareConstraints
import com.docuhyphen.app.api.service.communication.AppNotificationService
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.communication.EmailTemplateService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.storage.FileStorageService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@ApplicationScoped
class ExchangeDocumentService @Inject constructor(
    private val sessionRepo: ExchangeRepository,
    private val auditService: ExchangeDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val emailService: EmailService,
    private val emailTemplateService: EmailTemplateService,
    private val configurationService: ConfigurationService,
    private val fileStorageService: FileStorageService,
    private val appNotificationService: AppNotificationService,
    private val realtimeEventService: RealtimeEventService,
    private val shareService: ShareService,
    private val appUserService: AppUserService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    private enum class DocumentAction
    {
        ADD,
        UPDATE,
        DELETE,
        UPLOAD
    }

    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentService::class.java)
        private val emailDateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss 'UTC'")
                .withZone(ZoneOffset.UTC)
    }

    @Transactional
    fun addDocument(
        exchangeId: String,
        title: String?,
        documentType: DocumentType?,
        restrictedType: DocumentType?
    ): Document
    {
        val exchange = getExchange(exchangeId)
        validateSessionMutability(exchange)
        validateUserPermissions(exchange, authTokenContext.authToken.appUser!!, DocumentAction.ADD)
        validateTitle(title)
        validateDocumentTypeRestriction(documentType, restrictedType)

        val document = createDocument(title!!, documentType, restrictedType)
        exchange.documents.add(document)
        sessionRepo.update(exchange)

        val savedDocument = exchange.documents.last()
        auditService.logAction(savedDocument, DocumentAuditLogAction.CREATED, authTokenContext.authToken.appUser!!)

        broadcastDocumentEvent(exchange.id, RealtimeMessageType.EXCHANGE_DOCUMENT_ADDED, savedDocument.id)

        return savedDocument
    }

    @Transactional
    fun deleteDocument(exchangeId: String, documentId: String)
    {
        val exchange = getExchange(exchangeId)
        validateSessionMutability(exchange)
        val document = getDocument(exchange, documentId)
        validateUserPermissions(exchange, authTokenContext.authToken.appUser!!, DocumentAction.DELETE)

        if (document.isDeleted)
        {
            throw ExchangeDocumentNotFoundException("Document not found")
        }

        document.isDeleted = true
        document.updateDate = Timestamp.from(Instant.now())
        sessionRepo.update(exchange)

        auditService.logAction(document, DocumentAuditLogAction.DELETE, authTokenContext.authToken.appUser!!)

        broadcastDocumentEvent(exchange.id, RealtimeMessageType.EXCHANGE_DOCUMENT_REMOVED, document.id)
    }

    @Transactional
    fun updateDocument(
        exchangeId: String,
        documentId: String,
        title: String?,
        type: DocumentType?,
        restrictedType: DocumentType?
    ): Document
    {
        val exchange = getExchange(exchangeId)
        validateSessionMutability(exchange)
        val document = getDocument(exchange, documentId)
        validateUserPermissions(exchange, authTokenContext.authToken.appUser!!, DocumentAction.UPDATE)
        validateTitle(title)
        validateDocumentTypeRestriction(type, restrictedType)

        if (document.isDeleted)
        {
            throw ExchangeDocumentNotFoundException("Document not found")
        }

        document.title = title!!
        // Keep existing file type when metadata-only updates omit `type`.
        document.type = type ?: document.type
        document.restrictedType = restrictedType
        sessionRepo.update(exchange)

        auditService.logAction(document, DocumentAuditLogAction.UPDATE, authTokenContext.authToken.appUser!!)

        broadcastDocumentEvent(exchange.id, RealtimeMessageType.EXCHANGE_DOCUMENT_UPDATED, document.id)

        return document
    }

    @Transactional
    fun updateDocument(
        exchangeId: String,
        document: Document
    ): Document
    {
        val exchange = getExchange(exchangeId)
        validateTitle(document.title)

        if (document.isDeleted)
        {
            throw ExchangeDocumentNotFoundException("Document not found")
        }

        sessionRepo.update(exchange)
        auditService.logAction(document, DocumentAuditLogAction.UPDATE, authTokenContext.authToken.appUser!!)
        return document
    }

    @Transactional
    fun uploadDocument(
        file: File?,
        extension: String?,
        exchangeId: String?,
        documentId: String?,
        encryptionMode: DocumentEncryptionMode?
    ): Document
    {
        if (exchangeId == null) throw IllegalArgumentException("Session ID cannot be null")
        if (documentId == null) throw IllegalArgumentException("Document ID cannot be null")

        val exchange = getExchange(exchangeId)
        validateSessionMutability(exchange)
        val document = getDocument(exchange, documentId)

        if (document.isDeleted)
        {
            throw ExchangeDocumentNotFoundException("Document not found")
        }

        validateFileAndExtension(file, extension, document.restrictedType)

        val appUser = authTokenContext.authToken.appUser!!
        validateUserPermissions(exchange, appUser, DocumentAction.UPLOAD)

        document.hash = "hash"
        document.type = DocumentType.fromFileExtension(extension!!)
        document.uploadDate = Timestamp.from(Instant.now())
        document.lastUpdatedBy = appUser
        sessionRepo.update(exchange)

        fileStorageService.uploadDocument(file!!, "${document.id}$extension")
        updateDocument(exchangeId, document)

        auditService.logAction(document, DocumentAuditLogAction.UPLOAD, appUser)

        sendUploadNotification(exchange, appUser, document)

        return document
    }

    @Transactional
    fun uploadNoAuthDocument(
        file: File?,
        extension: String?,
        exchangeId: String?,
        documentId: String?,
        encryptionMode: DocumentEncryptionMode?
    ): Document
    {
        if (exchangeId == null) throw IllegalArgumentException("Session ID cannot be null")
        if (documentId == null) throw IllegalArgumentException("Document ID cannot be null")

        val exchange = getExchange(exchangeId)
        val document = getDocument(exchange, documentId)

        if (exchange.requireRecipientSignIn)
        {
            logger.warn("Attempted no-auth upload for session {} after sign-in requirement was enabled", exchange.id)
            throw IllegalArgumentException("This request now requires sign in. Please sign in to continue.")
        }

        if (exchange.status != ExchangeStatus.ACCEPTED_STARTED && exchange.status != ExchangeStatus.INITIATED)
        {
            logger.error("Attempted to update a exchange with an invalid status")
            throw IllegalArgumentException("Exchange not found")
        }

        if (exchange.status == ExchangeStatus.ENDED || exchange.status == ExchangeStatus.REJECTED)
        {
            logger.error("Attempted to update a exchange that has ended or rejected: ${exchange.status}")
            throw IllegalArgumentException("Exchange not found")
        }

        if (document.isDeleted)
        {
            throw ExchangeDocumentNotFoundException("Document not found")
        }

        if (!shareService.recipientConstraintAllows(exchange.id, "allow_document_upload"))
        {
            throw IllegalArgumentException("Permission to upload document not granted")
        }

        ensureNoAuthAccessWindowActive(exchange)

        validateFileAndExtension(file, extension, document.restrictedType)


        document.hash = "hash"
        document.type = DocumentType.fromFileExtension(extension!!)
        document.uploadDate = Timestamp.from(Instant.now())
        document.lastUpdatedBy = null
        sessionRepo.update(exchange)

        fileStorageService.uploadDocument(file!!, "${document.id}$extension")
        resolveRecipientEmail(exchange.id)?.let { auditService.logAction(document, DocumentAuditLogAction.UPLOAD, it) }
//        sendUploadNotification(exchange, appUser, document.title)

        broadcastDocumentEvent(exchange.id, RealtimeMessageType.EXCHANGE_DOCUMENT_UPDATED, document.id)

        return document
    }

    private fun ensureNoAuthAccessWindowActive(exchange: Exchange)
    {
        val verifiedAt = exchange.noAuthAccessVerifiedAt
            ?: throw IllegalArgumentException("Your access verification has expired. Ask the person who requested documents to resend an access code in Manage Access.")
        val validityDays = exchange.noAuthAccessValidityDays
            .coerceAtLeast(1)
        val validUntil = verifiedAt.toInstant().plusSeconds(validityDays.toLong() * 24 * 60 * 60)
        if (validUntil.isBefore(Instant.now()))
        {
            throw IllegalArgumentException("Your access verification has expired. Ask the person who requested documents to resend an access code in Manage Access.")
        }
    }

    @Transactional
    fun downloadDocument(exchangeId: String, documentId: String): File
    {
        val exchange = getExchange(exchangeId)
        validateDownloadPermission(exchange)
        val document = getDocument(exchange, documentId)

        val constraintsJson = shareService.recipientConstraintsJson(exchange.id)
        validateDownloadFormat(document, constraintsJson)

        val fileKey = "${document.id}${DocumentType.toFileExtension(document.type!!)}"
        return fileStorageService.downloadDocument(fileKey)
    }

    @Transactional
    fun downloadNoAuthSessionDocument(exchangeId: String?, documentId: String?): File
    {
        if (exchangeId == null) throw IllegalArgumentException("Session ID cannot be null")
        if (documentId == null) throw IllegalArgumentException("Document ID cannot be null")

        val exchange = getExchange(exchangeId)
        val document = getDocument(exchange, documentId)

        if (exchange.requireRecipientSignIn)
        {
            logger.warn("Attempted no-auth download for session {} after sign-in requirement was enabled", exchange.id)
            throw IllegalArgumentException("This request now requires sign in. Please sign in to continue.")
        }

        if (exchange.status != ExchangeStatus.ACCEPTED_STARTED && exchange.status != ExchangeStatus.INITIATED)
        {
            logger.error("Attempted to update a exchange with an invalid status")
            throw IllegalArgumentException("Exchange not found")
        }

        if (exchange.status == ExchangeStatus.ENDED || exchange.status == ExchangeStatus.REJECTED)
        {
            logger.error("Attempted to update a exchange that has ended or rejected: ${exchange.status}")
            throw IllegalArgumentException("Exchange not found")
        }

        ensureNoAuthAccessWindowActive(exchange)

        if (document.isDeleted)
        {
            throw ExchangeDocumentNotFoundException("Document not found")
        }

        val fileKey = "${document.id}${DocumentType.toFileExtension(document.type!!)}"
        return fileStorageService.downloadDocument(fileKey)
    }

    @Transactional
    fun downloadDocumentsAsZip(exchangeId: String, documentIds: List<String>): File
    {
        val exchange = getExchange(exchangeId)
        validateDownloadPermission(exchange)
        val documents = documentIds.map { getDocument(exchange, it) }

        val constraintsJson = shareService.recipientConstraintsJson(exchange.id)
        val allowedFormats = ShareConstraints.parse(constraintsJson).allowedDownloadFormats

        if (allowedFormats == null)
        {
            // No format restriction, fast path using original files
            val fileKeys = documents.map { "${it.id}${DocumentType.toFileExtension(it.type!!)}" }
            return fileStorageService.downloadDocumentsAsZip(fileKeys)
        }

        return buildZipRespectingFormats(exchangeId, documents, allowedFormats)
    }

    /**
     * Builds a ZIP file that includes originals for allowed formats and
     * PDF-converted versions for restricted formats.
     */
    private fun buildZipRespectingFormats(
        exchangeId: String,
        documents: List<Document>,
        allowedFormats: List<String>,
    ): File
    {
        val tempZip = File.createTempFile("documents-", ".zip")
        val tempFiles = mutableListOf<File>()

        try
        {
            java.util.zip.ZipOutputStream(tempZip.outputStream()).use { zos ->
                for (document in documents)
                {
                    val docType = document.type?.name
                    if (docType != null && docType in allowedFormats)
                    {
                        // Original format is allowed, include as-is
                        val fileKey = "${document.id}${DocumentType.toFileExtension(document.type!!)}"
                        val file = fileStorageService.downloadDocument(fileKey)
                        tempFiles.add(file)
                        val entryName = "${document.title ?: document.id}${DocumentType.toFileExtension(document.type!!)}"
                        zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    else
                    {
                        // Format not allowed, convert to PDF via LibreOffice
                        val pdfFile = getDocumentFilePreviewAsPdf(exchangeId, document.id.toString())
                        tempFiles.add(pdfFile)
                        val entryName = "${document.title ?: document.id}.pdf"
                        zos.putNextEntry(java.util.zip.ZipEntry(entryName))
                        pdfFile.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        finally
        {
            tempFiles.forEach { runCatching { it.delete() } }
        }

        return tempZip
    }

    private fun getExchange(exchangeId: String): Exchange
    {
        return sessionRepo.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")
    }

    private fun getDocument(exchange: Exchange, documentId: String): Document
    {
        val parsedDocumentId = UUID.fromString(documentId)
        return sessionRepo.findDocumentBySessionIdAndDocumentId(exchange.id, parsedDocumentId)
            ?: throw ExchangeDocumentNotFoundException("Document not found")
    }

    private fun validateTitle(title: String?)
    {
        title ?: throw IllegalArgumentException("Title cannot be null")
    }

    private fun validateFileAndExtension(file: File?, extension: String?, restrictedType: DocumentType?)
    {
        // Validate null checks
        file ?: throw IllegalArgumentException("File cannot be null")
        extension ?: throw IllegalArgumentException("Extension cannot be null")

        // Clean up the extension format (remove leading dots if present)
        val cleanExtension = if (extension.startsWith(".")) extension else ".$extension"

        // Check if extension is supported using the DocumentType enum
        val documentType = DocumentType.fromFileExtension(cleanExtension)
            ?: throw IllegalArgumentException("Unsupported file extension: $cleanExtension")

        if (restrictedType != null && documentType != restrictedType)
        {
            throw IllegalArgumentException("File type must be ${DocumentType.toFileExtension(restrictedType)}")
        }

        // Check file size (50MB limit)
        val maxSizeBytes = 52_428_800L // 50MB
        if (file.length() > maxSizeBytes)
        {
            throw IllegalArgumentException("File is too large. Maximum size allowed is 50MB")
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

    private fun validateSessionMutability(exchange: Exchange)
    {
        if (exchange.status != ExchangeStatus.INITIATED &&
            exchange.status != ExchangeStatus.ACCEPTED_STARTED
        )
        {
            throw IllegalArgumentException("Sharing session is not editable")
        }
    }

    private fun validateDocumentTypeRestriction(type: DocumentType?, restrictedType: DocumentType?)
    {
        if (type != null && restrictedType != null && type != restrictedType)
        {
            throw IllegalArgumentException("Document type must match restricted type")
        }
    }

    /** Email of the session's primary recipient, resolved from its recipient Share. */
    private fun resolveRecipientEmail(exchangeId: UUID): String? =
        shareService.primaryRecipientUserId(exchangeId)?.let { appUserService.getById(it)?.email }

    private fun validateUserPermissions(exchange: Exchange, appUser: AppUser, action: DocumentAction)
    {
        val required = when (action)
        {
            DocumentAction.ADD, DocumentAction.UPLOAD -> Action.DOCUMENT_UPLOAD
            DocumentAction.UPDATE -> Action.DOCUMENT_UPDATE
            DocumentAction.DELETE -> Action.DOCUMENT_DELETE
        }

        val decision = authorizationService.authorize(
            principal = PrincipalRef.user(appUser.id),
            action = required,
            resource = ResourceRef.session(exchange.id),
            context = authorizationContextFactory.currentContext(),
        )

        if (decision is Decision.Deny)
        {
            throw IllegalArgumentException("Permission to perform document action not granted")
        }
    }

    /**
     * Authenticated download gate. DOCUMENT_DOWNLOAD is conditional: VIEWER/PARTICIPANT
     * recipients only hold it when their share's `can_download` constraint opts in, and an
     * explicit `can_download=false` strips it from richer roles (see [ShareConstraints]).
     * The no-auth download path is governed separately by recipient constraints.
     */
    private fun validateDownloadPermission(exchange: Exchange)
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw IllegalArgumentException("Permission to download document not granted")

        val decision = authorizationService.authorize(
            principal = PrincipalRef.user(appUser.id),
            action = Action.DOCUMENT_DOWNLOAD,
            resource = ResourceRef.session(exchange.id),
            context = authorizationContextFactory.currentContext(),
        )

        if (decision is Decision.Deny)
        {
            throw IllegalArgumentException("Permission to download document not granted")
        }
    }

    /**
     * Blocks the original-file download when the document's type is not in the
     * share's `allowed_download_formats` list. Null list means no restriction.
     */
    private fun validateDownloadFormat(document: Document, constraintsJson: String?)
    {
        val allowed = ShareConstraints.parse(constraintsJson).allowedDownloadFormats
            ?: return                          // null = no restriction, always pass
        val docType = document.type?.name ?: return
        if (docType !in allowed)
        {
            throw io.quarkus.security.ForbiddenException(
                "Downloading format '$docType' is not permitted. " +
                "Use the 'Download as PDF' option instead."
            )
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

    private fun sendUploadNotification(exchange: Exchange, appUser: AppUser, document: Document)
    {
        val recipientEmail = if (exchange.initiator?.id == appUser.id)
        {
            resolveRecipientEmail(exchange.id)
        }
        else
        {
            exchange.initiator?.email
        } ?: return

        val uploadedAtInstant = document.uploadDate?.toInstant() ?: Instant.now()
        val model = mapOf(
            "name" to (exchange.name ?: "Sharing session"),
            "documentTitle" to (document.title ?: "Document"),
            "uploaderEmail" to appUser.email,
            "uploadedAt" to emailDateFormatter.format(uploadedAtInstant),
            "exchangeId" to exchange.id.toString(),
            "documentId" to document.id.toString(),
            "exchangeLink" to "${configurationService.baseUrl}/exchanges?s=${exchange.id}&d=${document.id}",
            "appName" to configurationService.emailSubjectTitle,
        )

        val subject = "Document uploaded: ${document.title ?: "Document"}"
        val body = emailTemplateService.renderTemplate("exchange-document-uploaded.ftl", model)
        emailService.sendEmail(recipientEmail, subject, body, useHtml = true)

        appNotificationService.sendNotification(
            recipientEmail,
            "Document uploaded",
            "${document.title ?: "Document"} uploaded in session ${exchange.name} by ${appUser.email}"
        )
    }

    fun getDocumentFilePreviewAsPdf(exchangeId: String, documentId: String): File
    {
        val exchange = getExchange(exchangeId)
        val document = getDocument(exchange, documentId)

        val fileKey = "${document.id}${DocumentType.toFileExtension(document.type!!)}"
        val originalFile = fileStorageService.downloadDocument(fileKey)

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

        val process = try
        {
            ProcessBuilder(command).start()
        }
        catch (e: java.io.IOException)
        {
            throw DocumentPreviewConversionException(
                "LibreOffice (soffice) is not available on this server. Original file can still be downloaded."
            )
        }
        val exitCode = process.waitFor()

        if (exitCode != 0 || !pdfFile.exists())
        {
            throw DocumentPreviewConversionException("PDF conversion failed for file: ${originalFile.name}")
        }

        return pdfFile
    }

    private fun broadcastDocumentEvent(exchangeId: UUID, type: String, documentId: UUID)
    {
        runCatching {
            realtimeEventService.broadcastToExchange(
                exchangeId,
                RealtimeMessage(
                    type = type,
                    exchangeId = exchangeId.toString(),
                    documentId = documentId.toString(),
                )
            )
        }.onFailure { e ->
            logger.warn("Failed to broadcast {} for session={} document={}", type, exchangeId, documentId, e)
        }
    }
}

class DocumentPreviewConversionException(message: String) : RuntimeException(message)