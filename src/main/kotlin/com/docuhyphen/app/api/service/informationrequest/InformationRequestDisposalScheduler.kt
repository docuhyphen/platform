package com.docuhyphen.app.api.service.informationrequest

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class InformationRequestDisposalScheduler @Inject constructor(
    private val worker: InformationRequestDisposalWorker,
)
{
    @Scheduled(
        every = "\${app.record-disposal.every:1h}",
        identity = "information-request-record-disposal",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun tick()
    {
        try
        {
            val run = worker.run()
            if (run.finalized > 0 || run.pending > 0)
            {
                logger.info("information request disposal: finalized={} pending={} refused={}", run.finalized, run.pending, run.refused)
            }
        }
        catch (exception: Exception)
        {
            logger.error("information request disposal pass failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestDisposalScheduler::class.java)
    }
}
