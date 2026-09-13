package com.docuhyphen.app.api.service.notification

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Periodically delivers committed `workflow_event_outbox` rows via [DomainEventDispatcher.dispatch]
 * and logs backlog health for operational visibility. Mirrors the existing scheduler pattern
 * (`LedgerProcessorScheduler`, `WorkflowEscalationScheduler`): Quarkus' scheduler bean is already on
 * the classpath via `quarkus-arc`, so no new dependency is needed.
 *
 * The job is deliberately tiny: all delivery and health logic stays in [DomainEventDispatcher] so
 * it can also be driven synchronously from tests or an admin action.
 */
@ApplicationScoped
class DomainEventDispatcherScheduler
{
    private val logger = LoggerFactory.getLogger(DomainEventDispatcherScheduler::class.java)

    @Inject private lateinit var dispatcher: DomainEventDispatcher

    @Scheduled(
        every = "\${app.domain-event.outbox.dispatch-every:10s}",
        identity = "domain-event-outbox-dispatch",
        // Skip overlapping fires; one in-flight dispatch pass per node at a time is plenty.
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
