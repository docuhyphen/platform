package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.DocumentAuditAction
import com.docuhyphen.app.api.model.entity.DocumentType
import com.docuhyphen.app.api.model.entity.DocumentVersion
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.AppUserRepository
import com.docuhyphen.app.api.repository.DocumentVersionRepository
import com.docuhyphen.app.api.repository.ExchangeDocumentRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
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
    private val appUserRepository: AppUserRepository,
    private val documentAuditService: ExchangeDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditRecorder: AuditRecorder,
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
        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        validateUploadPermission(exchange)

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
            this.createdBy = currentUserEmail?.let { appUserRepository.findByEmail(it) }
        }

        documentVersionRepository.save(version)

        // Log the action
        documentAuditService.logAction(
            document,
            DocumentAuditAction.VERSION_CREATED,
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
        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        validateDownloadPermission(exchange)

        val document = exchangeDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw ExchangeDocumentNotFoundException("Document not found")

        val version = documentVersionRepository.findById(UUID.fromString(versionId))
            ?: throw IllegalArgumentException("Version not found")

        recordVersionDownloadEvent(exchange, document.id, document.title, version.id)

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

    private fun validateUploadPermission(exchange: Exchange)
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw IllegalArgumentException("Permission to upload document version not granted")

        val decision = authorizationService.authorize(
            principal = PrincipalRef.user(appUser.id),
            action = Action.DOCUMENT_UPLOAD,
            resource = ResourceRef.exchange(exchange.id),
            context = authorizationContextFactory.currentContext(),
        )

        if (decision is Decision.Deny)
        {
            throw IllegalArgumentException("Permission to upload document version not granted")
        }
    }

    private fun validateDownloadPermission(exchange: Exchange)
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw IllegalArgumentException("Permission to download document version not granted")

        val decision = authorizationService.authorize(
            principal = PrincipalRef.user(appUser.id),
            action = Action.DOCUMENT_DOWNLOAD,
            resource = ResourceRef.exchange(exchange.id),
            context = authorizationContextFactory.currentContext(),
        )

        if (decision is Decision.Deny)
        {
            throw IllegalArgumentException("Permission to download document version not granted")
        }
    }

    /**
     * Records historical-version downloads. Failures
     * are caught and logged, never propagated, so audit plumbing can never break an actual
     * version-file download - same catch-and-log style as
     * [ExchangeDocumentAuditService.recordOnRecorder].
     */
    private fun recordVersionDownloadEvent(exchange: Exchange, documentId: UUID, documentTitle: String?, versionId: UUID)
    {
        val actorId = authTokenContext.authToken.appUser?.id
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = AuditEventType.DOCUMENT_VERSION_DOWNLOAD.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    actorRole = "APP_USER",
                    targetType = ResourceType.DOCUMENT.name,
                    targetId = documentId.toString(),
                    targetLabel = documentTitle,
                    owner = exchange.ownerOrganizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    payload = mapOf(
                        "version_id" to versionId.toString(),
                        "exchange_id" to exchange.id.toString(),
                        "exchange_name" to (exchange.name ?: "Exchange"),
                    ),
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("ExchangeDocumentVersionService: AuditRecorder rejected draft for version download: {}", e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("ExchangeDocumentVersionService: AuditRecorder capture failed (fail-closed) for version download: {}", e.message, e)
        }
    }
}
