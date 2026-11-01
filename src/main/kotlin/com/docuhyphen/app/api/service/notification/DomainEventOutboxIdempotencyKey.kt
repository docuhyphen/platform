package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.notification.DomainEvent

object DomainEventOutboxIdempotencyKey
{
    fun forEvent(event: DomainEvent): String = "${namespaceOf(event.type)}:${event.id}"

    private fun namespaceOf(eventType: String): String =
        eventType.substringBefore('.')
            .trim()
            .takeIf { it.isNotBlank() }
            ?: "domain_event"
}
