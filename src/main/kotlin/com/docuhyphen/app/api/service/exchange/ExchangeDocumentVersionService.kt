package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.document.DocumentVersionContent
import com.docuhyphen.app.api.model.document.DocumentVersionContentDigests
import com.docuhyphen.app.api.model.document.DocumentVersionCreatorMapper
import com.docuhyphen.app.api.model.document.DocumentVersionUpload
import com.docuhyphen.app.api.model.document.DocumentVersionView
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.DocumentVersionRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeDocumentVersionService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val documentVersionRepository: DocumentVersionRepository,
    private val documentAuditService: ExchangeDocumentAuditService,
    private val authTokenContext: AuthTokenContext,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditRecorder: AuditRecorder,
    private val auditOwnerScopeResolver: AuditOwnerScopeResolver,
    private val exchangeFeatureSubscriptionGuard: ExchangeFeatureSubscriptionGuard,
    private val documentVersionRecordingService: DocumentVersionRecordingService,
    private val principalDisplayService: PrincipalDisplayService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentVersionService::class.java)
    }

    @Transactional
    fun createVersion(
        exchangeId: String,
        documentId: String,
        file: File?,
        encryptionMode: DocumentEncryptionMode?,
    ): DocumentVersionView
    {
        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val creator = validateUploadPermission(exchange)
        exchangeFeatureSubscriptionGuard.requireDocumentVersionHistory(exchange)

        val document = exchangeDocument(exchange, documentId)

        if (file == null)
        {
            throw IllegalArgumentException("File is required")
        }

        return view(persistVersion(document, file, creator, encryptionMode ?: DocumentEncryptionMode.INTERNAL))
    }

    /**
     * Records a stored version snapshot for a document whose file has just been uploaded through
     * the document upload flow. The upload flow has already validated permissions, so no
     * additional authorization check is performed here. This keeps every uploaded file (including
     * the very first one) visible in the document's version history.
     *
     * No commercial check runs here either. The snapshot belongs to the upload the caller has
     * already been allowed to make, and dropping it would silently discard the only stored copy
     * of that file.
     */
    @Transactional
    fun recordUploadedFileAsVersion(
        document: Document,
        file: File,
        creator: PrincipalRef,
        encryptionMode: DocumentEncryptionMode,
    ): DocumentVersion
    {
        return persistVersion(document, file, creator, encryptionMode)
    }

    private fun persistVersion(
        document: Document,
        file: File,
        creator: PrincipalRef,
        encryptionMode: DocumentEncryptionMode,
    ): DocumentVersion
    {
        val versionNumber = documentVersionRecordingService.nextVersionNumber(document)
        val extension = document.type?.let { DocumentType.toFileExtension(it) } ?: ""

        val version = documentVersionRecordingService.recordVersion(
            document,
            versionNumber,
            DocumentVersionUpload(
                fileName = "${document.title}_v$versionNumber$extension",
                file = file,
                creator = creator,
                encryptionMode = encryptionMode,
                expectedDigest = DocumentVersionContentDigests.of(file),
            ),
        )

        val creatorDisplay = principalDisplayService.display(creator)
        documentAuditService.logAction(
            document,
            DocumentAuditAction.VERSION_CREATED,
            creatorDisplay.email ?: creatorDisplay.name ?: "System"
        )

        return version
    }

    fun getDocumentVersions(exchangeId: String, documentId: String): List<DocumentVersionView>
    {
        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        validateViewPermission(exchange)

        val document = exchangeDocument(exchange, documentId)

        return documentVersionRepository.findByDocumentId(document.id).map(::view)
    }

    fun getVersionContent(exchangeId: String, documentId: String, versionId: String): DocumentVersionContent
    {
        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        validateDownloadPermission(exchange)

        val document = exchangeDocument(exchange, documentId)

        val version = documentVersionRepository.findById(UUID.fromString(versionId))
            ?.takeIf { it.document.id == document.id }
            ?: throw IllegalArgumentException("Version not found")

        recordVersionDownloadEvent(exchange, document.id, document.title, version.id)

        return documentVersionRecordingService.open(version)
    }

    fun getLatestVersion(exchangeId: String, documentId: String): DocumentVersionView?
    {
        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        validateViewPermission(exchange)

        val document = exchangeDocument(exchange, documentId)

        return documentVersionRepository.findLatestByDocumentId(document.id)?.let(::view)
    }

    private fun exchangeDocument(exchange: Exchange, documentId: String): Document =
        exchangeRepository.findDocumentBySessionIdAndDocumentId(exchange.id, UUID.fromString(documentId))
            ?: throw ExchangeDocumentNotFoundException("Document not found")

    private fun view(version: DocumentVersion): DocumentVersionView =
        DocumentVersionView(version, principalDisplayService.display(DocumentVersionCreatorMapper.read(version)))

    fun resolveCommentVersion(documentId: UUID, versionId: String?): DocumentVersion?
    {
        if (versionId == null)
        {
            return documentVersionRepository.findLatestByDocumentId(documentId)
        }

        val parsedVersionId = runCatching { UUID.fromString(versionId) }
            .getOrElse { throw IllegalArgumentException("Invalid document version identifier") }
        val version = documentVersionRepository.findById(parsedVersionId)
            ?: throw IllegalArgumentException("Document version not found")
        require(version.document.id == documentId) { "Document version does not belong to this document" }
        return version
    }

    private fun validateUploadPermission(exchange: Exchange): PrincipalRef
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw IllegalArgumentException("Permission to upload document version not granted")
        val principal = PrincipalRef.user(appUser.id)

        val decision = authorizationService.authorize(
            principal = principal,
            action = Action.DOCUMENT_UPLOAD,
            resource = ResourceRef.exchange(exchange.id),
            context = authorizationContextFactory.currentContext(),
        )

        if (decision is Decision.Deny)
        {
            throw IllegalArgumentException("Permission to upload document version not granted")
        }

        return principal
    }

    private fun validateViewPermission(exchange: Exchange)
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw IllegalArgumentException("Permission to view document versions not granted")

        val decision = authorizationService.authorize(
            principal = PrincipalRef.user(appUser.id),
            action = Action.DOCUMENT_VIEW,
            resource = ResourceRef.exchange(exchange.id),
            context = authorizationContextFactory.currentContext(),
        )

        if (decision is Decision.Deny)
        {
            throw IllegalArgumentException("Permission to view document versions not granted")
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
                    owner = auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, exchange.id),
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
