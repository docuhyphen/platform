package com.docuhyphen.app.api.service.audit.archive

import com.docuhyphen.app.api.model.audit.archive.ArchiveCoverageState
import com.docuhyphen.app.api.model.audit.archive.ArchivedLedgerEventRecord
import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.LedgerProcessor
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.*
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class AuditArchiveVerifierTest
{
    @Test
    fun `a signed personal archive verifies with matching owner metadata`(@TempDir tempDir: Path) {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, repository, signing) = archiveOneSegment("user:${UUID.randomUUID()}:2026-01", tempDir, storage)
        val result = AuditArchiveVerifier(repository, storage, signing).verifySegment(segment)
        assertTrue(result.valid, result.note)
    }

    @Test
    fun `signed personal archive refuses owner metadata inconsistent with its stream`(@TempDir tempDir: Path) {
        val streamId = "user:${UUID.randomUUID()}:2026-01"
        val events = ledgerEvents(streamId, 1L..3L).onEach {
            it.ownerType = "USER"
            it.ownerId = UUID.randomUUID()
        }
        val ledger = mock<AuditLedgerEventRepository>()
        whenever(ledger.findByStreamOrderBySequence(streamId)).thenReturn(events)
        val segments = mock<AuditArchiveSegmentRepository>()
        whenever(segments.insert(any())).thenAnswer { it.getArgument(0) }
        val config = configService(3, tempDir)
        val storage = InMemoryAuditArchiveStorage()
        val signing = LocalAuditArchiveSigningKeyProvider(config)
        assertTrue(AuditArchiver(ledger, segments, storage, signing, config).closeSegmentIfReady(streamId, false))
        val captured = argumentCaptor<AuditArchiveSegment>()
        verify(segments).insert(captured.capture())
        val result = AuditArchiveVerifier(segments, storage, signing).verifySegment(captured.firstValue)
        assertFalse(result.valid, result.note)
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
                actorId = UUID.randomUUID()
                actorRole = "ORG_AUDITOR"
                actorLabel = "Jane Auditor"
                sessionId = "session-$sequence"
                serverTraceId = "trace-$sequence"
                correlationId = "correlation-$sequence"
                causationId = "causation-$sequence"
                organizationId = UUID.randomUUID()
                organizationLabel = "Acme Org"
                targetType = "EXCHANGE"
                targetId = "exchange-$sequence"
                targetLabel = "Q1 Filing"
                reason = "scheduled review"
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
            event.eventHash = LedgerProcessor.computeHash(canonicalJsonFor(event), sequence, prevHash)
            prevHash = event.eventHash
            event
        }
    }

    private fun canonicalJsonFor(event: AuditLedgerEvent): String = LedgerProcessor.canonicalEnvelopeJson(
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

    private fun configService(segmentSize: Int, tempDir: Path): AuditArchiveConfigService
    {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getSegmentSize()).thenReturn(segmentSize)
        whenever(config.getLocalSigningDirectory()).thenReturn(tempDir.resolve("keys").toString())
        return config
    }

    private fun archiveOneSegment(streamId: String, tempDir: Path, storage: InMemoryAuditArchiveStorage): Triple<AuditArchiveSegment, AuditArchiveSegmentRepository, AuditArchiveSigningKeyProvider>
    {
        val events = ledgerEvents(streamId, 1L..3L)
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events)

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(null)
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }

        val config = configService(segmentSize = 3, tempDir = tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)
        archiver.closeSegmentIfReady(streamId, force = false)

        val captor = argumentCaptor<AuditArchiveSegment>()
        verify(segmentRepo).insert(captor.capture())
        return Triple(captor.firstValue, segmentRepo, signingProvider)
    }

    private fun mutateRecord(segment: AuditArchiveSegment, storage: InMemoryAuditArchiveStorage, index: Int = 0, mutate: (ArchivedLedgerEventRecord) -> ArchivedLedgerEventRecord)
    {
        val lines = String(storage.objects[segment.segmentObjectKey]!!, StandardCharsets.UTF_8)
            .lineSequence().filter { it.isNotBlank() }.toMutableList()
        val record = Json.decodeFromString(ArchivedLedgerEventRecord.serializer(), lines[index])
        lines[index] = Json.encodeToString(ArchivedLedgerEventRecord.serializer(), mutate(record))
        storage.objects[segment.segmentObjectKey] = lines.joinToString("\n").toByteArray(StandardCharsets.UTF_8)
    }

    private fun setRawLine(segment: AuditArchiveSegment, storage: InMemoryAuditArchiveStorage, index: Int, raw: String)
    {
        val lines = String(storage.objects[segment.segmentObjectKey]!!, StandardCharsets.UTF_8)
            .lineSequence().filter { it.isNotBlank() }.toMutableList()
        lines[index] = raw
        storage.objects[segment.segmentObjectKey] = lines.joinToString("\n").toByteArray(StandardCharsets.UTF_8)
    }

    @Test
    fun `a genuine segment verifies successfully`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)

        assertTrue(result.valid, result.note)
    }

    @Test
    fun `mutating the archived segment content breaks verification`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )

        mutateRecord(segment, storage) { it.copy(payloadJson = """{"field":"TAMPERED"}""") }

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)

        assertFalse(result.valid)
    }

    @Test
    fun `mutating the archived manifest breaks signature verification`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )

        val tamperedManifest = String(storage.objects[segment.manifestObjectKey]!!, StandardCharsets.UTF_8)
            .replace(segment.merkleRoot, "0".repeat(segment.merkleRoot.length))
        storage.objects[segment.manifestObjectKey] = tamperedManifest.toByteArray(StandardCharsets.UTF_8)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)

        assertFalse(result.valid)
    }

    @Test
    fun `a missing archived object is reported as a verification failure, not an exception`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        storage.objects.remove(segment.segmentObjectKey)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val result = verifier.verifySegment(segment)

        assertFalse(result.valid)
    }

    @Test
    fun `tampering payload while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(payloadJson = """{"field":"TAMPERED"}""") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering reason while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(reason = "TAMPERED reason") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the actor id while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(actorId = UUID.randomUUID().toString()) }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the actor label while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(actorLabel = "TAMPERED Actor") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the organization label while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(organizationLabel = "TAMPERED Org") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the target id while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(targetId = "TAMPERED-target") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the target label while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(targetLabel = "TAMPERED Target") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the occurredAt timestamp while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(occurredAt = "2030-01-01T00:00:00Z") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the recordedAt timestamp while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(recordedAt = "2030-01-01T00:00:00Z") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the outcome while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(outcome = "FAILURE") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the category while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(category = "DOCUMENT") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the schema version while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(schemaVersion = it.schemaVersion + 1) }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the stream id while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage) { it.copy(streamId = "org-2:2026-01") }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `tampering the sequence while keeping the original eventHash is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage, index = 2) { it.copy(streamSequence = 99L) }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `malformed JSON in an archived record is reported as a verification failure, not an exception`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        setRawLine(segment, storage, 0, "{ not valid json")

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `a record missing a required field is reported as a verification failure, not an exception`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )

        val lines = String(storage.objects[segment.segmentObjectKey]!!, StandardCharsets.UTF_8)
            .lineSequence().filter { it.isNotBlank() }.toMutableList()
        val jsonObject = Json.parseToJsonElement(lines[0]).jsonObject
        val withoutPayload = JsonObject(jsonObject.filterKeys { it != "payloadJson" })
        lines[0] = withoutPayload.toString()
        storage.objects[segment.segmentObjectKey] = lines.joinToString("\n").toByteArray(StandardCharsets.UTF_8)

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `a duplicate sequence number across two records is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage, index = 1) { it.copy(streamSequence = it.streamSequence - 1) }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `a broken prevHash link between two records is detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        mutateRecord(segment, storage, index = 1) { it.copy(prevHash = "0".repeat(64)) }

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `out-of-order records within a segment are detected`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )

        val lines = String(storage.objects[segment.segmentObjectKey]!!, StandardCharsets.UTF_8)
            .lineSequence().filter { it.isNotBlank() }.toMutableList()
        val reordered = mutableListOf(lines[1], lines[0], lines[2])
        storage.objects[segment.segmentObjectKey] = reordered.joinToString("\n").toByteArray(StandardCharsets.UTF_8)

        val result = AuditArchiveVerifier(segmentRepo, storage, signingProvider).verifySegment(segment)
        assertFalse(result.valid)
    }

    @Test
    fun `verifyStreamChain detects a deleted segment even though the remaining segments individually still hash correctly`()
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val segmentA = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 1; lastSequence = 3; segmentDigest = "digest-a"; prevSegmentDigest = null
        }
        val segmentB = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 4; lastSequence = 6; segmentDigest = "digest-b"; prevSegmentDigest = "digest-a"
        }
        val segmentC = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 7; lastSequence = 9; segmentDigest = "digest-c"; prevSegmentDigest = "digest-b"
        }

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(listOf(segmentA, segmentC))

        val verifier = AuditArchiveVerifier(segmentRepo, mock(), mock())
        val result = verifier.verifyStreamChain(streamId)

        assertFalse(result.valid)
    }

    @Test
    fun `verifyStreamChain accepts a contiguous, correctly-linked chain`()
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val segmentA = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 1; lastSequence = 3; segmentDigest = "digest-a"; prevSegmentDigest = null
        }
        val segmentB = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 4; lastSequence = 6; segmentDigest = "digest-b"; prevSegmentDigest = "digest-a"
        }

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(listOf(segmentA, segmentB))

        val verifier = AuditArchiveVerifier(segmentRepo, mock(), mock())
        val result = verifier.verifyStreamChain(streamId)

        assertTrue(result.valid, result.note)
        assertEquals(2, result.segmentsChecked)
    }

    private fun archiveSegments(streamId: String, tempDir: Path, storage: InMemoryAuditArchiveStorage, segmentCount: Int): Pair<AuditArchiveSegmentRepository, MutableList<AuditArchiveSegment>>
    {
        val segments = mutableListOf<AuditArchiveSegment>()
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenAnswer { segments.lastOrNull() }
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenAnswer { segments.toList() }
        whenever(segmentRepo.insert(any())).thenAnswer { invocation -> (invocation.getArgument(0) as AuditArchiveSegment).also { segments.add(it) } }

        val config = configService(segmentSize = 3, tempDir = tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)

        var allEvents = listOf<AuditLedgerEvent>()
        for (i in 0 until segmentCount)
        {
            val batchStart = (i * 3 + 1).toLong()
            allEvents = allEvents + ledgerEvents(streamId, batchStart..(batchStart + 2))
            val ledgerRepo = mock<AuditLedgerEventRepository>()
            whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(allEvents)
            AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config).closeSegmentIfReady(streamId, force = false)
        }

        return segmentRepo to segments
    }

    @Test
    fun `checkRangeCoverage reports COMPLETE when the full requested range is archived and verifies`(@TempDir tempDir: Path)
    {
        val storage = InMemoryAuditArchiveStorage()
        val (segment, segmentRepo, signingProvider) = archiveOneSegment(
            "00000000-0000-0000-0000-000000000001:2026-01",
            tempDir,
            storage
        )
        whenever(segmentRepo.findByStreamOrderBySequence("00000000-0000-0000-0000-000000000001:2026-01")).thenReturn(
            listOf(segment)
        )

        val report = AuditArchiveVerifier(
            segmentRepo,
            storage,
            signingProvider
        ).checkRangeCoverage("00000000-0000-0000-0000-000000000001:2026-01", 1L, 3L)

        assertEquals(ArchiveCoverageState.COMPLETE, report.state, report.note)
    }

    @Test
    fun `checkRangeCoverage reports COMPLETE for an empty requested range without consulting archive state`()
    {
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        val report = AuditArchiveVerifier(
            segmentRepo,
            mock(),
            mock()
        ).checkRangeCoverage("00000000-0000-0000-0000-000000000001:2026-01", 5L, 3L)

        assertEquals(ArchiveCoverageState.COMPLETE, report.state)
    }

    @Test
    fun `checkRangeCoverage reports PARTIAL when the stream has no archived segments yet`()
    {
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findByStreamOrderBySequence("00000000-0000-0000-0000-000000000001:2026-01")).thenReturn(
            emptyList()
        )

        val report = AuditArchiveVerifier(
            segmentRepo,
            mock(),
            mock()
        ).checkRangeCoverage("00000000-0000-0000-0000-000000000001:2026-01", 1L, 3L)

        assertEquals(ArchiveCoverageState.PARTIAL, report.state)
    }

    @Test
    fun `checkRangeCoverage reports FAILED when the earliest archived segment starts after the requested range begins`()
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val onlySegment = AuditArchiveSegment().apply {
            this.streamId = streamId; firstSequence = 4; lastSequence = 6; segmentDigest = "digest-a"; prevSegmentDigest = null
            segmentObjectKey = "archive/$streamId/4-6.jsonl"; manifestObjectKey = "archive/$streamId/4-6.manifest.json"
        }
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(listOf(onlySegment))

        val report = AuditArchiveVerifier(segmentRepo, mock(), mock()).checkRangeCoverage(streamId, 1L, 6L)

        assertEquals(ArchiveCoverageState.FAILED, report.state)
    }

    @Test
    fun `checkRangeCoverage reports FAILED when a middle segment is missing from the chain`(@TempDir tempDir: Path)
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val storage = InMemoryAuditArchiveStorage()
        val (_, segments) = archiveSegments(streamId, tempDir, storage, segmentCount = 3)
        segments.removeAt(1)

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(segments)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(configService(3, tempDir))

        val report = AuditArchiveVerifier(segmentRepo, storage, signingProvider).checkRangeCoverage(streamId, 1L, 9L)

        assertEquals(ArchiveCoverageState.FAILED, report.state)
    }

    @Test
    fun `checkRangeCoverage reports PARTIAL when trailing events beyond the last archived segment are not archived yet`(@TempDir tempDir: Path)
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val storage = InMemoryAuditArchiveStorage()
        val (segmentRepo, _) = archiveSegments(streamId, tempDir, storage, segmentCount = 1)

        val signingProvider = LocalAuditArchiveSigningKeyProvider(configService(3, tempDir))
        val report = AuditArchiveVerifier(segmentRepo, storage, signingProvider).checkRangeCoverage(streamId, 1L, 6L)

        assertEquals(ArchiveCoverageState.PARTIAL, report.state)
    }

    @Test
    fun `checkRangeCoverage reports FAILED when an archived segment inside the requested range fails verification`(@TempDir tempDir: Path)
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val storage = InMemoryAuditArchiveStorage()
        val (segmentRepo, segments) = archiveSegments(streamId, tempDir, storage, segmentCount = 2)
        mutateRecord(segments[0], storage) { it.copy(payloadJson = """{"field":"TAMPERED"}""") }

        val signingProvider = LocalAuditArchiveSigningKeyProvider(configService(3, tempDir))
        val report = AuditArchiveVerifier(segmentRepo, storage, signingProvider).checkRangeCoverage(streamId, 1L, 6L)

        assertEquals(ArchiveCoverageState.FAILED, report.state)
    }

    @Test
    fun `checkRangeCoverage reports FAILED when storage holds an archived object no current segment row references`(@TempDir tempDir: Path)
    {
        val streamId = "00000000-0000-0000-0000-000000000001:2026-01"
        val storage = InMemoryAuditArchiveStorage()
        val (_, _, signingProvider) = archiveOneSegment(streamId, tempDir, storage)

        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(emptyList())

        val report = AuditArchiveVerifier(segmentRepo, storage, signingProvider).checkRangeCoverage(streamId, 1L, 3L)

        assertEquals(ArchiveCoverageState.FAILED, report.state)
    }
}
