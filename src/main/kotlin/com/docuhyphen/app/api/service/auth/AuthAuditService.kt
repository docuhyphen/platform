package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

@RequestScoped
class AuthAuditService @Inject constructor(
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuthAuditService::class.java)

        fun mapOutcome(outcome: String): AuditOutcome = when (outcome.trim().uppercase())
        {
            "SUCCESS", "ALLOW", "ORG_FOUND" -> AuditOutcome.SUCCESS
            "DENY", "DENIED" -> AuditOutcome.DENIED
            "FAILURE", "FAILED" -> AuditOutcome.FAILURE
            "ERROR" -> AuditOutcome.ERROR
            else -> AuditOutcome.SUCCESS
        }
    }

    fun emit(
        action: String,
        outcome: String,
        reasonCode: RevocationReasonCode? = null,
        actorId: UUID? = null,
        actorRole: String? = null,
        sessionId: String? = null,
        organizationId: UUID? = null,
        requestId: String? = null,
        reason: String? = null,
        beforeSnapshot: String? = null,
        afterSnapshot: String? = null,
        targetType: String? = null,
        targetId: String? = null,
    )
    {
        val eventType = AuditEventType.entries.firstOrNull { it.name == action }
        if (eventType == null)
        {
            logger.warn("Authentication audit action is not registered in the event catalog: {}", action)
            return
        }

        val eventId = UUID.randomUUID()
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = mapOutcome(outcome),
                    actorId = actorId,
                    actorRole = actorRole,
                    targetType = targetType,
                    targetId = targetId,
                    owner = organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    sessionId = sessionId,
                    reason = sanitize(reason, 2048),
                    payload = buildMap {
                        reasonCode?.let { put("reason_code", it.name) }
                        requestId?.let { put("request_id", it.take(256)) }
                        if (!beforeSnapshot.isNullOrBlank() || !afterSnapshot.isNullOrBlank())
                        {
                            put("state_changed", "true")
                        }
                    },
                    eventId = eventId,
                    idempotencyKey = "auth:$eventId",
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("Authentication audit draft rejected for action={}: {}", action, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("Authentication audit capture failed for action={}: {}", action, e.message, e)
        }
    }

    private fun sanitize(value: String?, maxLength: Int): String?
    {
        if (value.isNullOrBlank()) return null
        return value.take(maxLength)
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[REDACTED_EMAIL]")
            .replace(Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), "[REDACTED_JWT]")
            .replace(Regex("(?i)(token|secret|password)=[^;\\s]+"), "$1=[REDACTED]")
    }
}
