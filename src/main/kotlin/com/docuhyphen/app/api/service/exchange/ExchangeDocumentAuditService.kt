package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.ExchangeDocumentAuditDtoMapper
import com.docuhyphen.app.api.model.dto.DocumentAuditDetailedDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Document
import com.docuhyphen.app.api.model.entity.DocumentAuditAction
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class ExchangeDocumentAuditService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val auditRecorder: AuditRecorder,
    private val auditOwnerScopeResolver: AuditOwnerScopeResolver,
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(ExchangeDocumentAuditService::class.java)

        private val ACTION_TO_EVENT_TYPE: Map<DocumentAuditAction, AuditEventType> = mapOf(
            DocumentAuditAction.UPLOAD to AuditEventType.DOCUMENT_UPLOAD,
            DocumentAuditAction.DOWNLOAD to AuditEventType.DOCUMENT_DOWNLOAD,
            DocumentAuditAction.VIEW to AuditEventType.DOCUMENT_VIEW,
            DocumentAuditAction.CREATED to AuditEventType.DOCUMENT_CREATED,
            DocumentAuditAction.DELETE to AuditEventType.DOCUMENT_DELETE,
            DocumentAuditAction.UPDATE to AuditEventType.DOCUMENT_UPDATE,
            DocumentAuditAction.COMMENT to AuditEventType.DOCUMENT_COMMENT,
            DocumentAuditAction.VERSION_CREATED to AuditEventType.DOCUMENT_VERSION_CREATED,
        )

        private val EVENT_TYPE_KEY_TO_ACTION: Map<String, String> =
            ACTION_TO_EVENT_TYPE.entries.associate { (action, eventType) -> eventType.key to action.name }
    }

    fun logAction(document: Document, action: DocumentAuditAction, performedBy: AppUser)
    {
        recordOnRecorder(document, action, actorId = performedBy.id, actorEmail = performedBy.email)
    }

    fun logAction(document: Document, action: DocumentAuditAction, performedByEmail: String)
    {
        recordOnRecorder(document, action, actorId = null, actorEmail = performedByEmail)
    }

    fun getExchangeAuditEvents(exchangeId: String): List<DocumentAuditDetailedDto>
    {
        val exchange = exchangeRepository.findById(UUID.fromString(exchangeId))
            ?: throw ExchangeNotFoundException("Exchange not found")

        val documentsById = exchange.documents.associateBy { it.id.toString() }
        if (documentsById.isEmpty())
        {
            return emptyList()
        }

        val ledgerEvents =
            auditLedgerEventRepository.findByTargetTypeAndTargetIds("Document", documentsById.keys.toList())

        return ledgerEvents.map { event ->
            ExchangeDocumentAuditDtoMapper.toDto(
                event,
                documentsById,
                EVENT_TYPE_KEY_TO_ACTION[event.eventTypeKey] ?: event.eventTypeKey,
            )
        }
    }

    private fun recordOnRecorder(document: Document, action: DocumentAuditAction, actorId: UUID?, actorEmail: String?)
    {
        val eventType = ACTION_TO_EVENT_TYPE[action]
        if (eventType == null)
        {
            logger.warn("ExchangeDocumentAuditService: no AuditEventType mapping for action={}", action)
            return
        }

        try
        {
            val owningExchange = exchangeRepository.findByDocumentId(document.id)
            auditRecorder.record(
                AuditEventDraft(
                    owner = owningExchange?.let { auditOwnerScopeResolver.resolve(ResourceType.EXCHANGE, it.id) }
                        ?: auditOwnerScopeResolver.resolve(ResourceType.DOCUMENT, document.id),
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
            logger.warn(
                "ExchangeDocumentAuditService: AuditRecorder rejected draft for action={}: {}",
                action,
                e.message
            )
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
