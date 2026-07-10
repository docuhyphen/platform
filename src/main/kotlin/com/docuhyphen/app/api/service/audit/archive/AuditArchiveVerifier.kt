package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.repository.AuditArchiveSegmentRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64

/** Outcome of verifying one segment against its independently re-downloaded content + manifest. */
data class SegmentVerificationResult(val valid: Boolean, val note: String)

/** Outcome of verifying a stream's segment-to-segment digest chain (the "boundary checkpoint" the architecture calls for). */
data class StreamChainVerificationResult(val valid: Boolean, val note: String, val segmentsChecked: Int)

/**
 * Real `verifyDay`-equivalent verifier for Phase 4 task 4 of
 * `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`, replacing the prior no-op verification: recomputes the
 * Merkle root and segment digest from re-downloaded [AuditArchiveStorage] content, checks the
 * detached manifest signature, and walks each stream's `prevSegmentDigest` chain so a deleted,
 * inserted, reordered, or truncated segment range is detected even if an individual segment's own
 * hash still checks out in isolation.
 */
@ApplicationScoped
class AuditArchiveVerifier @Inject constructor(
    private val auditArchiveSegmentRepository: AuditArchiveSegmentRepository,
    private val archiveStorage: AuditArchiveStorage,
    private val signingKeyProvider: AuditArchiveSigningKeyProvider,
)
{
    /**
     * Re-downloads [segment]'s content + manifest objects and independently recomputes/verifies
     * everything a tamper could break: the Merkle root, the segment digest, the manifest
     * signature, and that the manifest's own claimed fields match the DB row.
     */
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

        val records = segmentBytes.toString(StandardCharsets.UTF_8)
            .lineSequence()
            .filter { it.isNotBlank() }
            .map { Json.decodeFromString(ArchivedLedgerEventRecord.serializer(), it) }
            .toList()

        if (records.size != segment.eventCount)
        {
            return SegmentVerificationResult(false, "event count mismatch: expected ${segment.eventCount}, found ${records.size}")
        }

        val recomputedMerkleRoot = MerkleTree.computeRoot(records.map { it.eventHash })
        if (recomputedMerkleRoot != segment.merkleRoot)
        {
            return SegmentVerificationResult(false, "merkle root mismatch: recomputed does not match recorded root")
        }

        val recomputedSegmentDigest = AuditArchiver.computeSegmentDigest(
            segment.streamId, segment.firstSequence, segment.lastSequence, recomputedMerkleRoot, segment.prevSegmentDigest,
        )
        if (recomputedSegmentDigest != segment.segmentDigest)
        {
            return SegmentVerificationResult(false, "segment digest mismatch: recomputed does not match recorded digest")
        }

        val signedManifest = try
        {
            Json.decodeFromString(SignedAuditArchiveManifest.serializer(), manifestBytes.toString(StandardCharsets.UTF_8))
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

        if (manifestEnvelope.merkleRoot != segment.merkleRoot || manifestEnvelope.segmentDigest != segment.segmentDigest)
        {
            return SegmentVerificationResult(false, "manifest envelope does not match segment row")
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

        return SegmentVerificationResult(true, "verified: merkle root, segment digest, and manifest signature all match")
    }

    /**
     * Walks [streamId]'s segments in sequence order and checks that each segment's
     * [AuditArchiveSegment.prevSegmentDigest] equals the previous segment's
     * [AuditArchiveSegment.segmentDigest] and that sequence ranges are contiguous
     * (`firstSequence == previousLastSequence + 1`). This is the boundary-checkpoint check: an
     * inner range can be individually well-hashed yet still be a deleted/reordered/truncated
     * range if the chain to its neighbors is broken.
     */
    fun verifyStreamChain(streamId: String): StreamChainVerificationResult
    {
        val segments = auditArchiveSegmentRepository.findByStreamOrderBySequence(streamId)
        if (segments.isEmpty())
        {
            return StreamChainVerificationResult(true, "no segments to verify", 0)
        }

        var previous: AuditArchiveSegment? = null
        for (segment in segments)
        {
            if (previous == null)
            {
                if (segment.prevSegmentDigest != null)
                {
                    return StreamChainVerificationResult(
                        false, "first segment [${segment.firstSequence},${segment.lastSequence}] unexpectedly has a prevSegmentDigest", segments.size,
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

        return StreamChainVerificationResult(true, "verified: ${segments.size} segments form a contiguous, unbroken digest chain", segments.size)
    }

    /**
     * Verifies up to [batchSize] segments not (re)verified within
     * [com.docuhyphen.app.api.service.config.AuditArchiveConfigService.getReverifyAfterHours],
     * persisting each result onto [AuditArchiveSegment]. Returns the results so the caller (
     * [AuditArchiveScheduler]) can emit an immutable audit event per failure.
     */
    fun verifyDueSegments(reverifyAfterHours: Int, batchSize: Int = 100): List<Pair<AuditArchiveSegment, SegmentVerificationResult>>
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
