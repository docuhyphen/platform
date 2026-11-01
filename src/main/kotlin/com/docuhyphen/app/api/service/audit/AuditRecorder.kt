package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditDraftValidationResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.repository.audit.AuditOutboxRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

@RequestScoped
class AuditRecorder @Inject constructor(
    private val auditOutboxRepository: AuditOutboxRepository,
    private val authTokenContext: AuthTokenContext,
    private val failurePolicyResolver: AuditFailurePolicyResolver,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditRecorder::class.java)
    }

    fun record(draft: AuditEventDraft): AuditCaptureResult
    {
        when (val validation = AuditEventDraftValidator.validate(draft))
        {
            is AuditDraftValidationResult.Invalid -> throw AuditDraftInvalidException(validation.errors)

            is AuditDraftValidationResult.Valid ->
            {
                val eventType = validation.eventType
                val idempotencyKey = draft.idempotencyKey ?: draft.eventId.toString()

                val existing = runCatching { auditOutboxRepository.findByIdempotencyKey(idempotencyKey) }.getOrNull()
                if (existing != null)
                {
                    logger.info(
                        "audit_outbox already contains an entry for idempotencyKey={} (id={}); skipping duplicate insert",
                        idempotencyKey, existing.id,
                    )
                    return AuditCaptureResult.Captured(existing.id, existing.eventId)
                }

                val policy = failurePolicyResolver.resolve(eventType.category)

                return try
                {
                    val entry = buildEntry(draft, eventType, idempotencyKey)
                    val saved = auditOutboxRepository.insert(entry)
                    AuditCaptureResult.Captured(saved.id, saved.eventId)
                }
                catch (e: Exception)
                {
                    when (policy)
                    {
                        AuditFailurePolicy.FAIL_CLOSED ->
                        {
                            logger.error(
                                "audit_outbox capture FAILED (fail-closed) eventType={} category={} idempotencyKey={}: {}",
                                eventType.key, eventType.category, idempotencyKey, e.message, e,
                            )
                            throw AuditCaptureFailedException(
                                "Failed to durably capture audit event ${eventType.key}; failing closed",
                                e,
                            )
                        }

                        AuditFailurePolicy.DEGRADED ->
                        {
                            logger.error(
                                "audit_outbox capture failed (degraded/log-only) eventType={} category={} idempotencyKey={}: {}",
                                eventType.key, eventType.category, idempotencyKey, e.message, e,
                            )
                            AuditCaptureResult.Degraded(draft.eventId, e.message ?: e.javaClass.simpleName)
                        }
                    }
                }
            }
        }
    }

    private fun resolveAuthenticatedActorLabel(): String? = runCatching {
        val appUser = authTokenContext.authToken.appUser ?: return@runCatching null
        val person = appUser.person
        val fullName = listOfNotNull(person?.firstName, person?.lastName)
            .joinToString(" ")
            .trim()
        if (fullName.isNotEmpty()) "$fullName <${appUser.email}>" else appUser.email
    }.getOrNull()

    private fun buildEntry(draft: AuditEventDraft, eventType: AuditEventType, idempotencyKey: String): AuditOutboxEntry
    {
        val now = Timestamp.from(Instant.now())
        return AuditOutboxEntry().apply {
            eventId = draft.eventId
            this.idempotencyKey = idempotencyKey
            eventTypeKey = eventType.key
            category = eventType.category.name
            outcome = draft.outcome.name
            actorId = draft.actorId ?: runCatching { authTokenContext.authToken.appUser?.id }.getOrNull()
            actorRole = draft.actorRole
            actorKind = draft.actorKind?.name
            actorLabel = draft.actorLabel ?: resolveAuthenticatedActorLabel()
            targetType = draft.targetType
            targetId = draft.targetId
            targetLabel = draft.targetLabel
            ownerType = when (draft.owner)
            {
                AuditOwnerScope.Platform -> "PLATFORM"
                is AuditOwnerScope.Organization -> "ORGANIZATION"
                is AuditOwnerScope.Personal -> "USER"
            }
            ownerId = when (val owner = draft.owner)
            {
                AuditOwnerScope.Platform -> null
                is AuditOwnerScope.Organization -> owner.organizationId
                is AuditOwnerScope.Personal -> owner.userId
            }
            organizationId = when (val owner = draft.owner)
            {
                AuditOwnerScope.Platform -> null
                is AuditOwnerScope.Organization -> owner.organizationId
                is AuditOwnerScope.Personal -> null
            }
            organizationLabel = draft.organizationLabel
            sessionId = draft.sessionId
            reason = draft.reason
            payloadJson = Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), draft.payload)
            serverTraceId = authTokenContext.serverTraceId
            correlationId = authTokenContext.correlationId
            causationId = authTokenContext.causationId
            businessTransactionId = draft.businessTransactionId
            status = "PENDING"
            attemptCount = 0
            occurredAt = now
            recordedAt = now
            catalogVersion = AuditEventType.CATALOG_VERSION
        }
    }
}
