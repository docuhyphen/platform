package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.repository.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveVerifier
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.UUID

/** Per-stream integrity result surfaced by [AuditIntegrityService.checkOrganization]. */
data class StreamIntegrityReport(
    val streamId: String,
    val chainValid: Boolean,
    val chainNote: String,
    val segmentsChecked: Int,
    val segmentsValid: Int,
    val segmentFailureNotes: List<String>,
)

data class OrganizationIntegrityReport(
    val organizationId: UUID?,
    val platformOnly: Boolean,
    val streams: List<StreamIntegrityReport>,
)
{
    val allValid: Boolean get() = streams.all { it.chainValid && it.segmentFailureNotes.isEmpty() }
}

/**
 * Read-only integrity report over every stream belonging to an organization (or the platform),
 * combining [AuditArchiveVerifier]'s segment-chain boundary checkpoint with a full re-verification
 * of every archived segment for that stream. The `GET
 * /organizations/{organizationId}/audit-integrity`"); this is the online counterpart the endpoint
 * serves, while [AuditExportBuilder] embeds the same verification logic plus a public-key/README
 * so an export bundle can be checked without calling this endpoint at all ("offline").
 */
@ApplicationScoped
class AuditIntegrityService @Inject constructor(
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditArchiveSegmentRepository: AuditArchiveSegmentRepository,
    private val auditArchiveVerifier: AuditArchiveVerifier,
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditIntegrityService::class.java)
    }

    fun checkOrganization(
        organizationId: UUID?,
        platformOnly: Boolean,
        requestedByUserId: UUID?,
    ): OrganizationIntegrityReport
    {
        val streamIds = auditLedgerEventRepository.findDistinctStreamIdsByOrganization(organizationId, platformOnly)
        val reports = streamIds.map { streamId -> checkStream(streamId) }

        recordCheck(organizationId, platformOnly, requestedByUserId, reports)
        return OrganizationIntegrityReport(organizationId, platformOnly, reports)
    }

    fun checkStream(streamId: String): StreamIntegrityReport
    {
        val chain = auditArchiveVerifier.verifyStreamChain(streamId)
        val segments = auditArchiveSegmentRepository.findByStreamOrderBySequence(streamId)
        val failureNotes = mutableListOf<String>()
        var validCount = 0
        for (segment in segments)
        {
            val result = auditArchiveVerifier.verifySegment(segment)
            if (result.valid) validCount++ else failureNotes += "segment[${segment.firstSequence},${segment.lastSequence}]: ${result.note}"
        }

        return StreamIntegrityReport(
            streamId = streamId,
            chainValid = chain.valid,
            chainNote = chain.note,
            segmentsChecked = segments.size,
            segmentsValid = validCount,
            segmentFailureNotes = failureNotes,
        )
    }

    private fun recordCheck(
        organizationId: UUID?,
        platformOnly: Boolean,
        requestedByUserId: UUID?,
        reports: List<StreamIntegrityReport>,
    )
    {
        try
        {
            val allValid = reports.all { it.chainValid && it.segmentFailureNotes.isEmpty() }
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = AuditEventType.AUDIT_INTEGRITY_CHECK_PERFORMED.key,
                    outcome = if (allValid) AuditOutcome.SUCCESS else AuditOutcome.FAILURE,
                    actorId = requestedByUserId,
                    actorKind = if (requestedByUserId == null) AuditActorKind.SYSTEM else AuditActorKind.HUMAN,
                    organizationId = organizationId,
                    targetType = if (platformOnly) "PLATFORM" else "ORGANIZATION",
                    targetId = organizationId?.toString() ?: "platform",
                    payload = mapOf(
                        "streams_checked" to reports.size.toString(),
                        "all_valid" to allValid.toString(),
                    ),
                ),
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("AuditIntegrityService: AuditRecorder rejected integrity check event: {}", e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("AuditIntegrityService: AuditRecorder capture failed for integrity check event: {}", e.message, e)
        }
    }
}
