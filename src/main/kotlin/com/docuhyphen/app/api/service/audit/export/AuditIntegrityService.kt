package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.audit.archive.StreamCoverageReport
import com.docuhyphen.app.api.model.audit.export.OrganizationIntegrityReport
import com.docuhyphen.app.api.model.audit.export.StreamIntegrityReport
import com.docuhyphen.app.api.repository.audit.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveVerifier
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.subscription.OrganizationFeatureSubscriptionGuard
import com.docuhyphen.app.api.service.subscription.PlanFeature
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class AuditIntegrityService @Inject constructor(
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditArchiveSegmentRepository: AuditArchiveSegmentRepository,
    private val auditArchiveVerifier: AuditArchiveVerifier,
    private val auditRecorder: AuditRecorder,
    private val subscriptionGuard: OrganizationFeatureSubscriptionGuard,
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
        subscriptionGuard.requireMutation(organizationId, PlanFeature.AUDIT_GOVERNANCE)
        val streamIds = auditLedgerEventRepository.findDistinctStreamIdsByOrganization(organizationId, platformOnly)
        val reports = streamIds.map { streamId -> checkStream(streamId) }

        recordCheck(organizationId, platformOnly, requestedByUserId, reports)
        return OrganizationIntegrityReport(organizationId, platformOnly, reports)
    }

    fun checkStream(streamId: String): StreamIntegrityReport
    {
        val chain = auditArchiveVerifier.verifyStreamChain(streamId)
        val segments = auditArchiveSegmentRepository.findByStreamOrderBySequence(streamId)
        val ledgerHeadSequence = auditLedgerEventRepository.findLatestByStream(streamId)?.streamSequence
        val archiveCoversLedgerHead = ledgerHeadSequence != null &&
                segments.lastOrNull()?.lastSequence == ledgerHeadSequence
        val failureNotes = mutableListOf<String>()
        val ownersValid = auditLedgerEventRepository.findByStreamOrderBySequence(streamId).all {
            it.streamId == streamId && AuditStreamOwnership.matches(it.streamId, it.ownerType, it.ownerId)
        }
        if (!ownersValid) failureNotes += "ledger owner does not match its protected stream"
        var validCount = 0
        for (segment in segments)
        {
            val result = auditArchiveVerifier.verifySegment(segment)
            if (result.valid) validCount++ else failureNotes += "segment[${segment.firstSequence},${segment.lastSequence}]: ${result.note}"
        }

        return StreamIntegrityReport(
            streamId = streamId,
            chainValid = chain.valid && archiveCoversLedgerHead && ownersValid,
            chainNote = if (chain.valid && !archiveCoversLedgerHead)
            {
                "archive ends at ${segments.lastOrNull()?.lastSequence ?: 0}, ledger ends at ${ledgerHeadSequence ?: 0}"
            }
            else
            {
                chain.note
            },
            segmentsChecked = segments.size,
            segmentsValid = validCount,
            segmentFailureNotes = failureNotes,
        )
    }

    fun checkCoverage(streamId: String, fromSequence: Long, toSequence: Long): StreamCoverageReport =
        auditArchiveVerifier.checkRangeCoverage(streamId, fromSequence, toSequence)

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
                    owner = organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
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
            logger.error(
                "AuditIntegrityService: AuditRecorder capture failed for integrity check event: {}",
                e.message,
                e
            )
        }
    }
}
