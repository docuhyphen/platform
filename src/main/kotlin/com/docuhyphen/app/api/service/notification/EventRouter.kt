package com.docuhyphen.app.api.service.notification

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
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
    @Inject private lateinit var sessionApprovalEventHandler: com.docuhyphen.app.api.service.exchange.ExchangeApprovalEventHandler

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

        fanOutNotifications(event)
    }

    /**
     * Routing entry point for durably-enqueued events delivered from the transactional outbox. The
     * lifecycle business side-effect (e.g. flipping Share state, advancing the Exchange) is a
     * required outcome for these events, so its failure is propagated to the caller: the dispatcher
     * leaves the outbox row pending and retries it, rather than marking a required lifecycle event
     * delivered when its side-effect never applied. Handlers must therefore be idempotent by
     * business key so a retry after an ambiguous failure does not repeat the side-effect (the
     * lifecycle handler guards every mutation on current status). Notification fan-out remains
     * best-effort: a delivery failure there must not force the whole event to be redelivered.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    open fun routeDurable(event: DomainEvent)
    {
        if (sessionApprovalEventHandler.handles(event.type))
        {
            sessionApprovalEventHandler.handle(event)
        }

        fanOutNotifications(event)
    }

    private fun fanOutNotifications(event: DomainEvent)
    {
        val tasks = ruleEngine.resolveDeliveries(event)
        if (tasks.isEmpty())
        {
            logger.debug("No delivery tasks for event id={} type={}", event.id, event.type)
            return
        }
        dispatcher.dispatchAll(tasks)
    }
}

