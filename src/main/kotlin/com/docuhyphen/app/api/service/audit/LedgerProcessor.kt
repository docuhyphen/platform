package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.CanonicalLedgerEnvelope
import com.docuhyphen.app.api.model.audit.LedgerDrainResult
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.audit.AuditOutboxRepository
import com.docuhyphen.app.api.repository.audit.StreamHeadRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

@ApplicationScoped
class LedgerProcessor @Inject constructor(
    private val auditOutboxRepository: AuditOutboxRepository,
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val streamHeadRepository: StreamHeadRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(LedgerProcessor::class.java)

        private val STREAM_MONTH_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC)

        fun resolveStreamId(entry: AuditOutboxEntry): String
        {
            val owner = when (entry.ownerType)
            {
                "PLATFORM" -> PLATFORM_STREAM_OWNER
                "ORGANIZATION" -> requireNotNull(entry.ownerId).toString()
                "USER" -> "$PERSONAL_STREAM_PREFIX${requireNotNull(entry.ownerId)}"
                else -> error("Unsupported audit owner type ${entry.ownerType}")
            }
            val month = STREAM_MONTH_FORMAT.format(entry.occurredAt.toInstant())
            return "$owner:$month"
        }

        fun ownerOfStream(streamId: String): AuditOwnerScope {
            val owner = streamId.substringBeforeLast(':')
            return when {
                owner == PLATFORM_STREAM_OWNER -> AuditOwnerScope.Platform
                owner.startsWith(PERSONAL_STREAM_PREFIX) ->
                    AuditOwnerScope.Personal(UUID.fromString(owner.removePrefix(PERSONAL_STREAM_PREFIX)))

                else -> AuditOwnerScope.Organization(UUID.fromString(owner))
            }
        }

        private const val PLATFORM_STREAM_OWNER = "platform"
        private const val PERSONAL_STREAM_PREFIX = "user:"

        fun resolveActorKind(entry: AuditOutboxEntry): String =
            entry.actorKind ?: if (entry.actorId != null) "HUMAN" else "SYSTEM"

        fun canonicalize(entry: AuditOutboxEntry, streamId: String, actorKind: String): String = canonicalEnvelopeJson(
            eventId = entry.eventId.toString(),
            eventTypeKey = entry.eventTypeKey,
            category = entry.category,
            outcome = entry.outcome,
            schemaVersion = entry.catalogVersion,
            occurredAt = entry.occurredAt.toInstant().toString(),
            recordedAt = entry.recordedAt.toInstant().toString(),
            streamId = streamId,
            actorKind = actorKind,
            actorId = entry.actorId?.toString(),
            actorRole = entry.actorRole,
            actorLabel = entry.actorLabel,
            sessionId = entry.sessionId,
            serverTraceId = entry.serverTraceId,
            correlationId = entry.correlationId,
            causationId = entry.causationId,
            organizationId = entry.organizationId?.toString(),
            organizationLabel = entry.organizationLabel,
            targetType = entry.targetType,
            targetId = entry.targetId,
            targetLabel = entry.targetLabel,
            reason = entry.reason,
            payloadJson = entry.payloadJson,
        )

        fun canonicalEnvelopeJson(
            eventId: String,
            eventTypeKey: String,
            category: String,
            outcome: String,
            schemaVersion: Int,
            occurredAt: String,
            recordedAt: String,
            streamId: String,
            actorKind: String,
            actorId: String?,
            actorRole: String?,
            actorLabel: String?,
            sessionId: String?,
            serverTraceId: String?,
            correlationId: String?,
            causationId: String?,
            organizationId: String?,
            organizationLabel: String?,
            targetType: String?,
            targetId: String?,
            targetLabel: String?,
            reason: String?,
            payloadJson: String,
        ): String
        {
            val envelope = CanonicalLedgerEnvelope(
                eventId = eventId,
                eventTypeKey = eventTypeKey,
                category = category,
                outcome = outcome,
                schemaVersion = schemaVersion,
                occurredAt = occurredAt,
                recordedAt = recordedAt,
                streamId = streamId,
                actorKind = actorKind,
                actorId = actorId,
                actorRole = actorRole,
                actorLabel = actorLabel,
                sessionId = sessionId,
                serverTraceId = serverTraceId,
                correlationId = correlationId,
                causationId = causationId,
                organizationId = organizationId,
                organizationLabel = organizationLabel,
                targetType = targetType,
                targetId = targetId,
                targetLabel = targetLabel,
                reason = reason,
                payloadJson = payloadJson,
            )
            return Json.encodeToString(CanonicalLedgerEnvelope.serializer(), envelope)
        }

        fun computeHash(canonicalJson: String, streamSequence: Long, prevHash: String?): String
        {
            val payload = "$canonicalJson|$streamSequence|${prevHash.orEmpty()}"
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    fun drain(batchSize: Int = 200): LedgerDrainResult
    {
        val candidates = auditOutboxRepository.findOldestUnledgeredByRecordedAt(batchSize)
        var appended = 0
        var alreadyLedgered = 0
        var failed = 0

        for (entry in candidates)
        {
            if (auditLedgerEventRepository.existsByEventId(entry.eventId))
            {
                alreadyLedgered++
                continue
            }

            try
            {
                if (appendOne(entry))
                {
                    appended++
                }
                else
                {
                    alreadyLedgered++
                }
            }
            catch (e: Exception)
            {
                logger.error("LedgerProcessor failed to append eventId={}: {}", entry.eventId, e.message, e)
                failed++
            }
        }

        return LedgerDrainResult(appended, alreadyLedgered, failed)
    }

    @Transactional
    open fun appendOne(entry: AuditOutboxEntry): Boolean
    {
        val streamId = resolveStreamId(entry)
        val streamHead = streamHeadRepository.lockOrCreate(streamId)

        if (auditLedgerEventRepository.existsByEventId(entry.eventId))
        {
            return false
        }

        val nextSequence = streamHead.lastSequence + 1
        val actorKind = resolveActorKind(entry)
        val canonicalJson = canonicalize(entry, streamId, actorKind)
        val eventHash = computeHash(canonicalJson, nextSequence, streamHead.lastHash)

        val ledgerEvent = AuditLedgerEvent().apply {
            eventId = entry.eventId
            eventTypeKey = entry.eventTypeKey
            category = entry.category
            outcome = entry.outcome
            schemaVersion = entry.catalogVersion
            occurredAt = entry.occurredAt
            recordedAt = entry.recordedAt
            ledgerTime = Timestamp.from(Instant.now())
            this.streamId = streamId
            streamSequence = nextSequence
            this.actorKind = actorKind
            actorId = entry.actorId
            actorRole = entry.actorRole
            actorLabel = entry.actorLabel
            sessionId = entry.sessionId
            serverTraceId = entry.serverTraceId
            correlationId = entry.correlationId
            causationId = entry.causationId
            organizationId = entry.organizationId
            ownerType = entry.ownerType
            ownerId = entry.ownerId
            organizationLabel = entry.organizationLabel
            targetType = entry.targetType
            targetId = entry.targetId
            targetLabel = entry.targetLabel
            reason = entry.reason
            payloadJson = entry.payloadJson
            prevHash = streamHead.lastHash
            this.eventHash = eventHash
        }

        auditLedgerEventRepository.insert(ledgerEvent)

        streamHead.lastSequence = nextSequence
        streamHead.lastHash = eventHash
        streamHeadRepository.advance(streamHead)

        return true
    }
}
