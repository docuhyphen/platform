package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeDocumentNotFoundException
import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.dto.DocumentAuditDetailedDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentAuditLog
import com.docuhyphen.app.api.model.entity.DocumentAuditLogAction
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.DocumentAuditRepository
import com.docuhyphen.app.api.repository.ExchangeDocumentRepository
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class ExchangeDocumentAuditService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val exchangeDocumentRepository: ExchangeDocumentRepository,
    private val documentAuditRepository: DocumentAuditRepository,
    private val entityManager: EntityManager,
    private val auditRecorder: AuditRecorder,
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentAuditService::class.java)

        private val ACTION_TO_EVENT_TYPE: Map<DocumentAuditLogAction, AuditEventType> = mapOf(
            DocumentAuditLogAction.UPLOAD to AuditEventType.DOCUMENT_UPLOAD,
            DocumentAuditLogAction.DOWNLOAD to AuditEventType.DOCUMENT_DOWNLOAD,
            DocumentAuditLogAction.VIEW to AuditEventType.DOCUMENT_VIEW,
            DocumentAuditLogAction.CREATED to AuditEventType.DOCUMENT_CREATED,
            DocumentAuditLogAction.DELETE to AuditEventType.DOCUMENT_DELETE,
            DocumentAuditLogAction.UPDATE to AuditEventType.DOCUMENT_UPDATE,
            DocumentAuditLogAction.COMMENT to AuditEventType.DOCUMENT_COMMENT,
            DocumentAuditLogAction.VERSION_CREATED to AuditEventType.DOCUMENT_VERSION_CREATED,
        )

        private val EVENT_TYPE_KEY_TO_ACTION: Map<String, String> =
            ACTION_TO_EVENT_TYPE.entries.associate { (action, eventType) -> eventType.key to action.name }

        private val payloadSerializer = MapSerializer(String.serializer(), String.serializer())
    }

    fun logAction(document: Document, action: DocumentAuditLogAction, performedBy: AppUser)
    {
        val managedPerformedBy = entityManager.merge(performedBy)
        val auditLog = DocumentAuditLog().apply {
            this.document = document
            this.action = action
            this.performedBy = managedPerformedBy
            this.performedByEmail = managedPerformedBy.email
            this.timestamp = Timestamp.from(Instant.now())
        }

        documentAuditRepository.save(auditLog)
        recordOnRecorder(document, action, actorId = managedPerformedBy.id, actorEmail = managedPerformedBy.email)
    }

    fun logAction(document: Document, action: DocumentAuditLogAction, performedByEmail: String)
    {
        val auditLog = DocumentAuditLog().apply {
            this.document = document
            this.action = action
            this.timestamp = Timestamp.from(Instant.now())
            this.performedByEmail = performedByEmail
        }

        documentAuditRepository.save(auditLog)
        recordOnRecorder(document, action, actorId = null, actorEmail = performedByEmail)
    }

    fun getDocumentAuditLogs(exchangeId: String?, documentId: String): List<DocumentAuditLog>
    {
        exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val document = exchangeDocumentRepository.findByDocumentId(UUID.fromString(documentId))
            ?: throw ExchangeDocumentNotFoundException("Document not found")

        return documentAuditRepository.findByDocumentId(document.id)
    }

    /**
     * Backs the Exchange "Audit" tab with a single
     * ledger query across every document in the Exchange, instead of a separate
     * one `/documents/{documentId}/audit` request per document. Reads
     * [AuditLedgerEventRepository.findByTargetTypeAndTargetIds] rather than the legacy
     * [DocumentAuditRepository] table, so results reflect the recorder-backed dual write in
     * [logAction] above.
     */
    fun getExchangeAuditEvents(exchangeId: String): List<DocumentAuditDetailedDto>
    {
        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val documentsById = exchange.documents.associateBy { it.id.toString() }
        if (documentsById.isEmpty())
        {
            return emptyList()
        }

        val ledgerEvents = auditLedgerEventRepository.findByTargetTypeAndTargetIds("Document", documentsById.keys.toList())

        return ledgerEvents.map { toDocumentAuditDetailedDto(it, documentsById) }
    }

    private fun toDocumentAuditDetailedDto(
        event: AuditLedgerEvent,
        documentsById: Map<String, Document>,
    ): DocumentAuditDetailedDto
    {
        val payload = runCatching { Json.decodeFromString(payloadSerializer, event.payloadJson) }
            .getOrDefault(emptyMap())
        val documentId = event.targetId
        val document = documentId?.let { documentsById[it] }

        return DocumentAuditDetailedDto(
            id = event.id,
            timestamp = event.occurredAt,
            action = EVENT_TYPE_KEY_TO_ACTION[event.eventTypeKey] ?: event.eventTypeKey,
            performedBy = null,
            performedByEmail = payload["actor_email"],
            documentId = documentId,
            documentTitle = document?.title ?: payload["document_title"],
        )
    }

    /**
     * Writes events to [AuditRecorder] alongside
     * the legacy [DocumentAuditLog] row above, so document actions also land in
     * `audit_outbox`/`audit_ledger_event`. The legacy `audit_log` table is kept for now because
     * `ExchangeDocumentAuditResource`/the exchange document sidebar UI still read it directly; the
     * compatibility projection in this phase re-points the read side, not the write side, of the
     * legacy table. An actor performing this action without an app account (e.g. a public-link
     * recipient) has no [actorId], so actor kind is distinguished by [actorEmail] presence instead
     * of guessing.
     */
    private fun recordOnRecorder(document: Document, action: DocumentAuditLogAction, actorId: UUID?, actorEmail: String?)
    {
        val eventType = ACTION_TO_EVENT_TYPE[action]
        if (eventType == null)
        {
            logger.warn("ExchangeDocumentAuditService: no AuditEventType mapping for action={}", action)
            return
        }

        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorRole = if (actorId != null) "APP_USER" else "PUBLIC_LINK_OR_EMAIL_ACTOR",
                    actorLabel = if (actorId == null) actorEmail else null,
                    targetType = "Document",
                    targetId = document.id.toString(),
                    targetLabel = document.title,
                    payload = buildMap {
                        put("document_title", document.title)
                        actorEmail?.let { put("actor_email", it) }
                    },
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("ExchangeDocumentAuditService: AuditRecorder rejected draft for action={}: {}", action, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error(
                "ExchangeDocumentAuditService: AuditRecorder capture failed (fail-closed) for action={}: {}",
                action, e.message, e,
            )
        }
    }
}
