package com.docuhyphen.app.api.service.informationrequest.lifecycle

import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.model.notification.DomainEventConsumptionOutcome
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.lifecycle.InformationRequestTransitionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.oversight.InformationRequestOperationsPostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewTestSupport
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventConsumptionService
import com.docuhyphen.app.api.service.notification.DomainEventDispatcher
import com.docuhyphen.app.api.service.notification.DomainEventPublisher
import com.docuhyphen.app.api.service.notification.EventRouter
import com.docuhyphen.app.api.service.notification.ProbeDomainEventConsumer
import com.docuhyphen.app.api.service.notification.TransactionalEventSink
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(InformationRequestOperationsPostgreSQLResource::class)
class InformationRequestEventConsumptionTransactionTest
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository
    @Inject lateinit var transitionRepository: InformationRequestTransitionRepository
    @Inject lateinit var eventRouter: EventRouter
    @Inject lateinit var dispatcher: DomainEventDispatcher
    @Inject lateinit var probe: ProbeDomainEventConsumer
    @Inject lateinit var consumption: DomainEventConsumptionService
    @Inject lateinit var firstViews: InformationRequestFirstViewService

    @Inject
    @TransactionalEventSink
    lateinit var publisher: DomainEventPublisher

    @Test
    fun `a redelivered event causes one business effect for each consumer`()
    {
        val event = probeEvent()
        val before = probe.applied.get()

        eventRouter.routeDurable(event)
        eventRouter.routeDurable(event)

        assertEquals(before + 1, probe.applied.get())
        assertEquals(
            DomainEventConsumptionOutcome.APPLIED,
            QuarkusTransaction.requiringNew().call { consumption.outcomeOf(ProbeDomainEventConsumer.CONSUMER_KEY, event) },
        )
    }

    @Test
    fun `a failed consumption keeps no receipt so the retried delivery applies it once`()
    {
        val event = probeEvent()
        val before = probe.applied.get()
        probe.failNext.set(true)

        assertThrows(IllegalStateException::class.java) { eventRouter.routeDurable(event) }
        assertNull(QuarkusTransaction.requiringNew().call { consumption.outcomeOf(ProbeDomainEventConsumer.CONSUMER_KEY, event) })

        eventRouter.routeDurable(event)
        eventRouter.routeDurable(event)
        assertEquals(before + 1, probe.applied.get())
    }

    @Test
    fun `events sharing an ordering key are delivered in order and a held earlier event holds back the later one`()
    {
        drainOutbox()
        val key = "information_request:${UUID.randomUUID()}"
        val first = probeEvent(key)
        val second = probeEvent(key)
        QuarkusTransaction.requiringNew().run {
            publisher.publish(first, AuditOwnerScope.Platform)
            publisher.publish(second, AuditOwnerScope.Platform)
        }
        setNextAttempt(first, Instant.now().plusSeconds(3600))

        assertEquals(DomainEventDispatcher.DeliveryOutcome.NONE, dispatcher.deliverNext(Timestamp.from(Instant.now())))
        assertEquals("PENDING", statusOf(second))

        setNextAttempt(first, Instant.now().minusSeconds(1))
        assertEquals(DomainEventDispatcher.DeliveryOutcome.DELIVERED, dispatcher.deliverNext(Timestamp.from(Instant.now())))
        assertEquals("DELIVERED", statusOf(first))
        assertEquals("PENDING", statusOf(second))
        assertEquals(DomainEventDispatcher.DeliveryOutcome.DELIVERED, dispatcher.deliverNext(Timestamp.from(Instant.now())))
        assertEquals("DELIVERED", statusOf(second))
    }

    @Test
    fun `a first view by a responding party and the first response are each recorded once as ordered request events`()
    {
        val support = InformationRequestReviewTestSupport(dataSource, runtime, requestRepository, reviewRepository)
        val fixture = support.fixture()
        val services = runtime.build(fixture.requestId)

        assertFalse(firstViews.recordIfFirst(fixture.requestId, support.owner(fixture)))
        assertTrue(firstViews.recordIfFirst(fixture.requestId, support.contributor(fixture)))
        assertFalse(firstViews.recordIfFirst(fixture.requestId, support.attestor(fixture)))
        QuarkusTransaction.requiringNew().run {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            assertEquals(InformationRequestState.ISSUED, request.state)
            assertNotNull(request.firstViewedAt)
        }

        support.patchNarrative(services, fixture, fixture.documentRequirementId, "Recorded on first save")
        support.patchNarrative(services, fixture, fixture.documentRequirementId, "Recorded on second save")

        QuarkusTransaction.requiringNew().run {
            val request = requireNotNull(requestRepository.findById(fixture.requestId))
            assertEquals(InformationRequestState.IN_PROGRESS, request.state)
            assertNotNull(request.startedAt)
            val transitions = transitionRepository.findForRequest(fixture.requestId)
            assertEquals(
                listOf(
                    InformationRequestMutation.RECORD_FIRST_VIEW,
                    InformationRequestMutation.START_RESPONSE,
                    InformationRequestMutation.SAVE_RESPONSE,
                    InformationRequestMutation.SAVE_RESPONSE,
                ),
                transitions.map { it.mutation }.takeLast(4),
            )
            val start = transitions.single { it.mutation == InformationRequestMutation.START_RESPONSE }
            assertEquals(InformationRequestState.ISSUED, start.fromState)
            assertEquals(InformationRequestState.IN_PROGRESS, start.toState)
            assertTrue(
                transitions.filter { it.mutation == InformationRequestMutation.SAVE_RESPONSE }
                    .all { it.fromState == InformationRequestState.IN_PROGRESS && it.toState == InformationRequestState.IN_PROGRESS },
            )
        }
        assertEquals(
            listOf(
                "information_request.request.view",
                "information_request.request.start",
                "information_request.requirement.respond",
                "information_request.requirement.respond",
            ),
            orderedEventTypes("information_request:${fixture.requestId}").takeLast(4),
        )
    }

    private fun probeEvent(orderingKey: String? = null) = DomainEvent(
        type = ProbeDomainEventConsumer.PROBE_TYPE,
        subject = DomainEvent.SubjectRef("PROBE", UUID.randomUUID().toString()),
        orderingKey = orderingKey,
    )

    private fun drainOutbox()
    {
        repeat(DRAIN_LIMIT) {
            if (dispatcher.deliverNext(Timestamp.from(Instant.now())) == DomainEventDispatcher.DeliveryOutcome.NONE) return
        }
    }

    private fun setNextAttempt(event: DomainEvent, at: Instant)
    {
        dataSource.connection.use { connection ->
            execute(connection, "UPDATE workflow_event_outbox SET next_attempt_at = ? WHERE event_id = ?", Timestamp.from(at), event.eventUuid())
        }
    }

    private fun statusOf(event: DomainEvent): String =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT status FROM workflow_event_outbox WHERE event_id = ?").use { statement ->
                statement.setObject(1, event.eventUuid())
                statement.executeQuery().use { rows ->
                    check(rows.next())
                    rows.getString(1)
                }
            }
        }

    private fun orderedEventTypes(orderingKey: String): List<String> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT event_type FROM workflow_event_outbox WHERE ordering_key = ? ORDER BY sequence_number",
            ).use { statement ->
                statement.setString(1, orderingKey)
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    private companion object
    {
        const val DRAIN_LIMIT = 1000
    }
}
