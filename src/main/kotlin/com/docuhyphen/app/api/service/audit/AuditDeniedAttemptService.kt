package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.UUID

@ApplicationScoped
class AuditDeniedAttemptService @Inject constructor(
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditDeniedAttemptService::class.java)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun record(
        actorId: UUID,
        organizationId: UUID?,
        targetType: String,
        targetId: String,
        reasonCode: String,
    )
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = AuditEventType.AUDIT_ACCESS_DENIED.key,
                    outcome = AuditOutcome.DENIED,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    actorRole = "AUDIT_GOVERNANCE",
                    owner = organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                    targetType = targetType,
                    targetId = targetId,
                    reason = "Audit governance action denied",
                    payload = mapOf("reason_code" to reasonCode),
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("Denied audit action could not be recorded: {}", e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("Denied audit action capture failed: {}", e.message, e)
        }
    }
}
