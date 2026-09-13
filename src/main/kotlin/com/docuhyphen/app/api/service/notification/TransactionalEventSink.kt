package com.docuhyphen.app.api.service.notification

import jakarta.inject.Qualifier

/**
 * CDI qualifier selecting the durable, outbox-backed [DomainEventPublisher]. Events published
 * through this sink are enqueued into the transactional event outbox and delivered asynchronously
 * by a dispatcher, rather than routed synchronously on the calling transaction.
 * Unqualified [DomainEventPublisher] injection continues to resolve to the default in-process
 * publisher for every other service.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
annotation class TransactionalEventSink
