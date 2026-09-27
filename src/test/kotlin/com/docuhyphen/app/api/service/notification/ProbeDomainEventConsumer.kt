package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.notification.DomainEventConsumptionResult
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@ApplicationScoped
class ProbeDomainEventConsumer : DomainEventConsumer
{
    val applied = AtomicInteger()
    val failNext = AtomicBoolean(false)

    override val consumerKey: String = CONSUMER_KEY

    override fun handles(event: DomainEvent): Boolean = event.type == PROBE_TYPE

    override fun consume(event: DomainEvent): DomainEventConsumptionResult
    {
        if (failNext.compareAndSet(true, false)) throw IllegalStateException("probe consumer failed once")
        applied.incrementAndGet()
        return DomainEventConsumptionResult.applied("probe")
    }

    companion object
    {
        const val CONSUMER_KEY = "test-probe"
        const val PROBE_TYPE = "test.consumption.probe"
    }
}
