package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.document.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.document.DocumentVersionRecordingService
import com.docuhyphen.app.api.service.identity.PrincipalDisplayService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

@ApplicationScoped
class ExchangeDocumentVersionService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
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
        private const val UPLOAD_REFUSAL = "Permission to upload document version not granted"
        private const val VIEW_REFUSAL = "Permission to view document versions not granted"
        private const val DOWNLOAD_REFUSAL = "Permission to download document version not granted"
    }

    @Transactional
    fun createVersion(
        exchangeId: String,
        documentId: String,
        file: File?,
        encryptionMode: DocumentEncryptionMode?,
    ): DocumentVersionView
    {
        val exchangeUuid = UUID.fromString(exchangeId)
        val creator = requireExchangePermission(exchangeUuid, Action.DOCUMENT_UPLOAD, UPLOAD_REFUSAL)
        val exchange = exchangeRepository.findById(exchangeUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")
        exchangeFeatureSubscriptionGuard.requireDocumentVersionHistory(exchange)

        val document = exchangeDocument(exchange, documentId)

        if (file == null)
        {
            throw IllegalArgumentException("File is required")
        }

        return view(persistVersion(document, file, creator, encryptionMode ?: DocumentEncryptionMode.INTERNAL))
    }

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
        val exchangeUuid = UUID.fromString(exchangeId)
        requireExchangePermission(exchangeUuid, Action.DOCUMENT_VIEW, VIEW_REFUSAL)
        val exchange = exchangeRepository.findById(exchangeUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")

        val document = exchangeDocument(exchange, documentId)

        return documentVersionRecordingService.versionsOf(document.id).map(::view)
    }

    fun getVersionContent(exchangeId: String, documentId: String, versionId: String): DocumentVersionContent
    {
        val exchangeUuid = UUID.fromString(exchangeId)
        requireExchangePermission(exchangeUuid, Action.DOCUMENT_DOWNLOAD, DOWNLOAD_REFUSAL)
        val exchange = exchangeRepository.findById(exchangeUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")

        val document = exchangeDocument(exchange, documentId)

        val version = documentVersionRecordingService.findVersion(UUID.fromString(versionId))
            ?.takeIf { it.document?.id == document.id }
            ?: throw IllegalArgumentException("Version not found")

        recordVersionDownloadEvent(exchange, document.id, document.title, version.id)

        return documentVersionRecordingService.open(version)
    }

    fun getLatestVersion(exchangeId: String, documentId: String): DocumentVersionView?
    {
        val exchangeUuid = UUID.fromString(exchangeId)
        requireExchangePermission(exchangeUuid, Action.DOCUMENT_VIEW, VIEW_REFUSAL)
        val exchange = exchangeRepository.findById(exchangeUuid)
            ?: throw ExchangeNotFoundException("Exchange not found")

        val document = exchangeDocument(exchange, documentId)

        return documentVersionRecordingService.latestVersionOf(document.id)?.let(::view)
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
            return documentVersionRecordingService.latestVersionOf(documentId)
        }

        val parsedVersionId = runCatching { UUID.fromString(versionId) }
            .getOrElse { throw IllegalArgumentException("Invalid document version identifier") }
        val version = documentVersionRecordingService.findVersion(parsedVersionId)
            ?: throw IllegalArgumentException("Document version not found")
        require(version.document?.id == documentId) { "Document version does not belong to this document" }
        return version
    }

    private fun requireExchangePermission(exchangeId: UUID, action: Action, refusal: String): PrincipalRef
    {
        val appUser = authTokenContext.authToken.appUser
            ?: throw ExchangeNotFoundException("Exchange not found")
        val principal = PrincipalRef.user(appUser.id)
        val context = authorizationContextFactory.currentContext()
        val exchange = ResourceRef.exchange(exchangeId)

        if (authorizationService.authorize(principal, action, exchange, context) is Decision.Allow)
        {
            return principal
        }
        if (authorizationService.authorize(principal, Action.EXCHANGE_VIEW, exchange, context) is Decision.Allow)
        {
            throw ForbiddenException(refusal)
        }
        throw ExchangeNotFoundException("Exchange not found")
    }

    private fun recordVersionDownloadEvent(
        exchange: Exchange,
        documentId: UUID,
        documentTitle: String?,
        versionId: UUID
    )
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
            logger.warn(
                "ExchangeDocumentVersionService: AuditRecorder rejected draft for version download: {}",
                e.message
            )
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error(
                "ExchangeDocumentVersionService: AuditRecorder capture failed (fail-closed) for version download: {}",
                e.message,
                e
            )
        }
    }
}
