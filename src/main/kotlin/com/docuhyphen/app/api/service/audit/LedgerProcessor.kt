package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.AuditOutboxRepository
import com.docuhyphen.app.api.repository.StreamHeadRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Outcome of a [LedgerProcessor.drain] pass.
 */
data class LedgerDrainResult(val appended: Int, val alreadyLedgered: Int, val failed: Int)

/**
 * Turns committed `audit_outbox` intents into an ordered, hash-chained, per-stream ledger
 * from durable outbox rows.
 *
 * `@ApplicationScoped`: unlike [AuditRecorder], this has no per-request context to derive - it
 * only reads already-durable outbox rows and appends ledger rows, so it can run from a scheduled
 * background job ([LedgerProcessorScheduler]) rather than within an inbound HTTP request.
 *
 * `audit_outbox` rows are never mutated (the append-only trigger from `V41__audit_outbox.sql`
 * denies it unconditionally), so "already drained" is tracked by existence in
 * `audit_ledger_event` ([AuditLedgerEventRepository.existsByEventId]), not by an outbox status
 * flag because the outbox is append-only.
 */
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

        /**
         * Stream = owner scope + time partition. This exact partitioning is one of the
         * compliance and legal teams have not yet finalized stream partitioning and segment-closing
         * policy. `<organizationId-or-"platform">:<UTC yyyy-MM>` is a documented placeholder
         * default, not a final policy, chosen so streams stay small enough to append to quickly
         * without being reconsidered per event.
         */
        fun resolveStreamId(entry: AuditOutboxEntry): String
        {
            val owner = entry.organizationId?.toString() ?: "platform"
            val month = STREAM_MONTH_FORMAT.format(entry.occurredAt.toInstant())
            return "$owner:$month"
        }

        /**
         * Actor-kind resolution. Prefer
         * the explicit [AuditOutboxEntry.actorKind] set by call sites migrated onto
         * [com.docuhyphen.app.api.service.audit.catalog.AuditActorKind]; only fall back to the
         * coarse `HUMAN`/`SYSTEM` guess (by [AuditOutboxEntry.actorId] presence) for older/
         * unmigrated call sites that leave it null.
         */
        fun resolveActorKind(entry: AuditOutboxEntry): String =
            entry.actorKind ?: if (entry.actorId != null) "HUMAN" else "SYSTEM"

        /**
         * Deterministic JSON encoding of the canonical envelope fields that participate in the
         * hash chain (excludes [streamSequence]/[prevHash], which [computeHash] appends
         * separately per the architecture's `eventHash = sha256(canonicalEvent || sequence ||
         * prevHash)` formula).
         */
        fun canonicalize(entry: AuditOutboxEntry, streamId: String, actorKind: String): String
        {
            val envelope = CanonicalLedgerEnvelope(
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
            return Json.encodeToString(CanonicalLedgerEnvelope.serializer(), envelope)
        }

        /** `sha256(canonicalEventJson || streamSequence || prevHash)`, hex-encoded. */
        fun computeHash(canonicalJson: String, streamSequence: Long, prevHash: String?): String
        {
            val payload = "$canonicalJson|$streamSequence|${prevHash.orEmpty()}"
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Drains up to [batchSize] of the oldest outbox rows, appending exactly one ledger event for
     * each row not already ledgered. Safe to call repeatedly/concurrently: rows already ledgered
     * (by this call or a concurrent one) are skipped, and per-stream ordering is serialized by
     * [StreamHeadRepository.lockOrCreate]'s row lock, never by this method's own iteration order.
     */
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
                // A unique-constraint violation here means a concurrent drain pass already
                // ledgered this event_id between our existence check and our insert; that is
                // "already ledgered", not a real failure. Anything else is a genuine failure that
                // must not silently disappear - it is logged and retried on the next drain pass
                // (the outbox row is untouched, so nothing is lost).
                logger.error("LedgerProcessor failed to append eventId={}: {}", entry.eventId, e.message, e)
                failed++
            }
        }

        return LedgerDrainResult(appended, alreadyLedgered, failed)
    }

    /**
     * Appends exactly one ledger event for [entry], or returns `false` if a concurrent drain pass
     * already ledgered it. Runs in its own transaction so the [StreamHeadRepository] row lock is
     * held only for the duration of this single append, not the whole batch.
     */
    @Transactional
    open fun appendOne(entry: AuditOutboxEntry): Boolean
    {
        // Re-check inside the transaction: the drain()-level check above is a fast pre-filter,
        // this is the authoritative guard (backed by the event_id unique constraint).
        if (auditLedgerEventRepository.existsByEventId(entry.eventId))
        {
            return false
        }

        val streamId = resolveStreamId(entry)
        val streamHead = streamHeadRepository.lockOrCreate(streamId)

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

/**
 * Canonical, deterministically-serialized subset of ledger fields that participate in
 * [LedgerProcessor.computeHash]. Field order is fixed by declaration order (kotlinx.serialization
 * always encodes `@Serializable` properties in declaration order), so encoding the same logical
 * event always produces byte-identical JSON, which is what makes the hash chain meaningful.
 */
@Serializable
data class CanonicalLedgerEnvelope(
    val eventId: String,
    val eventTypeKey: String,
    val category: String,
    val outcome: String,
    val schemaVersion: Int,
    val occurredAt: String,
    val recordedAt: String,
    val streamId: String,
    val actorKind: String,
    val actorId: String?,
    val actorRole: String?,
    val actorLabel: String?,
    val sessionId: String?,
    val serverTraceId: String?,
    val correlationId: String?,
    val causationId: String?,
    val organizationId: String?,
    val organizationLabel: String?,
    val targetType: String?,
    val targetId: String?,
    val targetLabel: String?,
    val reason: String?,
    val payloadJson: String,
)
