package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Periodically closes ready ledger ranges into signed archive segments ([AuditArchiver]) and
 * continuously re-verifies previously archived segments ([AuditArchiveVerifier]), recording every
 * verification result (success or failure) as its own immutable audit event - the architecture's
 * "continuous verification job emits its result as an immutable audit event and alerts on failure
 * via CloudWatch. CloudWatch alerting is achieved by logging
 * failures at ERROR with a distinctive, greppable message that a CloudWatch Logs metric filter +
 * alarm can match (see `infra/cloudformation.yml`), reusing the CloudWatch Logs group already in
 * the stack rather than adding a new AWS service.
 *
 * [AuditRecorder] is `@RequestScoped` (it derives per-request actor/correlation context), so the
 * verification tick uses [ActivateRequestContext] to open a request context for the duration of
 * the scheduled invocation, exactly as recommended for injecting request-scoped beans into a
 * background job; with no inbound HTTP request, [AuditEventDraft.actorKind] is explicitly set to
 * `SYSTEM` so the ledger never has to guess.
 */
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
                recordVerificationResult(segment.streamId, segment.firstSequence, segment.lastSequence, result)
            }

            val failedStreams = results.map { it.first.streamId }.distinct()
            for (streamId in failedStreams)
            {
                val chainResult = verifier.verifyStreamChain(streamId)
                if (!chainResult.valid)
                {
                    logger.error("{}: stream chain break streamId={} note={}", ALERT_MARKER, streamId, chainResult.note)
                }
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
