package com.docuhyphen.app.api.service.notification

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Single per-process funnel: every [DomainEvent] flows through here. Iteration 3 runs
 * synchronously; a Kafka-backed router (with consumer + outbox table) is the planned
 * drop-in replacement.
 *
 * The router intentionally has no other behaviour beyond "route through rule engine
 * → dispatcher" so adding new sinks (analytics, webhooks, audit-log mirror) is a
 * matter of injecting them here.
 */
@ApplicationScoped
class EventRouter
{
    private val logger = LoggerFactory.getLogger(EventRouter::class.java)

    @Inject private lateinit var ruleEngine: NotificationRuleEngine
    @Inject private lateinit var dispatcher: DeliveryDispatcher
    @Inject private lateinit var sessionApprovalEventHandler: com.docuhyphen.app.api.service.sharingsession.SessionApprovalEventHandler

    fun route(event: DomainEvent)
    {
        // Business side-effects first (e.g. approval workflow outcomes flip Share state), so the
        // resulting state is consistent before notifications about it fan out. Failures here must
        // not block the notification path.
        if (sessionApprovalEventHandler.handles(event.type))
        {
            try
            {
                sessionApprovalEventHandler.handle(event)
            }
            catch (t: Throwable)
            {
                logger.error("Session-approval side-effect failed for event id={} type={}", event.id, event.type, t)
            }
        }

        val tasks = ruleEngine.resolveDeliveries(event)
        if (tasks.isEmpty())
        {
            logger.debug("No delivery tasks for event id={} type={}", event.id, event.type)
            return
        }
        dispatcher.dispatchAll(tasks)
    }
}

