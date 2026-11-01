package com.docuhyphen.app.api.service.audit

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class LedgerProcessorScheduler @Inject constructor(
    private val ledgerProcessor: LedgerProcessor,
)
{
    private val logger = LoggerFactory.getLogger(LedgerProcessorScheduler::class.java)

    @Scheduled(
        every = "\${app.audit.ledger.drain-every:15s}",
        identity = "audit-ledger-drain",
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
