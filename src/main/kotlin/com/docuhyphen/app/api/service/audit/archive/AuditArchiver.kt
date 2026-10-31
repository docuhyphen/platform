package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.audit.archive.ArchivedLedgerEventRecord
import com.docuhyphen.app.api.model.audit.archive.AuditArchiveManifestEnvelope
import com.docuhyphen.app.api.model.audit.archive.SegmentCloseResult
import com.docuhyphen.app.api.model.audit.archive.SignedAuditArchiveManifest
import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

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

    fun closeReadySegments(maxStreamsPerPass: Int = 50): SegmentCloseResult
    {
        val streamIds = auditLedgerEventRepository.findDistinctStreamIds()
        var closed = 0
        var skipped = 0
        var failed = 0

        for (streamId in streamIds)
        {
            if (closed + failed >= maxStreamsPerPass) break
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
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS).toString()

        val segmentContent = events.joinToString("\n") { event ->
            Json.encodeToString(ArchivedLedgerEventRecord.serializer(), toRecord(event))
        }

        val segmentBytes = segmentContent.toByteArray(StandardCharsets.UTF_8)
        val segmentObjectKey = "archive/$streamId/$firstSequence-$lastSequence.jsonl"
        val manifestObjectKey = "archive/$streamId/$firstSequence-$lastSequence.manifest.json"
        val manifestEnvelope = AuditArchiveManifestEnvelope(
            formatVersion = AuditArchiveSegment.CURRENT_FORMAT_VERSION,
            streamId = streamId,
            firstSequence = firstSequence,
            lastSequence = lastSequence,
            eventCount = events.size,
            merkleRoot = merkleRoot,
            segmentDigest = segmentDigest,
            prevSegmentDigest = prevSegmentDigest,
            schemaVersions = schemaVersions,
            signingKeyId = signingKeyId,
            contentObjectKey = segmentObjectKey,
            contentSha256 = MerkleTree.sha256Hex(segmentBytes),
            contentLength = segmentBytes.size.toLong(),
            createdAt = createdAt,
        )
        val manifestJson = Json.encodeToString(AuditArchiveManifestEnvelope.serializer(), manifestEnvelope)
        val signatureBytes = signingKeyProvider.sign(manifestJson.toByteArray(StandardCharsets.UTF_8))
        val signedManifest = SignedAuditArchiveManifest(
            manifestJson = manifestJson,
            signatureAlgorithm = "SHA256withRSA",
            signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes),
        )
        val signedManifestJson = Json.encodeToString(SignedAuditArchiveManifest.serializer(), signedManifest)

        archiveStorage.putObject(segmentObjectKey, segmentBytes)
        archiveStorage.putObject(manifestObjectKey, signedManifestJson.toByteArray(StandardCharsets.UTF_8))

        val segment = AuditArchiveSegment().apply {
            this.streamId = streamId
            this.firstSequence = firstSequence
            this.lastSequence = lastSequence
            this.eventCount = events.size
            this.formatVersion = AuditArchiveSegment.CURRENT_FORMAT_VERSION
            this.merkleRoot = merkleRoot
            this.segmentDigest = segmentDigest
            this.prevSegmentDigest = prevSegmentDigest
            this.schemaVersions = schemaVersions
            this.signingKeyId = signingKeyId
            this.manifestSignature = signedManifest.signatureBase64
            this.segmentObjectKey = segmentObjectKey
            this.manifestObjectKey = manifestObjectKey
            this.status = "CLOSED"
            this.createdAt = java.sql.Timestamp.from(Instant.parse(createdAt))
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
        actorLabel = event.actorLabel,
        sessionId = event.sessionId,
        serverTraceId = event.serverTraceId,
        correlationId = event.correlationId,
        causationId = event.causationId,
        ownerType = event.ownerType,
        ownerId = event.ownerId?.toString(),
        organizationId = event.organizationId?.toString(),
        organizationLabel = event.organizationLabel,
        targetType = event.targetType,
        targetId = event.targetId,
        targetLabel = event.targetLabel,
        reason = event.reason,
        payloadJson = event.payloadJson,
        prevHash = event.prevHash,
        eventHash = event.eventHash,
    )
}
