package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/** Canonical, signed segment manifest fields. Field order is fixed by declaration order so the same segment always produces byte-identical manifest JSON, which is what [AuditArchiveSigningKeyProvider.sign]/[AuditArchiveSigningKeyProvider.verify] operate over. */
@Serializable
data class AuditArchiveManifestEnvelope(
    val formatVersion: Int,
    val streamId: String,
    val firstSequence: Long,
    val lastSequence: Long,
    val eventCount: Int,
    val merkleRoot: String,
    val segmentDigest: String,
    val prevSegmentDigest: String?,
    val schemaVersions: String,
    val signingKeyId: String,
    val createdAt: String,
    val legacyImport: Boolean,
)

/** The object actually uploaded to [AuditArchiveStorage] alongside the segment content: the exact signed JSON string plus its detached signature, so the offline verifier never has to re-serialize (and risk a byte mismatch) to check the signature. */
@Serializable
data class SignedAuditArchiveManifest(
    val manifestJson: String,
    val signatureAlgorithm: String,
    val signatureBase64: String,
)

/** One archived ledger event record, denormalized exactly as it already exists in `audit_ledger_event` - already validated policy-safe (no secrets/raw content) at capture time. */
@Serializable
data class ArchivedLedgerEventRecord(
    val eventId: String,
    val eventTypeKey: String,
    val category: String,
    val outcome: String,
    val schemaVersion: Int,
    val occurredAt: String,
    val recordedAt: String,
    val streamId: String,
    val streamSequence: Long,
    val actorKind: String,
    val actorId: String?,
    val actorRole: String?,
    val sessionId: String?,
    val serverTraceId: String?,
    val correlationId: String?,
    val causationId: String?,
    val organizationId: String?,
    val targetType: String?,
    val targetId: String?,
    val reason: String?,
    val payloadJson: String,
    val prevHash: String?,
    val eventHash: String,
)

data class SegmentCloseResult(val closed: Int, val skipped: Int, val failed: Int)

/**
 * Closes `audit_ledger_event` ranges into signed, archived segments (Phase 4 tasks 1-2 of
 * `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`), replacing [com.docuhyphen.app.api.service.auth.AuthAuditWormSink]'s
 * local-JSONL-only approach with segments that carry a Merkle root, a segment-to-segment digest
 * chain (`prevSegmentDigest`), event count, schema versions, and a signing key id, all archived
 * to [AuditArchiveStorage] alongside a manifest signed by [AuditArchiveSigningKeyProvider].
 *
 * `@ApplicationScoped`: like [com.docuhyphen.app.api.service.audit.LedgerProcessor], this has no
 * per-request context to derive; it only reads already-durable ledger rows and writes archive
 * segments, so it runs from [AuditArchiveScheduler] rather than an inbound HTTP request.
 */
@ApplicationScoped
class AuditArchiver @Inject constructor(
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditArchiveSegmentRepository: AuditArchiveSegmentRepository,
    private val archiveStorage: AuditArchiveStorage,
    private val signingKeyProvider: AuditArchiveSigningKeyProvider,
    private val configService: AuditArchiveConfigService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditArchiver::class.java)

        /** `sha256(streamId|firstSequence|lastSequence|merkleRoot|prevSegmentDigest)`, hex-encoded. */
        fun computeSegmentDigest(
            streamId: String,
            firstSequence: Long,
            lastSequence: Long,
            merkleRoot: String,
            prevSegmentDigest: String?,
        ): String
        {
            val payload = "$streamId|$firstSequence|$lastSequence|$merkleRoot|${prevSegmentDigest.orEmpty()}"
            return MerkleTree.sha256Hex(payload.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /**
     * Closes one ready segment (>= [AuditArchiveConfigService.getSegmentSize] unarchived events)
     * for each distinct stream that currently has ledger events, up to [maxStreamsPerPass]
     * streams per call. Safe to call repeatedly: streams without enough new events are skipped,
     * not partially closed.
     */
    fun closeReadySegments(maxStreamsPerPass: Int = 50): SegmentCloseResult
    {
        val streamIds = auditLedgerEventRepository.findDistinctStreamIds().take(maxStreamsPerPass)
        var closed = 0
        var skipped = 0
        var failed = 0

        for (streamId in streamIds)
        {
            try
            {
                if (closeSegmentIfReady(streamId, force = false)) closed++ else skipped++
            }
            catch (e: Exception)
            {
                logger.error("AuditArchiver failed to close segment for streamId={}: {}", streamId, e.message, e)
                failed++
            }
        }

        return SegmentCloseResult(closed, skipped, failed)
    }

    /**
     * Closes the next unarchived range of ledger events for [streamId] into one segment if
     * either it has reached [AuditArchiveConfigService.getSegmentSize] events, or [force] is
     * true (used to flush a small trailing segment on a timer so history is not left unarchived
     * indefinitely). Returns false when there is nothing new to archive.
     */
    fun closeSegmentIfReady(streamId: String, force: Boolean): Boolean
    {
        val latestSegment = auditArchiveSegmentRepository.findLatestByStream(streamId)
        val fromSequence = (latestSegment?.lastSequence ?: 0) + 1

        val candidateEvents = auditLedgerEventRepository.findByStreamOrderBySequence(streamId)
            .filter { it.streamSequence >= fromSequence }

        if (candidateEvents.isEmpty()) return false
        if (!force && candidateEvents.size < configService.getSegmentSize()) return false

        val eventsToClose = candidateEvents.take(configService.getSegmentSize())
        buildAndArchiveSegment(streamId, eventsToClose, latestSegment)
        return true
    }

    private fun buildAndArchiveSegment(
        streamId: String,
        events: List<AuditLedgerEvent>,
        previousSegment: AuditArchiveSegment?,
    )
    {
        val firstSequence = events.first().streamSequence
        val lastSequence = events.last().streamSequence
        val merkleRoot = MerkleTree.computeRoot(events.map { it.eventHash })
        val prevSegmentDigest = previousSegment?.segmentDigest
        val segmentDigest = computeSegmentDigest(streamId, firstSequence, lastSequence, merkleRoot, prevSegmentDigest)
        val schemaVersions = events.map { it.schemaVersion }.distinct().sorted().joinToString(",")
        val signingKeyId = signingKeyProvider.keyId()
        val createdAt = Instant.now().toString()

        val segmentContent = events.joinToString("\n") { event ->
            Json.encodeToString(ArchivedLedgerEventRecord.serializer(), toRecord(event))
        }

        val manifestEnvelope = AuditArchiveManifestEnvelope(
            formatVersion = 1,
            streamId = streamId,
            firstSequence = firstSequence,
            lastSequence = lastSequence,
            eventCount = events.size,
            merkleRoot = merkleRoot,
            segmentDigest = segmentDigest,
            prevSegmentDigest = prevSegmentDigest,
            schemaVersions = schemaVersions,
            signingKeyId = signingKeyId,
            createdAt = createdAt,
            legacyImport = false,
        )
        val manifestJson = Json.encodeToString(AuditArchiveManifestEnvelope.serializer(), manifestEnvelope)
        val signatureBytes = signingKeyProvider.sign(manifestJson.toByteArray(StandardCharsets.UTF_8))
        val signedManifest = SignedAuditArchiveManifest(
            manifestJson = manifestJson,
            signatureAlgorithm = "SHA256withRSA",
            signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes),
        )
        val signedManifestJson = Json.encodeToString(SignedAuditArchiveManifest.serializer(), signedManifest)

        val segmentObjectKey = "archive/$streamId/$firstSequence-$lastSequence.jsonl"
        val manifestObjectKey = "archive/$streamId/$firstSequence-$lastSequence.manifest.json"

        archiveStorage.putObject(segmentObjectKey, segmentContent.toByteArray(StandardCharsets.UTF_8))
        archiveStorage.putObject(manifestObjectKey, signedManifestJson.toByteArray(StandardCharsets.UTF_8))

        val segment = AuditArchiveSegment().apply {
            this.streamId = streamId
            this.firstSequence = firstSequence
            this.lastSequence = lastSequence
            this.eventCount = events.size
            this.merkleRoot = merkleRoot
            this.segmentDigest = segmentDigest
            this.prevSegmentDigest = prevSegmentDigest
            this.schemaVersions = schemaVersions
            this.signingKeyId = signingKeyId
            this.manifestSignature = signedManifest.signatureBase64
            this.segmentObjectKey = segmentObjectKey
            this.manifestObjectKey = manifestObjectKey
            this.status = "CLOSED"
            this.legacyImport = false
        }
        auditArchiveSegmentRepository.insert(segment)

        logger.info(
            "audit archive: closed segment streamId={} range=[{},{}] eventCount={} segmentDigest={}",
            streamId, firstSequence, lastSequence, events.size, segmentDigest,
        )
    }

    private fun toRecord(event: AuditLedgerEvent): ArchivedLedgerEventRecord = ArchivedLedgerEventRecord(
        eventId = event.eventId.toString(),
        eventTypeKey = event.eventTypeKey,
        category = event.category,
        outcome = event.outcome,
        schemaVersion = event.schemaVersion,
        occurredAt = event.occurredAt.toInstant().toString(),
        recordedAt = event.recordedAt.toInstant().toString(),
        streamId = event.streamId,
        streamSequence = event.streamSequence,
        actorKind = event.actorKind,
        actorId = event.actorId?.toString(),
        actorRole = event.actorRole,
        sessionId = event.sessionId,
        serverTraceId = event.serverTraceId,
        correlationId = event.correlationId,
        causationId = event.causationId,
        organizationId = event.organizationId?.toString(),
        targetType = event.targetType,
        targetId = event.targetId,
        reason = event.reason,
        payloadJson = event.payloadJson,
        prevHash = event.prevHash,
        eventHash = event.eventHash,
    )
}
