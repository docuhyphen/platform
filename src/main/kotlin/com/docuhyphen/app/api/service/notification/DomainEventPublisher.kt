package com.docuhyphen.app.api.service.notification

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Single entry point services call when something happens worth notifying about
 * (`domainEventPublisher.publish(event)`). The publisher is responsible for getting the
 * event onto whatever transport the deployment is configured for.
 *
 * Iteration 3 ships the **in-process** implementation: the publisher calls
 * [EventRouter] synchronously on the same thread/transaction. A Kafka-backed
 * implementation (using SmallRye Reactive Messaging `Emitter<DomainEvent>` on topic
 * `docuhyphen.events.v1`) is the planned drop-in replacement; it will be wired behind a
 * feature flag in a later iteration so existing transactional semantics aren't disrupted.
 */
interface DomainEventPublisher
{
    fun publish(event: DomainEvent)
    fun publishAll(events: Collection<DomainEvent>) = events.forEach(::publish)
}

@ApplicationScoped
class InProcessDomainEventPublisher : DomainEventPublisher
{
    private val logger = LoggerFactory.getLogger(InProcessDomainEventPublisher::class.java)

    @Inject private lateinit var eventRouter: EventRouter

    override fun publish(event: DomainEvent)
    {
        logger.debug("Publishing event id={} type={}", event.id, event.type)
        try
        {
            eventRouter.route(event)
        }
        catch (t: Throwable)
        {
            // Notifications must never break the calling business transaction.
            logger.error("EventRouter failed for event id=${event.id} type=${event.type}", t)
        }
    }
}

