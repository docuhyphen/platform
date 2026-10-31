package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.audit.archive.*
import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.repository.audit.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.service.audit.LedgerProcessor
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class AuditArchiveVerifier @Inject constructor(
    private val auditArchiveSegmentRepository: AuditArchiveSegmentRepository,
    private val archiveStorage: AuditArchiveStorage,
    private val signingKeyProvider: AuditArchiveSigningKeyProvider,
)
{
    fun verifySegment(segment: AuditArchiveSegment): SegmentVerificationResult
    {
        val segmentBytes = try
        {
            archiveStorage.getObject(segment.segmentObjectKey)
        }
        catch (e: Exception)
        {
            return SegmentVerificationResult(false, "segment object unreadable: ${e.message}")
        }

        val manifestBytes = try
        {
            archiveStorage.getObject(segment.manifestObjectKey)
        }
        catch (e: Exception)
        {
            return SegmentVerificationResult(false, "manifest object unreadable: ${e.message}")
        }

        val records = try
        {
            segmentBytes.toString(StandardCharsets.UTF_8)
                .lineSequence()
                .filter { it.isNotBlank() }
                .map { Json.decodeFromString(ArchivedLedgerEventRecord.serializer(), it) }
                .toList()
        }
        catch (e: Exception)
        {
            return SegmentVerificationResult(
                false,
                "segment content malformed or missing a required field: ${e.message}"
            )
        }

        if (records.size != segment.eventCount)
        {
            return SegmentVerificationResult(
                false,
                "event count mismatch: expected ${segment.eventCount}, found ${records.size}"
            )
        }

        for ((index, record) in records.withIndex())
        {
            val ownerId = record.ownerId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (record.streamId != segment.streamId ||
                (record.ownerId != null && ownerId == null) ||
                !com.docuhyphen.app.api.service.audit.AuditStreamOwnership.matches(
                    record.streamId, record.ownerType, ownerId,
                )
            ) {
                return SegmentVerificationResult(false, "record owner does not match its protected stream")
            }
            val expectedSequence = segment.firstSequence + index
            if (record.streamSequence != expectedSequence)
            {
                return SegmentVerificationResult(
                    false,
                    "record at position $index has sequence ${record.streamSequence}, expected $expectedSequence: " +
                            "records are missing, duplicated, or out of order",
                )
            }
        }

        for (index in records.indices)
        {
            val record = records[index]
            if (index > 0 && record.prevHash != records[index - 1].eventHash)
            {
                return SegmentVerificationResult(
                    false,
                    "record at position $index has a prevHash that does not chain from the previous record's recomputed eventHash",
                )
            }

            val canonicalJson = LedgerProcessor.canonicalEnvelopeJson(
                eventId = record.eventId,
                eventTypeKey = record.eventTypeKey,
                category = record.category,
                outcome = record.outcome,
                schemaVersion = record.schemaVersion,
                occurredAt = record.occurredAt,
                recordedAt = record.recordedAt,
                streamId = record.streamId,
                actorKind = record.actorKind,
                actorId = record.actorId,
                actorRole = record.actorRole,
                actorLabel = record.actorLabel,
                sessionId = record.sessionId,
                serverTraceId = record.serverTraceId,
                correlationId = record.correlationId,
                causationId = record.causationId,
                organizationId = record.organizationId,
                organizationLabel = record.organizationLabel,
                targetType = record.targetType,
                targetId = record.targetId,
                targetLabel = record.targetLabel,
                reason = record.reason,
                payloadJson = record.payloadJson,
            )
            val recomputedEventHash = LedgerProcessor.computeHash(canonicalJson, record.streamSequence, record.prevHash)
            if (recomputedEventHash != record.eventHash)
            {
                return SegmentVerificationResult(
                    false,
                    "record at position $index: recomputed event hash does not match the archived eventHash - " +
                            "a protected field was modified after archiving",
                )
            }
        }

        val recomputedMerkleRoot = MerkleTree.computeRoot(records.map { it.eventHash })
        if (recomputedMerkleRoot != segment.merkleRoot)
        {
            return SegmentVerificationResult(false, "merkle root mismatch: recomputed does not match recorded root")
        }

        val recomputedSegmentDigest = AuditArchiver.computeSegmentDigest(
            segment.streamId,
            segment.firstSequence,
            segment.lastSequence,
            recomputedMerkleRoot,
            segment.prevSegmentDigest,
        )
        if (recomputedSegmentDigest != segment.segmentDigest)
        {
            return SegmentVerificationResult(
                false,
                "segment digest mismatch: recomputed does not match recorded digest"
            )
        }

        val signedManifest = try
        {
            Json.decodeFromString(
                SignedAuditArchiveManifest.serializer(),
                manifestBytes.toString(StandardCharsets.UTF_8)
            )
        }
        catch (e: Exception)
        {
            return SegmentVerificationResult(false, "manifest object malformed: ${e.message}")
        }

        val manifestEnvelope = try
        {
            Json.decodeFromString(AuditArchiveManifestEnvelope.serializer(), signedManifest.manifestJson)
        }
        catch (e: Exception)
        {
            return SegmentVerificationResult(false, "manifest envelope malformed: ${e.message}")
        }

        val expectedContentSha256 = MerkleTree.sha256Hex(segmentBytes)
        val manifestMatchesRow = manifestEnvelope.formatVersion == segment.formatVersion &&
                manifestEnvelope.formatVersion == AuditArchiveSegment.CURRENT_FORMAT_VERSION &&
                manifestEnvelope.streamId == segment.streamId &&
                manifestEnvelope.firstSequence == segment.firstSequence &&
                manifestEnvelope.lastSequence == segment.lastSequence &&
                manifestEnvelope.eventCount == segment.eventCount &&
                manifestEnvelope.merkleRoot == segment.merkleRoot &&
                manifestEnvelope.segmentDigest == segment.segmentDigest &&
                manifestEnvelope.prevSegmentDigest == segment.prevSegmentDigest &&
                manifestEnvelope.schemaVersions == segment.schemaVersions &&
                manifestEnvelope.signingKeyId == segment.signingKeyId &&
                manifestEnvelope.contentObjectKey == segment.segmentObjectKey &&
                manifestEnvelope.contentSha256 == expectedContentSha256 &&
                manifestEnvelope.contentLength == segmentBytes.size.toLong() &&
                runCatching { Instant.parse(manifestEnvelope.createdAt) }.getOrNull() == segment.createdAt.toInstant() &&
                signedManifest.signatureAlgorithm == "SHA256withRSA" &&
                signedManifest.signatureBase64 == segment.manifestSignature
        if (!manifestMatchesRow)
        {
            return SegmentVerificationResult(
                false,
                "manifest metadata does not match the segment row or content object"
            )
        }

        val signatureValid = try
        {
            signingKeyProvider.verify(
                signedManifest.manifestJson.toByteArray(StandardCharsets.UTF_8),
                Base64.getDecoder().decode(signedManifest.signatureBase64),
                manifestEnvelope.signingKeyId,
            )
        }
        catch (e: Exception)
        {
            false
        }

        if (!signatureValid)
        {
            return SegmentVerificationResult(false, "manifest signature verification failed")
        }

        return SegmentVerificationResult(
            true,
            "verified: merkle root, segment digest, and manifest signature all match"
        )
    }

    fun verifyStreamChain(streamId: String): StreamChainVerificationResult
    {
        val segments = auditArchiveSegmentRepository.findByStreamOrderBySequence(streamId)
        if (segments.isEmpty())
        {
            return StreamChainVerificationResult(false, "stream has no archived segments", 0)
        }

        if (segments.first().firstSequence != 1L)
        {
            return StreamChainVerificationResult(
                false,
                "first archived segment begins at ${segments.first().firstSequence}, expected sequence 1",
                segments.size,
            )
        }

        var previous: AuditArchiveSegment? = null
        for (segment in segments)
        {
            if (previous == null)
            {
                if (segment.prevSegmentDigest != null)
                {
                    return StreamChainVerificationResult(
                        false,
                        "first segment [${segment.firstSequence},${segment.lastSequence}] unexpectedly has a prevSegmentDigest",
                        segments.size,
                    )
                }
            }
            else
            {
                if (segment.prevSegmentDigest != previous.segmentDigest)
                {
                    return StreamChainVerificationResult(
                        false,
                        "chain break before segment [${segment.firstSequence},${segment.lastSequence}]: " +
                                "prevSegmentDigest does not match the prior segment's segmentDigest",
                        segments.size,
                    )
                }
                if (segment.firstSequence != previous.lastSequence + 1)
                {
                    return StreamChainVerificationResult(
                        false,
                        "sequence gap or overlap between segments ending at ${previous.lastSequence} and starting at ${segment.firstSequence}",
                        segments.size,
                    )
                }
            }
            previous = segment
        }

        return StreamChainVerificationResult(
            true,
            "verified: ${segments.size} segments form a contiguous, unbroken digest chain",
            segments.size
        )
    }

    fun checkRangeCoverage(streamId: String, fromSequence: Long, toSequence: Long): StreamCoverageReport
    {
        if (fromSequence > toSequence)
        {
            return StreamCoverageReport(streamId, ArchiveCoverageState.COMPLETE, "empty range requested")
        }

        val segments = auditArchiveSegmentRepository.findByStreamOrderBySequence(streamId)

        val orphanNote = reconcileStorageInventory(streamId, segments)
        if (orphanNote != null)
        {
            return StreamCoverageReport(streamId, ArchiveCoverageState.FAILED, orphanNote)
        }

        if (segments.isEmpty())
        {
            return StreamCoverageReport(streamId, ArchiveCoverageState.PARTIAL, "stream has no archived segments yet")
        }

        val chain = verifyStreamChain(streamId)
        if (!chain.valid)
        {
            return StreamCoverageReport(streamId, ArchiveCoverageState.FAILED, "segment chain broken: ${chain.note}")
        }

        if (fromSequence < segments.first().firstSequence)
        {
            return StreamCoverageReport(
                streamId, ArchiveCoverageState.FAILED,
                "requested range starts at sequence $fromSequence but the earliest archived segment begins at " +
                        "${segments.first().firstSequence}: earlier events are not covered by any archive checkpoint",
            )
        }

        val relevantSegments = segments.filter { it.lastSequence >= fromSequence && it.firstSequence <= toSequence }
        for (segment in relevantSegments)
        {
            val result = verifySegment(segment)
            if (!result.valid)
            {
                return StreamCoverageReport(
                    streamId, ArchiveCoverageState.FAILED,
                    "segment[${segment.firstSequence},${segment.lastSequence}] failed verification: ${result.note}",
                )
            }
        }

        if (toSequence > segments.last().lastSequence)
        {
            return StreamCoverageReport(
                streamId, ArchiveCoverageState.PARTIAL,
                "requested range ends at sequence $toSequence but the latest archived segment only covers " +
                        "through ${segments.last().lastSequence}: trailing events are not archived yet",
            )
        }

        return StreamCoverageReport(
            streamId, ArchiveCoverageState.COMPLETE,
            "every event in [$fromSequence,$toSequence] is covered by a verified archive checkpoint",
        )
    }

    private fun reconcileStorageInventory(streamId: String, segments: List<AuditArchiveSegment>): String?
    {
        val knownKeys = segments.flatMap { listOf(it.segmentObjectKey, it.manifestObjectKey) }.toSet()
        val storedKeys = archiveStorage.listKeysWithPrefix("archive/$streamId/")
        val orphanKeys = storedKeys.filter { it !in knownKeys }
        if (orphanKeys.isEmpty())
        {
            return null
        }
        return "storage holds ${orphanKeys.size} archived object(s) under this stream's prefix " +
                "(e.g. ${orphanKeys.first()}) that no current segment row references - a segment row may " +
                "have been deleted after archiving"
    }

    fun verifyDueSegments(
        reverifyAfterHours: Int,
        batchSize: Int = 100
    ): List<Pair<AuditArchiveSegment, SegmentVerificationResult>>
    {
        val since = Timestamp.from(Instant.now().minusSeconds(reverifyAfterHours * 3600L))
        val due = auditArchiveSegmentRepository.findDueForVerification(since, batchSize)

        return due.map { segment ->
            val result = verifySegment(segment)
            segment.status = if (result.valid) "VERIFIED" else "VERIFICATION_FAILED"
            segment.lastVerificationAt = Timestamp.from(Instant.now())
            segment.lastVerificationNote = result.note
            auditArchiveSegmentRepository.updateVerification(segment)
            segment to result
        }
    }
}
