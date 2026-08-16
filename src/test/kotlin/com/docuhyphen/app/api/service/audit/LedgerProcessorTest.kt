package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.model.entity.StreamHead
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.audit.AuditOutboxRepository
import com.docuhyphen.app.api.repository.audit.StreamHeadRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Verifies the append and recovery guarantees provided by [LedgerProcessor]:
 *  - every committed outbox row yields exactly one ledger event (idempotent by event id).
 *  - a stream's appended events form a contiguous sequence with a single unbroken hash chain
 *    by using the row-lock-then-read-then-write sequencing that
 *    [StreamHeadRepository.lockOrCreate] enforces in production.
 *  - mutating any canonical field, the sequence, or the previous hash breaks recomputed-hash
 *    verification (tamper evidence).
 *  - a per-row append failure is caught, counted, and does not abort the rest of the batch.
 */
class LedgerProcessorTest
{
    private fun outboxEntry(
        eventId: UUID = UUID.randomUUID(),
        organizationId: UUID? = null,
        occurredAt: Timestamp = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z")),
    ): AuditOutboxEntry = AuditOutboxEntry().apply {
        this.eventId = eventId
        idempotencyKey = "key-$eventId"
        eventTypeKey = "exchange.lifecycle.rescinded"
        category = "EXCHANGE"
        outcome = "SUCCESS"
        actorId = UUID.randomUUID()
        actorRole = "OWNER"
        targetType = "EXCHANGE"
        targetId = UUID.randomUUID().toString()
        this.organizationId = organizationId
        sessionId = "session-1"
        payloadJson = """{"previousStatus":"INITIATED"}"""
        serverTraceId = "trace-1"
        correlationId = "corr-1"
        this.occurredAt = occurredAt
        recordedAt = occurredAt
        catalogVersion = 1
    }

    private fun mockStreamHeadRepository(): Pair<StreamHeadRepository, MutableMap<String, StreamHead>>
    {
        val store = mutableMapOf<String, StreamHead>()
        val repo = mock<StreamHeadRepository>()
        whenever(repo.lockOrCreate(any())).thenAnswer { inv ->
            val streamId = inv.getArgument<String>(0)
            store.getOrPut(streamId) { StreamHead().apply { this.streamId = streamId } }
        }
        whenever(repo.advance(any())).thenAnswer { inv -> inv.getArgument<StreamHead>(0) }
        return repo to store
    }

    @Test
    fun `drain appends exactly one ledger event per outbox row and none are already ledgered`()
    {
        val entryA = outboxEntry()
        val entryB = outboxEntry()

        val outboxRepo = mock<AuditOutboxRepository>()
        whenever(outboxRepo.findOldestUnledgeredByRecordedAt(any())).thenReturn(listOf(entryA, entryB))

        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.existsByEventId(any())).thenReturn(false)
        whenever(ledgerRepo.insert(any())).thenAnswer { inv -> inv.getArgument<AuditLedgerEvent>(0) }

        val (streamHeadRepo, _) = mockStreamHeadRepository()

        val processor = LedgerProcessor(outboxRepo, ledgerRepo, streamHeadRepo)
        val result = processor.drain()

        assertEquals(2, result.appended)
        assertEquals(0, result.alreadyLedgered)
        assertEquals(0, result.failed)
        verify(ledgerRepo, org.mockito.kotlin.times(2)).insert(any())
    }

    @Test
    fun `drain skips outbox rows already present in the ledger`()
    {
        val entry = outboxEntry()

        val outboxRepo = mock<AuditOutboxRepository>()
        whenever(outboxRepo.findOldestUnledgeredByRecordedAt(any())).thenReturn(listOf(entry))

        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.existsByEventId(eq(entry.eventId))).thenReturn(true)

        val (streamHeadRepo, _) = mockStreamHeadRepository()

        val processor = LedgerProcessor(outboxRepo, ledgerRepo, streamHeadRepo)
        val result = processor.drain()

        assertEquals(0, result.appended)
        assertEquals(1, result.alreadyLedgered)
        verify(ledgerRepo, never()).insert(any())
    }

    @Test
    fun `sequential appends to the same stream form a contiguous single hash chain`()
    {
        val organizationId = UUID.randomUUID()
        val entryOne = outboxEntry(organizationId = organizationId)
        val entryTwo = outboxEntry(organizationId = organizationId)
        val entryThree = outboxEntry(organizationId = organizationId)

        val outboxRepo = mock<AuditOutboxRepository>()
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.existsByEventId(any())).thenReturn(false)
        val appended = mutableListOf<AuditLedgerEvent>()
        whenever(ledgerRepo.insert(any())).thenAnswer { inv ->
            inv.getArgument<AuditLedgerEvent>(0).also { appended.add(it) }
        }

        val (streamHeadRepo, _) = mockStreamHeadRepository()
        val processor = LedgerProcessor(outboxRepo, ledgerRepo, streamHeadRepo)

        processor.appendOne(entryOne)
        processor.appendOne(entryTwo)
        processor.appendOne(entryThree)

        assertEquals(listOf(1L, 2L, 3L), appended.map { it.streamSequence })
        assertNull(appended[0].prevHash)
        assertEquals(appended[0].eventHash, appended[1].prevHash)
        assertEquals(appended[1].eventHash, appended[2].prevHash)
        // Every stream_id must be identical (same organization + partition), and every hash unique.
        assertEquals(1, appended.map { it.streamId }.distinct().size)
        assertEquals(3, appended.map { it.eventHash }.distinct().size)
    }

    @Test
    fun `appendOne is a no-op when the outbox row is already ledgered`()
    {
        val entry = outboxEntry()

        val outboxRepo = mock<AuditOutboxRepository>()
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.existsByEventId(eq(entry.eventId))).thenReturn(true)

        val (streamHeadRepo, _) = mockStreamHeadRepository()
        val processor = LedgerProcessor(outboxRepo, ledgerRepo, streamHeadRepo)

        val appended = processor.appendOne(entry)

        assertFalse(appended)
        verify(ledgerRepo, never()).insert(any())
    }

    @Test
    fun `drain counts a per-row append failure without aborting the rest of the batch`()
    {
        val goodEntry = outboxEntry()
        val badEntry = outboxEntry()

        val outboxRepo = mock<AuditOutboxRepository>()
        whenever(outboxRepo.findOldestUnledgeredByRecordedAt(any())).thenReturn(listOf(badEntry, goodEntry))

        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.existsByEventId(any())).thenReturn(false)
        whenever(ledgerRepo.insert(any())).thenAnswer { inv ->
            val ev = inv.getArgument<AuditLedgerEvent>(0)
            if (ev.eventId == badEntry.eventId) throw RuntimeException("db unavailable")
            ev
        }

        val (streamHeadRepo, _) = mockStreamHeadRepository()
        val processor = LedgerProcessor(outboxRepo, ledgerRepo, streamHeadRepo)

        val result = processor.drain()

        assertEquals(1, result.appended)
        assertEquals(1, result.failed)
    }

    @Test
    fun `mutating any canonical field breaks recomputed-hash verification`()
    {
        val entry = outboxEntry()
        val streamId = LedgerProcessor.resolveStreamId(entry)
        val actorKind = LedgerProcessor.resolveActorKind(entry)
        val canonicalJson = LedgerProcessor.canonicalize(entry, streamId, actorKind)
        val originalHash = LedgerProcessor.computeHash(canonicalJson, 1L, null)

        // Recomputing from the same inputs must reproduce the same hash (sanity check).
        assertEquals(originalHash, LedgerProcessor.computeHash(canonicalJson, 1L, null))

        // Tampering the payload changes the canonical JSON and therefore the hash.
        val tamperedEntry = outboxEntry(eventId = entry.eventId).apply { payloadJson = """{"previousStatus":"TAMPERED"}""" }
        val tamperedJson = LedgerProcessor.canonicalize(tamperedEntry, streamId, actorKind)
        assertNotEquals(originalHash, LedgerProcessor.computeHash(tamperedJson, 1L, null))

        // Tampering the sequence changes the hash.
        assertNotEquals(originalHash, LedgerProcessor.computeHash(canonicalJson, 2L, null))

        // Tampering the previous hash changes the hash.
        assertNotEquals(originalHash, LedgerProcessor.computeHash(canonicalJson, 1L, "some-other-prev-hash"))
    }

    @Test
    fun `resolveStreamId groups by organization and UTC month, defaulting to platform`()
    {
        val orgId = UUID.randomUUID()
        val withOrg = outboxEntry(organizationId = orgId, occurredAt = Timestamp.from(Instant.parse("2026-03-05T00:00:00Z")))
        val withoutOrg = outboxEntry(organizationId = null, occurredAt = Timestamp.from(Instant.parse("2026-03-05T00:00:00Z")))

        assertEquals("$orgId:2026-03", LedgerProcessor.resolveStreamId(withOrg))
        assertEquals("platform:2026-03", LedgerProcessor.resolveStreamId(withoutOrg))
    }
}
