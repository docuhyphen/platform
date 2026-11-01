package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.notification.DomainEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory

@ApplicationScoped
class EventRouter
{
    private val logger = LoggerFactory.getLogger(EventRouter::class.java)

    @Inject
    private lateinit var ruleEngine: NotificationRuleEngine
    @Inject
    private lateinit var dispatcher: DeliveryDispatcher
    @Inject
    private lateinit var sessionApprovalEventHandler: com.docuhyphen.app.api.service.exchange.ExchangeApprovalEventHandler
    @Inject
    private lateinit var consumers: jakarta.enterprise.inject.Instance<DomainEventConsumer>
    @Inject
    private lateinit var consumption: DomainEventConsumptionService

    fun route(event: DomainEvent)
    {
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    open fun routeDurable(event: DomainEvent)
    {
        if (sessionApprovalEventHandler.handles(event.type))
        {
            sessionApprovalEventHandler.handle(event)
        }

        consumers.sortedWith(compareBy<DomainEventConsumer>({ it.routingOrder }, { it.consumerKey }))
            .filter { it.handles(event) }
            .forEach { consumption.consumeOnce(it, event) }
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

