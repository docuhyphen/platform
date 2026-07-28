package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
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
    private val authTokenContext: AuthTokenContext,
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
        structuredDetails: Map<String, String> = emptyMap(),
    )
    {
        runCatching {
            record(
                action,
                outcome,
                reasonCode,
                actorId,
                actorRole,
                sessionId,
                organizationId,
                requestId,
                reason,
                beforeSnapshot,
                afterSnapshot,
                targetType,
                targetId,
                structuredDetails,
            )
        }.onFailure { exception ->
            when (exception)
            {
                is AuditDraftInvalidException ->
                    logger.warn("Authentication audit draft rejected for action={}: {}", action, exception.message)
                else ->
                    logger.error("Authentication audit capture failed for action={}: {}", action, exception.message, exception)
            }
        }
    }

    fun emitRequired(
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
        structuredDetails: Map<String, String> = emptyMap(),
    )
    {
        val result = record(
            action,
            outcome,
            reasonCode,
            actorId,
            actorRole,
            sessionId,
            organizationId,
            requestId,
            reason,
            beforeSnapshot,
            afterSnapshot,
            targetType,
            targetId,
            structuredDetails,
        )
        if (result is AuditCaptureResult.Degraded)
        {
            throw AuditCaptureFailedException(
                "Required audit capture degraded for $action: ${result.reason}",
                null,
            )
        }
    }

    private fun record(
        action: String,
        outcome: String,
        reasonCode: RevocationReasonCode?,
        actorId: UUID?,
        actorRole: String?,
        sessionId: String?,
        organizationId: UUID?,
        requestId: String?,
        reason: String?,
        beforeSnapshot: String?,
        afterSnapshot: String?,
        targetType: String?,
        targetId: String?,
        structuredDetails: Map<String, String>,
    ): AuditCaptureResult
    {
        val eventType = AuditEventType.entries.firstOrNull { it.name == action }
            ?: throw IllegalArgumentException("Audit action is not registered in the event catalog: $action")
        val eventId = UUID.randomUUID()
        return auditRecorder.record(
            AuditEventDraft(
                eventTypeKey = eventType.key,
                outcome = mapOutcome(outcome),
                actorId = actorId,
                actorRole = actorRole,
                targetType = targetType,
                targetId = targetId,
                owner = organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                sessionId = sessionId ?: currentSessionId(),
                reason = sanitize(reason, 2048),
                payload = buildMap {
                    reasonCode?.let { put("reason_code", it.name) }
                    (requestId ?: currentRequestId())?.let { put("request_id", it.take(256)) }
                    currentSourceIp()?.let { put("source_ip", it.take(64)) }
                    if (!beforeSnapshot.isNullOrBlank() || !afterSnapshot.isNullOrBlank())
                    {
                        put("state_changed", "true")
                    }
                    structuredDetails.forEach { (key, value) ->
                        put(key.take(64), sanitize(value, 2048).orEmpty())
                    }
                },
                eventId = eventId,
                idempotencyKey = "auth:$eventId",
            )
        )
    }

    private fun currentRequestId(): String? = runCatching {
        authTokenContext.clientRequestIdHint ?: authTokenContext.correlationId
    }.getOrNull()

    private fun currentSessionId(): String? = runCatching {
        authTokenContext.authToken.jti
    }.getOrNull()

    private fun currentSourceIp(): String? = runCatching {
        authTokenContext.clientIp
    }.getOrNull()

    private fun sanitize(value: String?, maxLength: Int): String?
    {
        if (value.isNullOrBlank()) return null
        return value.take(maxLength)
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[REDACTED_EMAIL]")
            .replace(Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), "[REDACTED_JWT]")
            .replace(Regex("(?i)(token|secret|password)=[^;\\s]+"), "$1=[REDACTED]")
    }
}
