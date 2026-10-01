package com.docuhyphen.app.api.service.informationrequest.notice

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class InformationRequestNoticeScheduler @Inject constructor(
    private val worker: InformationRequestNoticeWorker,
)
{
    @Scheduled(
        every = "\${app.information-request.notice.dispatch-every:1m}",
        identity = "information-request-notice-dispatch",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun tick()
    {
        try
        {
            val result = worker.dispatchPending()
            if (result.rendered > 0 || result.delivered > 0 || result.failed > 0)
            {
                logger.info(
                    "information request notice dispatch: rendered={} delivered={} failed={}",
                    result.rendered, result.delivered, result.failed,
                )
            }
        }
        catch (exception: Exception)
        {
            logger.error("information request notice dispatch pass failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoticeScheduler::class.java)
    }
}
