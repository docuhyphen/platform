package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AuthAuditEvent
import com.docuhyphen.app.api.repository.AuthAuditEventRepository
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
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuthAuditService::class.java)
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

        if (configurationService.isAuditImmutableEnabled())
        {
            val previousEventHash = authAuditEventRepository.findLatestEventHash()
            val eventHash = hashEvent(
                action = action,
                outcome = outcome,
                reasonCode = reasonCode?.name,
                actorId = actorId,
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
        action: String,
        outcome: String,
        reasonCode: String?,
        actorId: UUID?,
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
        val payload = listOf(
            previousEventHash.orEmpty(),
            action,
            outcome,
            reasonCode.orEmpty(),
            actorId?.toString().orEmpty(),
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
}




