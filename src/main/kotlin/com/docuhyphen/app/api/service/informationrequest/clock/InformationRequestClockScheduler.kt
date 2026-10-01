package com.docuhyphen.app.api.service.informationrequest.clock

import com.docuhyphen.app.api.repository.informationrequest.clock.InformationRequestClockRepository
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant

@ApplicationScoped
class InformationRequestClockScheduler @Inject constructor(
    private val clockRepository: InformationRequestClockRepository,
    private val processor: InformationRequestClockPointProcessor,
    private val clock: Clock,
)
{
    @Scheduled(
        every = "\${app.information-request.clock.every:1m}",
        identity = "information-request-clock",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    @ActivateRequestContext
    fun tick()
    {
        try
        {
            val processed = processDuePoints(clock.instant())
            if (processed > 0) logger.info("information request clock points processed={}", processed)
        }
        catch (exception: Exception)
        {
            logger.error("information request clock pass failed", exception)
        }
    }

    fun processDuePoints(now: Instant): Int
    {
        var processed = 0
        val clockIds = QuarkusTransaction.requiringNew().call {
            (clockRepository.findUnstoppedOfFinishedIds(BATCH_SIZE) + clockRepository.findDuePointClockIds(Timestamp.from(now), BATCH_SIZE)).distinct()
        }
        clockIds.forEach { clockId ->
            try
            {
                if (processor.process(clockId, now)) processed++
            }
            catch (exception: Exception)
            {
                logger.warn("information request clock {} point processing failed; it will be retried", clockId, exception)
            }
        }
        return processed
    }

    private companion object
    {
        const val BATCH_SIZE = 200
        val logger = LoggerFactory.getLogger(InformationRequestClockScheduler::class.java)
    }
}
