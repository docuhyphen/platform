package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.archive.SegmentVerificationResult
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class AuditArchiveScheduler @Inject constructor(
    private val archiver: AuditArchiver,
    private val verifier: AuditArchiveVerifier,
    private val configService: AuditArchiveConfigService,
    private val auditRecorder: AuditRecorder,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditArchiveScheduler::class.java)
        private const val ALERT_MARKER = "AUDIT_ARCHIVE_VERIFICATION_FAILED"
    }

    @Scheduled(
        every = "\${app.audit.archive.archive-every:5m}",
        identity = "audit-archive-close-segments",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun archiveTick()
    {
        if (!configService.isArchiveEnabled()) return

        try
        {
            val result = archiver.closeReadySegments()
            if (result.closed > 0 || result.failed > 0)
            {
                logger.info(
                    "audit archive close pass: closed={} skipped={} failed={}",
                    result.closed, result.skipped, result.failed,
                )
            }
        }
        catch (e: Exception)
        {
            logger.error("audit archive close pass failed: {}", e.message, e)
        }
    }

    @Scheduled(
        every = "\${app.audit.archive.verify-every:1h}",
        identity = "audit-archive-verify-segments",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun verifyTick()
    {
        if (!configService.isArchiveEnabled()) return

        try
        {
            val results = verifier.verifyDueSegments(configService.getReverifyAfterHours())
            for ((segment, result) in results)
            {
                runCatching {
                    recordVerificationResult(
                        segment.streamId,
                        segment.firstSequence,
                        segment.lastSequence,
                        result
                    )
                }
                    .onFailure {
                        logger.error(
                            "{}: verification result not recorded streamId={} range=[{},{}]",
                            ALERT_MARKER, segment.streamId, segment.firstSequence, segment.lastSequence, it,
                        )
                    }
            }

            for (streamId in results.map { it.first.streamId }.distinct()) {
                runCatching { verifier.verifyStreamChain(streamId) }
                    .onSuccess { chainResult ->
                        if (!chainResult.valid) {
                            logger.error(
                                "{}: stream chain break streamId={} note={}",
                                ALERT_MARKER,
                                streamId,
                                chainResult.note
                            )
                        }
                    }
                    .onFailure { logger.error("{}: stream chain not verified streamId={}", ALERT_MARKER, streamId, it) }
            }
        }
        catch (e: Exception)
        {
            logger.error("audit archive verification pass failed: {}", e.message, e)
        }
    }

    private fun recordVerificationResult(
        streamId: String,
        firstSequence: Long,
        lastSequence: Long,
        result: SegmentVerificationResult,
    )
    {
        if (!result.valid)
        {
            logger.error(
                "{}: streamId={} range=[{},{}] note={}",
                ALERT_MARKER, streamId, firstSequence, lastSequence, result.note,
            )
        }

        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    owner = LedgerProcessor.ownerOfStream(streamId),
                    eventTypeKey = if (result.valid) AuditEventType.ARCHIVE_INTEGRITY_VERIFIED.key else AuditEventType.ARCHIVE_INTEGRITY_FAILED.key,
                    outcome = if (result.valid) AuditOutcome.SUCCESS else AuditOutcome.FAILURE,
                    actorKind = AuditActorKind.SYSTEM,
                    targetType = "AUDIT_STREAM",
                    targetId = streamId,
                    payload = mapOf(
                        "first_sequence" to firstSequence.toString(),
                        "last_sequence" to lastSequence.toString(),
                        "note" to result.note,
                    ),
                ),
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.error("audit archive verification event draft invalid: {}", e.message, e)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("audit archive verification event capture failed: {}", e.message, e)
        }
    }
}
