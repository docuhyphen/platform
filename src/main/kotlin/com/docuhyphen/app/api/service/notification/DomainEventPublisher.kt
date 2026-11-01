package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.notification.DomainEvent
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

interface DomainEventPublisher
{
    fun publish(event: DomainEvent)
    fun publish(event: DomainEvent, owner: AuditOwnerScope) = publish(event)
    fun publishAll(events: Collection<DomainEvent>) = events.forEach(::publish)
}

@ApplicationScoped
class InProcessDomainEventPublisher : DomainEventPublisher
{
    private val logger = LoggerFactory.getLogger(InProcessDomainEventPublisher::class.java)

    @Inject
    private lateinit var eventRouter: EventRouter

    override fun publish(event: DomainEvent)
    {
        logger.debug("Publishing event id={} type={}", event.id, event.type)
        try
        {
            eventRouter.route(event)
        }
        catch (t: Throwable)
        {
            logger.error("EventRouter failed for event id=${event.id} type=${event.type}", t)
        }
    }
}

