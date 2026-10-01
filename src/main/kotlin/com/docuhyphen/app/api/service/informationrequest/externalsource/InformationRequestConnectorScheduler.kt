package com.docuhyphen.app.api.service.informationrequest.externalsource

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class InformationRequestConnectorScheduler @Inject constructor(
    private val worker: InformationRequestConnectorWorker,
)
{
    @Scheduled(
        every = "\${app.information-request.connectors.every:1m}",
        identity = "information-request-connector-exchanges",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun tick()
    {
        try
        {
            val processed = worker.processDue()
            if (processed > 0) logger.info("information request connector pass: processed={}", processed)
        }
        catch (exception: Exception)
        {
            logger.error("information request connector pass failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestConnectorScheduler::class.java)
    }
}
