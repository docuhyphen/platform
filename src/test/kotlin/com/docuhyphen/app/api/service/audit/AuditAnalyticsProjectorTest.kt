package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditAnalyticsFact
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditAnalyticsFactRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Verifies [AuditAnalyticsProjector]: projecting the same ledger event twice never
 * produces a second fact row (idempotent under retry/concurrent ticks), and the projected fact
 * carries only denormalized dimensions - never [AuditLedgerEvent.payloadJson] - so a prohibited
 * payload field can never reach the projection.
 */
class AuditAnalyticsProjectorTest
{
    private fun ledgerEvent(): AuditLedgerEvent = AuditLedgerEvent().apply {
        eventId = UUID.randomUUID()
        eventTypeKey = "exchange.lifecycle.ended"
        category = "EXCHANGE"
        outcome = "SUCCESS"
        schemaVersion = 7
        occurredAt = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        streamId = "platform:2026-01"
        actorKind = "HUMAN"
        eventHash = "hash"
        payloadJson = """{"secret_token":"should-never-be-projected"}"""
    }

    @Test
    fun `projecting the same ledger event twice does not duplicate the fact row`()
    {
        val projectedIds = mutableSetOf<UUID>()
        val factRepo = mock<AuditAnalyticsFactRepository>()
        whenever(factRepo.existsByLedgerEventId(any())).thenAnswer { projectedIds.contains(it.getArgument(0)) }
        whenever(factRepo.insert(any())).thenAnswer {
            val fact = it.getArgument<AuditAnalyticsFact>(0)
            projectedIds.add(fact.ledgerEventId)
            fact
        }

        val projector = AuditAnalyticsProjector(mock<AuditLedgerEventRepository>(), factRepo)
        val event = ledgerEvent()

        assertEquals(true, projector.projectOne(event))
        assertFalse(projector.projectOne(event))
        assertEquals(1, projectedIds.size)
    }

    @Test
    fun `the projected fact never carries the ledger event's payload`()
    {
        val factRepo = mock<AuditAnalyticsFactRepository>()
        whenever(factRepo.existsByLedgerEventId(any())).thenReturn(false)
        val captured = mutableListOf<AuditAnalyticsFact>()
        whenever(factRepo.insert(any())).thenAnswer {
            val fact = it.getArgument<AuditAnalyticsFact>(0)
            captured.add(fact)
            fact
        }

        val projector = AuditAnalyticsProjector(mock<AuditLedgerEventRepository>(), factRepo)
        projector.projectOne(ledgerEvent())

        // AuditAnalyticsFact has no payload field at all - the assertion is structural, not content-based.
        assertEquals(1, captured.size)
        assertEquals("EXCHANGE", captured.first().category)
    }
}
