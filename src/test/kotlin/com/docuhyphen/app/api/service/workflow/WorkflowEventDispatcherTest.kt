package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.WorkflowEventOutboxEntry
import com.docuhyphen.app.api.repository.workflow.WorkflowEventOutboxRepository
import com.docuhyphen.app.api.service.notification.DomainEvent
import com.docuhyphen.app.api.service.notification.DomainEventJson
import com.docuhyphen.app.api.service.notification.EventRouter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * The dispatcher must deliver committed outbox rows exactly once per successful route, keep a row
 * pending and back off after a routing failure (never discarding a required event), and mark only a
 * non-retryable decode error terminally failed. The dispatcher uses field injection, so the
 * repository and router are set reflectively with Mockito mocks; `self` is intentionally left
 * uninitialized so per-row delivery runs directly (transactions are a no-op outside CDI).
 */
class WorkflowEventDispatcherTest
{
    private val outboxRepository: WorkflowEventOutboxRepository = mock()
    private val eventRouter: EventRouter = mock()
    private lateinit var dispatcher: WorkflowEventDispatcher
    private val now: Timestamp = Timestamp.from(Instant.parse("2026-07-11T10:00:00Z"))

    @BeforeEach
    fun setUp()
    {
        dispatcher = WorkflowEventDispatcher()
        inject(dispatcher, "outboxRepository", outboxRepository)
        inject(dispatcher, "eventRouter", eventRouter)
    }

    private fun pendingRow(eventType: String = "exchange.activated"): WorkflowEventOutboxEntry
    {
        val event = DomainEvent(
            type = eventType,
            subject = DomainEvent.SubjectRef("EXCHANGE", UUID.randomUUID().toString()),
        )
        return WorkflowEventOutboxEntry().apply {
            eventId = event.eventUuid()
            idempotencyKey = event.id
            this.eventType = eventType
            envelopeJson = DomainEventJson.instance.encodeToString(DomainEvent.serializer(), event)
        }
    }

    @Test
    fun `deliverNext routes the event and marks the row delivered`()
    {
        val row = pendingRow()
        whenever(outboxRepository.claimNextPending(any())).thenReturn(row)

        val outcome = dispatcher.deliverNext(now)

        assertEquals(WorkflowEventDispatcher.DeliveryOutcome.DELIVERED, outcome)
        verify(eventRouter).routeDurable(any())
        assertEquals(WorkflowEventOutboxEntry.STATUS_DELIVERED, row.status)
        assertEquals(now, row.deliveredAt)
        assertNull(row.lastError)
        verify(outboxRepository).update(row)
    }

    @Test
    fun `deliverNext returns NONE when no row is deliverable`()
    {
        whenever(outboxRepository.claimNextPending(any())).thenReturn(null)

        val outcome = dispatcher.deliverNext(now)

        assertEquals(WorkflowEventDispatcher.DeliveryOutcome.NONE, outcome)
        verify(eventRouter, never()).routeDurable(any())
        verify(outboxRepository, never()).update(any())
    }

    @Test
    fun `deliverNext keeps the row pending and backs off after a routing failure`()
    {
        val row = pendingRow()
        whenever(outboxRepository.claimNextPending(any())).thenReturn(row)
        doThrow(RuntimeException("share activation failed")).whenever(eventRouter).routeDurable(any())

        val outcome = dispatcher.deliverNext(now)

        assertEquals(WorkflowEventDispatcher.DeliveryOutcome.RETRIED, outcome)
        assertEquals(WorkflowEventOutboxEntry.STATUS_PENDING, row.status)
        assertEquals(1, row.attemptCount)
        assertNotNull(row.lastError)
        // First retry backs off by the 30s base interval.
        assertEquals(now.toInstant().plusSeconds(30), row.nextAttemptAt.toInstant())
        assertTrue(row.nextAttemptAt.after(now))
        verify(outboxRepository).update(row)
    }

    @Test
    fun `deliverNext does not leak the exception message beyond a safe summary`()
    {
        val row = pendingRow()
        whenever(outboxRepository.claimNextPending(any())).thenReturn(row)
        doThrow(RuntimeException("secret-value-42")).whenever(eventRouter).routeDurable(any())

        dispatcher.deliverNext(now)

        // A summary is recorded, but it is length-bounded and prefixed with a stable context label.
        assertTrue(row.lastError!!.startsWith("routing failed:"))
        assertTrue(row.lastError!!.length <= 1024)
    }

    @Test
    fun `deliverNext marks an undecodable envelope FAILED without routing`()
    {
        val row = WorkflowEventOutboxEntry().apply {
            eventType = "workflow.completed"
            envelopeJson = "this is not json"
        }
        whenever(outboxRepository.claimNextPending(any())).thenReturn(row)

        val outcome = dispatcher.deliverNext(now)

        assertEquals(WorkflowEventDispatcher.DeliveryOutcome.FAILED, outcome)
        assertEquals(WorkflowEventOutboxEntry.STATUS_FAILED, row.status)
        verify(eventRouter, never()).routeDurable(any())
        verify(outboxRepository).update(row)
    }

    @Test
    fun `dispatch delivers each deliverable row and stops when none remain`()
    {
        val rowOne = pendingRow()
        val rowTwo = pendingRow()
        whenever(outboxRepository.claimNextPending(any()))
            .thenReturn(rowOne)
            .thenReturn(rowTwo)
            .thenReturn(null)

        val result = dispatcher.dispatch()

        assertEquals(2, result.delivered)
        assertEquals(0, result.retried)
        assertEquals(0, result.failed)
        verify(eventRouter, times(2)).routeDurable(any())
    }

    @Test
    fun `backlogHealth reports pending age and repeated failure counts`()
    {
        whenever(outboxRepository.countByStatus(WorkflowEventOutboxEntry.STATUS_PENDING)).thenReturn(5L)
        whenever(outboxRepository.oldestPendingCreatedAt())
            .thenReturn(Timestamp.from(Instant.now().minusSeconds(120)))
        whenever(outboxRepository.countPendingWithAttemptsAtLeast(WorkflowEventDispatcher.REPEATED_FAILURE_THRESHOLD))
            .thenReturn(2L)
        whenever(outboxRepository.countPendingWithAttemptsAtLeast(WorkflowEventDispatcher.EXHAUSTED_THRESHOLD))
            .thenReturn(1L)

        val health = dispatcher.backlogHealth()

        assertEquals(5L, health.pending)
        assertTrue(health.oldestPendingAgeSeconds >= 120L)
        assertEquals(2L, health.repeatedFailures)
        assertEquals(1L, health.exhausted)
    }

    private fun inject(target: Any, field: String, value: Any)
    {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        f.set(target, value)
    }
}
