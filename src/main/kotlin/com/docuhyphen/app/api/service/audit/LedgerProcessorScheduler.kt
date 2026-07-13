package com.docuhyphen.app.api.service.audit

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Periodically drains `audit_outbox` into the ordered ledger via [LedgerProcessor.drain]. Mirrors
 * the existing `WorkflowEscalationScheduler` pattern: Quarkus' scheduler bean is already on the
 * classpath transitively via `quarkus-arc`, no new dependency needed.
 *
 * The job is deliberately tiny: all logic stays in [LedgerProcessor] so it can also be invoked
 * synchronously from tests or an admin action without waiting for the next tick.
 */
@ApplicationScoped
class LedgerProcessorScheduler @Inject constructor(
    private val ledgerProcessor: LedgerProcessor,
)
{
    private val logger = LoggerFactory.getLogger(LedgerProcessorScheduler::class.java)

    @Scheduled(
        every = "\${app.audit.ledger.drain-every:15s}",
        identity = "audit-ledger-drain",
        // Skip overlapping fires; one in-flight drain pass at a time is plenty.
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun tick()
    {
        try
        {
            val batchSize = 200
            val result = ledgerProcessor.drain(batchSize)
            if (result.appended > 0 || result.failed > 0)
            {
                logger.info(
                    "audit ledger drain: appended={} alreadyLedgered={} failed={}",
                    result.appended, result.alreadyLedgered, result.failed,
                )
            }
        }
        catch (e: Exception)
        {
            logger.error("audit ledger drain pass failed: {}", e.message, e)
        }
    }
}
