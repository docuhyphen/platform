package com.docuhyphen.app.api.service.notification

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

@ApplicationScoped
class DomainEventDispatcherScheduler
{
    private val logger = LoggerFactory.getLogger(DomainEventDispatcherScheduler::class.java)

    @Inject
    private lateinit var dispatcher: DomainEventDispatcher

    @Scheduled(
        every = "\${app.domain-event.outbox.dispatch-every:10s}",
        identity = "domain-event-outbox-dispatch",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
    )
    fun tick()
    {
        try
        {
            val result = dispatcher.dispatch()
            if (result.delivered > 0 || result.retried > 0 || result.failed > 0)
            {
                logger.info(
                    "domain event outbox dispatch: delivered={} retried={} failed={}",
                    result.delivered, result.retried, result.failed,
                )
            }

            val health = dispatcher.backlogHealth()
            if (health.repeatedFailures > 0 || health.exhausted > 0)
            {
                logger.warn(
                    "domain event outbox backlog: pending={} oldestPendingAgeSeconds={} repeatedFailures={} exhausted={}",
                    health.pending, health.oldestPendingAgeSeconds, health.repeatedFailures, health.exhausted,
                )
            }
        }
        catch (t: Throwable)
        {
            logger.error("domain event outbox dispatch pass failed", t)
        }
    }
}
