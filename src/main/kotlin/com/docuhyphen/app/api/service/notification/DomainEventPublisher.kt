package com.docuhyphen.app.api.service.notification

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

/**
 * Single entry point services call when something happens worth notifying about
 * (`domainEventPublisher.publish(event)`). The publisher is responsible for getting the
 * event onto whatever transport the deployment is configured for.
 *
 * Two implementations exist:
 *   * [InProcessDomainEventPublisher] (the CDI default): routes the event synchronously through
 *     [EventRouter] on the calling thread and swallows router failures so notifications can never
 *     break the calling business transaction. Used by every non-workflow service.
 *   * [com.docuhyphen.app.api.service.workflow.WorkflowEventOutboxPublisher] (selected by the
 *     [WorkflowEventSink] qualifier): enqueues the event into a transactional outbox row that
 *     commits atomically with the workflow state mutation, and a background dispatcher routes it
 *     after commit. Used by the workflow engine so required lifecycle and terminal events are never
 *     lost by a crash between the state commit and routing.
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

