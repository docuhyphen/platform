package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AuthAuditEvent
import com.docuhyphen.app.api.repository.AuthAuditEventRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class AuthAuditService @Inject constructor(
    private val authAuditEventRepository: AuthAuditEventRepository,
    private val configurationService: ConfigurationService,
    private val wormSink: AuthAuditWormSink,
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuthAuditService::class.java)

        /**
         * Maps the free-text legacy `outcome` string (historically anything a call site chose to
         * pass, e.g. "SUCCESS", "DENY", or a descriptive lookup result like "ORG_FOUND"/"NO_ORG")
         * onto the bounded [AuditOutcome] vocabulary the recorder/catalog requires. The original
         * string is preserved verbatim in the outbox payload (`legacy_outcome`) so no nuance is
         * lost by this coarser mapping.
         */
        fun mapLegacyOutcome(outcome: String): AuditOutcome = when (outcome.trim().uppercase())
        {
            "SUCCESS", "ALLOW", "ORG_FOUND" -> AuditOutcome.SUCCESS
            "DENY", "DENIED" -> AuditOutcome.DENIED
            "FAILURE", "FAILED" -> AuditOutcome.FAILURE
            "ERROR" -> AuditOutcome.ERROR
            // Descriptive, non-failure lookup results (e.g. "NO_ORG", "MULTIPLE_ORGS") that are
            // not themselves a denial/failure/error of the requested action.
            else -> AuditOutcome.SUCCESS
        }
    }

    fun findRecent(
        limit: Int,
        action: String?,
        outcome: String?,
        includeSnapshots: Boolean,
        organizationId: UUID? = null,
    ): List<AuthAuditEvent>
    {
        val events = authAuditEventRepository.findRecent(
            limit = limit,
            action = action,
            outcome = outcome,
            organizationId = organizationId,
        )
        if (includeSnapshots)
        {
            return events
        }

        return events.map { event ->
            AuthAuditEvent().apply {
                this.id = event.id
                this.actorId = event.actorId
                this.action = event.action
                this.outcome = event.outcome
                this.reasonCode = event.reasonCode
                this.sessionId = event.sessionId
                this.organizationId = event.organizationId
                this.requestId = event.requestId
                this.actionReason = event.actionReason
                this.beforeSnapshot = null
                this.afterSnapshot = null
                this.createdDate = event.createdDate
                this.eventHash = event.eventHash
                this.prevEventHash = event.prevEventHash
            }
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
        val timestamp = Instant.now()
        val sanitizedReason = sanitize(reason, 2048)
        val sanitizedBeforeSnapshot = sanitize(beforeSnapshot, 4000)
        val sanitizedAfterSnapshot = sanitize(afterSnapshot, 4000)
        val eventEntityId = UUID.randomUUID()

        if (configurationService.isAuditImmutableEnabled())
        {
            val previousEventHash = authAuditEventRepository.findLatestEventHash()
            val eventHash = hashEvent(
                eventId = eventEntityId,
                action = action,
                outcome = outcome,
                reasonCode = reasonCode?.name,
                actorId = actorId,
                actorRole = actorRole,
                targetType = targetType,
                targetId = targetId,
                sessionId = sessionId,
                organizationId = organizationId,
                requestId = requestId,
                reason = sanitizedReason,
                beforeSnapshot = sanitizedBeforeSnapshot,
                afterSnapshot = sanitizedAfterSnapshot,
                timestamp = timestamp,
                previousEventHash = previousEventHash,
            )

            authAuditEventRepository.save(
                AuthAuditEvent().apply {
                    this.id = eventEntityId
                    this.actorId = actorId
                    this.actorRole = actorRole
                    this.targetType = targetType
                    this.targetId = targetId
                    this.action = action
                    this.outcome = outcome
                    this.reasonCode = reasonCode?.name
                    this.sessionId = sessionId
                    this.organizationId = organizationId
                    this.requestId = requestId
                    this.actionReason = sanitizedReason
                    this.beforeSnapshot = sanitizedBeforeSnapshot
                    this.afterSnapshot = sanitizedAfterSnapshot
                    this.createdDate = Timestamp.from(timestamp)
                    this.prevEventHash = previousEventHash
                    this.eventHash = eventHash
                }
            )

            // Mirror to the append-only WORM sink so DB-level tampering can still be detected.
            wormSink.append(
                action = action,
                outcome = outcome,
                reasonCode = reasonCode?.name,
                actorId = actorId?.toString(),
                sessionId = sessionId,
                organizationId = organizationId?.toString(),
                requestId = requestId,
                reason = sanitizedReason,
                eventHash = eventHash,
                prevEventHash = previousEventHash,
                timestampEpochMillis = timestamp.toEpochMilli(),
            )
        }

        // Phase 3 (AUDIT-ARCHITECTURE-IMPLEMENTATION.md): route auth events through the canonical
        // AuditRecorder in addition to the legacy auth_audit_event table above, so they also land
        // in audit_outbox/audit_ledger_event. The legacy table/WORM sink are kept as-is (dual
        // write) because AuthAuditResource's UI still reads from auth_audit_event directly;
        // migrating that read path is left to Phase 7 (Auditor Portal) alongside the rest of the
        // ledger-backed UI work. An event type not yet in the catalog is a call-site bug worth
        // fixing, but must never break the auth flow it documents, so it is logged and skipped
        // here rather than thrown.
        recordOnRecorder(
            eventId = eventEntityId,
            action = action,
            outcome = outcome,
            reasonCode = reasonCode?.name,
            actorId = actorId,
            actorRole = actorRole,
            sessionId = sessionId,
            organizationId = organizationId,
            reason = sanitizedReason,
            targetType = targetType,
            targetId = targetId,
        )

        logger.info(
            "auth_audit action={} outcome={} reasonCode={} actorId={} sessionId={} organizationId={} requestId={} reason={} before={} after={} timestamp={}",
            action,
            outcome,
            reasonCode?.name,
            actorId,
            sessionId,
            organizationId,
            requestId,
            sanitizedReason,
            sanitizedBeforeSnapshot,
            sanitizedAfterSnapshot,
            timestamp,
        )
    }

    private fun sanitize(value: String?, maxLen: Int): String?
    {
        if (value.isNullOrBlank())
        {
            return null
        }

        var sanitized = value.take(maxLen)
        sanitized = sanitized.replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[REDACTED_EMAIL]")
        sanitized = sanitized.replace(Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), "[REDACTED_JWT]")
        sanitized = sanitized.replace(Regex("(?i)(token|secret|password)=[^;\\s]+"), "$1=[REDACTED]")
        return sanitized
    }

    private fun hashEvent(
        eventId: UUID,
        action: String,
        outcome: String,
        reasonCode: String?,
        actorId: UUID?,
        actorRole: String?,
        targetType: String?,
        targetId: String?,
        sessionId: String?,
        organizationId: UUID?,
        requestId: String?,
        reason: String?,
        beforeSnapshot: String?,
        afterSnapshot: String?,
        timestamp: Instant,
        previousEventHash: String?,
    ): String
    {
        // Phase 3 fix: eventId, actorRole, targetType, and targetId previously were not covered by
        // the hash, so tampering with them would not have broken chain verification. They are now
        // included so the full identity of the recorded event participates in the chain.
        val payload = listOf(
            previousEventHash.orEmpty(),
            eventId.toString(),
            action,
            outcome,
            reasonCode.orEmpty(),
            actorId?.toString().orEmpty(),
            actorRole.orEmpty(),
            targetType.orEmpty(),
            targetId.orEmpty(),
            sessionId.orEmpty(),
            organizationId?.toString().orEmpty(),
            requestId.orEmpty(),
            reason.orEmpty(),
            beforeSnapshot.orEmpty(),
            afterSnapshot.orEmpty(),
            timestamp.toString(),
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Best-effort dual write onto [AuditRecorder]: maps the legacy free-text `action`/`outcome`
     * strings onto the versioned catalog and records one `audit_outbox` entry per auth event, in
     * addition to the legacy `auth_audit_event` row written by the caller above.
     *
     * Never throws: an auth flow must not fail because of an audit-catalog mismatch
     * ([AuditDraftInvalidException], a call-site/catalog-seeding bug) or a capture failure under
     * the fail-closed policy ([AuditCaptureFailedException]) for a category that has not been
     * deliberately opted into fail-closed via `app.audit.failure-policy.fail-closed-categories` -
     * doing so here would make an unrelated behavior-preserving migration a new way to break
     * sign-in/sign-out/token-refresh in production.
     */
    private fun recordOnRecorder(
        eventId: UUID,
        action: String,
        outcome: String,
        reasonCode: String?,
        actorId: UUID?,
        actorRole: String?,
        sessionId: String?,
        organizationId: UUID?,
        reason: String?,
        targetType: String?,
        targetId: String?,
    )
    {
        val eventType = AuditEventType.entries.firstOrNull { it.name == action }
        if (eventType == null)
        {
            logger.warn(
                "AuthAuditService: no AuditEventType catalog entry for legacy action={}; skipping AuditRecorder dual write",
                action,
            )
            return
        }

        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = mapLegacyOutcome(outcome),
                    actorId = actorId,
                    actorRole = actorRole,
                    targetType = targetType,
                    targetId = targetId,
                    organizationId = organizationId,
                    sessionId = sessionId,
                    reason = reason,
                    payload = buildMap {
                        put("legacy_outcome", outcome)
                        reasonCode?.let { put("reason_code", it) }
                    },
                    eventId = eventId,
                    idempotencyKey = "auth_audit_event:$eventId",
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuthAuditService: AuditRecorder rejected draft for action={}: {}", action, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("AuthAuditService: AuditRecorder capture failed (fail-closed) for action={}: {}", action, e.message, e)
        }
    }
}




