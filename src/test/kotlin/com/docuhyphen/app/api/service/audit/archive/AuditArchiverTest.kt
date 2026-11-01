package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.LedgerProcessor
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class InMemoryAuditArchiveStorage : AuditArchiveStorage
{
    val objects: MutableMap<String, ByteArray> = mutableMapOf()

    override fun putObject(key: String, bytes: ByteArray)
    {
        if (objects.containsKey(key)) throw AuditArchiveObjectAlreadyExistsException(key)
        objects[key] = bytes
    }

    override fun getObject(key: String): ByteArray = objects[key] ?: throw AuditArchiveObjectNotFoundException(key)

    override fun objectExists(key: String): Boolean = objects.containsKey(key)

    override fun listKeysWithPrefix(prefix: String): List<String> = objects.keys.filter { it.startsWith(prefix) }
}

class AuditArchiverTest
{
    @Test
    fun `streams without a ready segment do not consume the archive batch`(@TempDir tempDir: Path) {
        val idle = (1..50).map { "idle-$it" }
        val ready = "user:${UUID.randomUUID()}:2026-01"
        val ledger = mock<AuditLedgerEventRepository>()
        whenever(ledger.findDistinctStreamIds()).thenReturn(idle + ready)
        idle.forEach { whenever(ledger.findByStreamOrderBySequence(it)).thenReturn(emptyList()) }
        whenever(ledger.findByStreamOrderBySequence(ready)).thenReturn(ledgerEvents(ready, 1L..3L))
        val segments = mock<AuditArchiveSegmentRepository>()
        whenever(segments.insert(any())).thenAnswer { it.getArgument(0) }
        val config = configService(3, tempDir)
        val result = AuditArchiver(
            ledger,
            segments,
            InMemoryAuditArchiveStorage(),
            LocalAuditArchiveSigningKeyProvider(config),
            config
        )
            .closeReadySegments(1)
        assertEquals(1, result.closed)
    }

    private fun ledgerEvents(streamId: String, sequences: LongRange, firstPrevHash: String? = null): List<AuditLedgerEvent>
    {
        var prevHash = firstPrevHash
        return sequences.map { sequence ->
            val event = AuditLedgerEvent().apply {
                eventId = UUID.randomUUID()
                eventTypeKey = "exchange.lifecycle.rescinded"
                category = "EXCHANGE"
                outcome = "SUCCESS"
                schemaVersion = 4
                occurredAt = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z"))
                recordedAt = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z"))
                this.streamId = streamId
                streamSequence = sequence
                actorKind = "HUMAN"
                payloadJson = """{"field":"value-$sequence"}"""
                when (val owner = LedgerProcessor.ownerOfStream(streamId)) {
                    com.docuhyphen.app.api.model.audit.AuditOwnerScope.Platform -> {
                        ownerType = "PLATFORM"
                        ownerId = null
                        organizationId = null
                    }

                    is com.docuhyphen.app.api.model.audit.AuditOwnerScope.Personal -> {
                        ownerType = "USER"
                        ownerId = owner.userId
                        organizationId = null
                    }

                    is com.docuhyphen.app.api.model.audit.AuditOwnerScope.Organization -> {
                        ownerType = "ORGANIZATION"
                        ownerId = owner.organizationId
                        organizationId = owner.organizationId
                    }
                }
                this.prevHash = prevHash
            }
            val canonicalJson = LedgerProcessor.canonicalEnvelopeJson(
                eventId = event.eventId.toString(),
                eventTypeKey = event.eventTypeKey,
                category = event.category,
                outcome = event.outcome,
                schemaVersion = event.schemaVersion,
                occurredAt = event.occurredAt.toInstant().toString(),
                recordedAt = event.recordedAt.toInstant().toString(),
                streamId = event.streamId,
                actorKind = event.actorKind,
                actorId = event.actorId?.toString(),
                actorRole = event.actorRole,
                actorLabel = event.actorLabel,
                sessionId = event.sessionId,
                serverTraceId = event.serverTraceId,
                correlationId = event.correlationId,
                causationId = event.causationId,
                organizationId = event.organizationId?.toString(),
                organizationLabel = event.organizationLabel,
                targetType = event.targetType,
                targetId = event.targetId,
                targetLabel = event.targetLabel,
                reason = event.reason,
                payloadJson = event.payloadJson,
            )
            event.eventHash = LedgerProcessor.computeHash(canonicalJson, sequence, prevHash)
            prevHash = event.eventHash
            event
        }
    }

    private fun configService(segmentSize: Int, tempDir: Path): AuditArchiveConfigService
    {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getSegmentSize()).thenReturn(segmentSize)
        whenever(config.getLocalSigningDirectory()).thenReturn(tempDir.resolve("keys").toString())
        return config
    }

    @Test
    fun `a stream with fewer events than the segment size is left open`(@TempDir tempDir: Path)
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(ledgerEvents(streamId, 1L..1L))

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(null)

        val config = configService(segmentSize = 5, tempDir = tempDir)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, InMemoryAuditArchiveStorage(), LocalAuditArchiveSigningKeyProvider(config), config)

        val closed = archiver.closeSegmentIfReady(streamId, force = false)

        assertFalse(closed)
    }

    @Test
    fun `closing a segment uploads a signed manifest that AuditArchiveVerifier accepts`(@TempDir tempDir: Path)
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val events = ledgerEvents(streamId, 1L..3L)

        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events)

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(null)
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }

        val storage = InMemoryAuditArchiveStorage()
        val config = configService(segmentSize = 3, tempDir = tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)

        val closed = archiver.closeSegmentIfReady(streamId, force = false)
        assertTrue(closed)

        val segmentCaptor = org.mockito.kotlin.argumentCaptor<com.docuhyphen.app.api.model.entity.AuditArchiveSegment>()
        org.mockito.kotlin.verify(segmentRepo).insert(segmentCaptor.capture())
        val segment = segmentCaptor.firstValue

        assertEquals(1L, segment.firstSequence)
        assertEquals(3L, segment.lastSequence)
        assertEquals(3, segment.eventCount)
        assertNull(segment.prevSegmentDigest)
        assertEquals(MerkleTree.computeRoot(events.map { it.eventHash }), segment.merkleRoot)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)
        assertTrue(result.valid, result.note)
    }

    @Test
    fun `closing a second segment links prevSegmentDigest to the first segment's segmentDigest`(@TempDir tempDir: Path)
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val firstBatch = ledgerEvents(streamId, 1L..2L)
        val secondBatch = ledgerEvents(streamId, 3L..4L, firstPrevHash = firstBatch.last().eventHash)

        val ledgerRepo = mock<AuditLedgerEventRepository>()
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }

        val storage = InMemoryAuditArchiveStorage()
        val config = configService(segmentSize = 2, tempDir = tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)

        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(firstBatch)
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(null)
        archiver.closeSegmentIfReady(streamId, force = false)

        val firstCaptor = org.mockito.kotlin.argumentCaptor<com.docuhyphen.app.api.model.entity.AuditArchiveSegment>()
        org.mockito.kotlin.verify(segmentRepo).insert(firstCaptor.capture())
        val firstSegment = firstCaptor.firstValue

        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(firstBatch + secondBatch)
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(firstSegment)
        archiver.closeSegmentIfReady(streamId, force = false)

        val allCaptor = org.mockito.kotlin.argumentCaptor<com.docuhyphen.app.api.model.entity.AuditArchiveSegment>()
        org.mockito.kotlin.verify(segmentRepo, org.mockito.kotlin.times(2)).insert(allCaptor.capture())
        val secondSegment = allCaptor.secondValue

        assertEquals(firstSegment.segmentDigest, secondSegment.prevSegmentDigest)
        assertEquals(3L, secondSegment.firstSequence)
        assertEquals(4L, secondSegment.lastSequence)
    }

    @Test
    fun `closed segments still verify after the database stores their creation time to the microsecond`(@TempDir tempDir: Path) {
        val streamIds = (1..10).map { "${UUID(0L, it.toLong())}:2026-01" }
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        streamIds.forEach { whenever(ledgerRepo.findByStreamOrderBySequence(it)).thenReturn(ledgerEvents(it, 1L..1L)) }

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(any())).thenReturn(null)
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }

        val storage = InMemoryAuditArchiveStorage()
        val config = configService(segmentSize = 1, tempDir = tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)
        streamIds.forEach { archiver.closeSegmentIfReady(it, force = false) }

        val captor = org.mockito.kotlin.argumentCaptor<com.docuhyphen.app.api.model.entity.AuditArchiveSegment>()
        org.mockito.kotlin.verify(segmentRepo, org.mockito.kotlin.times(streamIds.size)).insert(captor.capture())

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        captor.allValues.forEach { segment ->
            segment.createdAt = storedAtDatabasePrecision(segment.createdAt)
            val result = verifier.verifySegment(segment)
            assertTrue(result.valid, "${segment.streamId}: ${result.note}")
        }
    }

    private fun storedAtDatabasePrecision(timestamp: Timestamp): Timestamp =
        Timestamp.from(timestamp.toInstant().plusNanos(500).truncatedTo(ChronoUnit.MICROS))
}
