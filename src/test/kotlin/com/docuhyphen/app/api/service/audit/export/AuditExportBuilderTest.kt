package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportApproval
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.StreamHead
import com.docuhyphen.app.api.repository.audit.AuditArchiveSegmentRepository
import com.docuhyphen.app.api.repository.audit.AuditExportApprovalRepository
import com.docuhyphen.app.api.repository.audit.AuditExportRepository
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.repository.audit.AuditOutboxRepository
import com.docuhyphen.app.api.repository.audit.StreamHeadRepository
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditFailurePolicy
import com.docuhyphen.app.api.service.audit.AuditFailurePolicyResolver
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.AuditSearchProjectionService.AuditAccessActor
import com.docuhyphen.app.api.service.audit.LedgerProcessor
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveObjectAlreadyExistsException
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveObjectNotFoundException
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveVerifier
import com.docuhyphen.app.api.service.audit.archive.AuditArchiver
import com.docuhyphen.app.api.service.audit.archive.LocalAuditArchiveSigningKeyProvider
import com.docuhyphen.app.api.service.audit.archive.MerkleTree
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.config.AuditArchiveConfigService
import com.docuhyphen.app.api.service.config.AuditExportConfigService
import com.docuhyphen.app.api.service.organization.OrganizationService
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** In-memory [AuditArchiveStorage] test double, same contract as [com.docuhyphen.app.api.service.audit.archive.InMemoryAuditArchiveStorage]. */
private class InMemoryStorage : AuditArchiveStorage
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

/**
 * Verifies [AuditExportBuilder] ("A range does not verify merely because inner events
 * link; boundary checkpoints are required"):
 *  - a genuinely archived, unbroken stream produces a `READY` export whose bundle round-trips
 *    (manifest hash, detached signature, per-stream integrity report all present and consistent).
 *  - a stream whose segment chain has been broken (a deleted middle segment) fails
 *    [AuditExportBuilder.build] with [AuditExportIntegrityFailedException] and never reaches
 *    `READY` - even though the two remaining segments each still individually hash correctly.
 */
class AuditExportBuilderTest
{
    /**
     * Builds [sequences] as a real, hash-chained run of ledger events for [organizationId], using
     * the same canonical serializer production code uses ([LedgerProcessor.canonicalEnvelopeJson] /
     * [LedgerProcessor.computeHash]) instead of an arbitrary placeholder hash string, so segments
     * built from these events pass [AuditArchiveVerifier]'s per-record hash recomputation exactly
     * as genuinely-appended ledger events would.
     */
    private fun ledgerEvents(
        streamId: String,
        sequences: LongRange,
        organizationId: UUID,
        actorLabelFor: (Long) -> String? = { null },
        targetLabelFor: (Long) -> String? = { null },
    ): List<AuditLedgerEvent>
    {
        var prevHash: String? = null
        return sequences.map { sequence ->
            val event = AuditLedgerEvent().apply {
                eventId = UUID.randomUUID()
                eventTypeKey = "exchange.lifecycle.rescinded"
                category = "EXCHANGE"
                outcome = "SUCCESS"
                schemaVersion = 4
                occurredAt = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z").plusSeconds(sequence))
                recordedAt = Timestamp.from(Instant.parse("2026-01-15T10:00:00Z"))
                this.streamId = streamId
                streamSequence = sequence
                actorKind = "HUMAN"
                this.organizationId = organizationId
                actorLabel = actorLabelFor(sequence)
                targetLabel = targetLabelFor(sequence)
                payloadJson = "{}"
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

    private fun archiveConfig(segmentSize: Int, tempDir: Path): AuditArchiveConfigService
    {
        val config = mock<AuditArchiveConfigService>()
        whenever(config.getSegmentSize()).thenReturn(segmentSize)
        whenever(config.getLocalSigningDirectory()).thenReturn(tempDir.resolve("keys").toString())
        return config
    }

    /** Archives every 3 events into their own segment (2 segments for 6 events), returning the closed segments in order. */
    private fun archiveSegments(
        streamId: String,
        events: List<AuditLedgerEvent>,
        segmentSize: Int,
        tempDir: Path,
        storage: AuditArchiveStorage,
    ): Pair<List<AuditArchiveSegment>, AuditArchiveSegmentRepository>
    {
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.insert(any())).thenAnswer { it.getArgument(0) }
        val config = archiveConfig(segmentSize, tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val ledgerRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events)
        val archiver = AuditArchiver(ledgerRepo, segmentRepo, storage, signingProvider, config)

        val closed = mutableListOf<AuditArchiveSegment>()
        var latest: AuditArchiveSegment? = null
        while (true)
        {
            whenever(segmentRepo.findLatestByStream(streamId)).thenReturn(latest)
            val didClose = archiver.closeSegmentIfReady(streamId, force = false)
            if (!didClose) break

            val captor = argumentCaptor<AuditArchiveSegment>()
            verify(segmentRepo, org.mockito.kotlin.times(closed.size + 1)).insert(captor.capture())
            latest = captor.lastValue
            closed += latest
        }
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(closed)
        return closed to segmentRepo
    }

    private fun builder(
        streamId: String,
        organizationId: UUID,
        events: List<AuditLedgerEvent>,
        segmentRepo: AuditArchiveSegmentRepository,
        storage: AuditArchiveStorage,
        tempDir: Path,
        exportRepo: AuditExportRepository,
    ): AuditExportBuilder
    {
        val ledgerEventRepo = mock<AuditLedgerEventRepository>()
        whenever(
            ledgerEventRepo.findDistinctStreamIdsForExport(any(), any(), any(), any()),
        ).thenReturn(listOf(streamId))
        whenever(
            ledgerEventRepo.findForExport(any(), any(), any(), any(), any()),
        ).thenReturn(events)
        whenever(ledgerEventRepo.findLatestByStream(streamId)).thenReturn(events.last())

        val config = archiveConfig(3, tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val auditRecorder = mock<AuditRecorder>()
        val integrityService = AuditIntegrityService(ledgerEventRepo, segmentRepo, verifier, auditRecorder, mock())
        val auditArchiver = AuditArchiver(ledgerEventRepo, segmentRepo, storage, signingProvider, config)

        whenever(exportRepo.update(any())).thenAnswer { it.getArgument(0) }

        return AuditExportBuilder(ledgerEventRepo, integrityService, storage, signingProvider, auditArchiver)
    }

    private fun exportFor(organizationId: UUID): AuditExport = AuditExport().apply {
        id = UUID.randomUUID()
        this.organizationId = organizationId
        requestedByUserId = UUID.randomUUID()
        categoriesCsv = "EXCHANGE"
        occurredAfter = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        occurredBefore = Timestamp.from(Instant.parse("2026-02-01T00:00:00Z"))
        purpose = "regulator inquiry"
        status = AuditExportStatus.BUILDING
    }

    @Test
    fun `a genuinely unbroken stream produces a READY export with a self-verifiable bundle`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = ledgerEvents(streamId, 1L..6L, organizationId)
        val storage = InMemoryStorage()
        val (segments, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        assertEquals(2, segments.size)

        val exportRepo = mock<AuditExportRepository>()
        val builder = builder(streamId, organizationId, events, segmentRepo, storage, tempDir, exportRepo)
        val export = exportFor(organizationId)

        val result = builder.build(export)

        assertEquals(6, result.eventCount)
        assertEquals(AuditExportStatus.BUILDING, export.status)
        assertTrue(storage.objectExists(result.bundleObjectKey))

        val bundleBytes = storage.getObject(result.bundleObjectKey)
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bundleBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null)
            {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.containsKey("manifest.json"))
        assertTrue(entries.containsKey("events.jsonl"))
        assertTrue(entries.containsKey("events.csv"))
        assertTrue(entries.containsKey("integrity.json"))
        assertTrue(entries.containsKey("signature.json"))
        assertTrue(entries.containsKey("README.txt"))
        assertTrue(entries.containsKey("verify.mjs"))

        // bundleDigest covers the whole immutable ZIP object (what is actually re-fetched and
        // re-hashed at download time), not merely manifest.json's own bytes - a change to any
        // entry, not only the manifest, must be detectable by re-hashing the stored object alone.
        assertEquals(MerkleTree.sha256Hex(bundleBytes), result.bundleDigest)

        val eventLines = String(entries.getValue("events.jsonl"), StandardCharsets.UTF_8).lines().filter { it.isNotBlank() }
        assertEquals(6, eventLines.size)

        val signedManifest = Json.decodeFromString(
            SignedAuditExportManifest.serializer(),
            String(entries.getValue("signature.json"), StandardCharsets.UTF_8),
        )
        val bundlePath = tempDir.resolve("bundle.zip")
        val verifierPath = tempDir.resolve("verify.mjs")
        val trustedKeyPath = tempDir.resolve("trusted-public-key.pem")
        java.nio.file.Files.write(bundlePath, bundleBytes)
        java.nio.file.Files.write(verifierPath, entries.getValue("verify.mjs"))
        java.nio.file.Files.writeString(trustedKeyPath, signedManifest.publicKeyPem)
        val verifierProcess = ProcessBuilder(
            "node",
            verifierPath.toString(),
            bundlePath.toString(),
            trustedKeyPath.toString(),
        ).redirectErrorStream(true).start()
        val verifierOutput = verifierProcess.inputStream.bufferedReader().readText()
        assertEquals(0, verifierProcess.waitFor(), verifierOutput)
    }

    @Test
    fun `offline verifier rejects missing duplicate unexpected tampered and untrusted bundles`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = ledgerEvents(streamId, 1L..6L, organizationId)
        val storage = InMemoryStorage()
        val (_, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        val result = builder(
            streamId,
            organizationId,
            events,
            segmentRepo,
            storage,
            tempDir,
            mock(),
        ).build(exportFor(organizationId))
        val validBundle = storage.getObject(result.bundleObjectKey)
        val entries = readZipEntries(validBundle)
        val signedManifest = Json.decodeFromString(
            SignedAuditExportManifest.serializer(),
            String(entries.getValue("signature.json"), StandardCharsets.UTF_8),
        )
        val verifierPath = tempDir.resolve("verify.mjs")
        val trustedKeyPath = tempDir.resolve("trusted-public-key.pem")
        Files.write(verifierPath, entries.getValue("verify.mjs"))
        Files.writeString(trustedKeyPath, signedManifest.publicKeyPem)

        val orderedEntries = entries.entries.map { it.key to it.value }
        val invalidBundles = listOf(
            "missing" to zipEntries(orderedEntries.filterNot { it.first == "events.csv" }),
            "duplicate" to renameCentralDirectoryEntry(validBundle, "events.csv", "README.txt"),
            "unexpected" to zipEntries(orderedEntries + ("unexpected.txt" to "unexpected".toByteArray())),
            "tampered" to zipEntries(
                orderedEntries.map { (name, bytes) ->
                    if (name == "events.jsonl") name to (bytes + "tampered".toByteArray()) else name to bytes
                },
            ),
        )
        invalidBundles.forEach { (name, bundle) ->
            val (exitCode, output) = runOfflineVerifier(
                verifierPath,
                tempDir.resolve("$name.zip"),
                trustedKeyPath,
                bundle,
            )
            assertEquals(1, exitCode, output)
        }

        val replacementProvider = LocalAuditArchiveSigningKeyProvider(
            archiveConfig(3, tempDir.resolve("replacement")),
        )
        val replacementKeyPath = tempDir.resolve("replacement-public-key.pem")
        Files.writeString(replacementKeyPath, replacementProvider.activePublicKeyPem())
        val (replacementExitCode, replacementOutput) = runOfflineVerifier(
            verifierPath,
            tempDir.resolve("valid-with-replacement-key.zip"),
            replacementKeyPath,
            validBundle,
        )
        assertEquals(1, replacementExitCode, replacementOutput)
    }

    @Test
    fun `evidence path runs from capture through approved download and independent bundle verification`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val requesterId = UUID.randomUUID()
        val approverId = UUID.randomUUID()
        val outboxEntries = mutableListOf<AuditOutboxEntry>()
        val outboxRepository = mock<AuditOutboxRepository>()
        whenever(outboxRepository.findByIdempotencyKey(any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            outboxEntries.firstOrNull { it.idempotencyKey == key }
        }
        whenever(outboxRepository.insert(any())).thenAnswer { invocation ->
            invocation.getArgument<AuditOutboxEntry>(0).also { entry ->
                entry.id = UUID.randomUUID()
                outboxEntries += entry
            }
        }
        whenever(outboxRepository.findOldestUnledgeredByRecordedAt(any())).thenAnswer { outboxEntries.toList() }

        val tokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = AppUser().apply { id = requesterId } }
            serverTraceId = "evidence-path-trace"
            correlationId = "evidence-path-correlation"
        }
        val failurePolicyResolver = mock<AuditFailurePolicyResolver>()
        whenever(failurePolicyResolver.resolve(any())).thenReturn(AuditFailurePolicy.FAIL_CLOSED)
        val recorder = AuditRecorder(outboxRepository, tokenContext, failurePolicyResolver)

        repeat(3) { index ->
            recorder.record(
                AuditEventDraft(
                    owner = AuditOwnerScope.Organization(organizationId),
                    eventTypeKey = AuditEventType.EXCHANGE_RESCINDED.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = requesterId,
                    targetType = "EXCHANGE",
                    targetId = UUID.randomUUID().toString(),
                    payload = mapOf(
                        "previousStatus" to "INITIATED",
                        "newStatus" to "RESCINDED",
                    ),
                    idempotencyKey = "evidence-path-$index",
                ),
            )
        }
        assertEquals(3, outboxEntries.size)

        val ledgerEvents = mutableListOf<AuditLedgerEvent>()
        val ledgerRepository = mock<AuditLedgerEventRepository>()
        whenever(ledgerRepository.existsByEventId(any())).thenAnswer { invocation ->
            val eventId = invocation.getArgument<UUID>(0)
            ledgerEvents.any { it.eventId == eventId }
        }
        whenever(ledgerRepository.insert(any())).thenAnswer { invocation ->
            invocation.getArgument<AuditLedgerEvent>(0).also(ledgerEvents::add)
        }
        val streamHeads = mutableMapOf<String, StreamHead>()
        val streamHeadRepository = mock<StreamHeadRepository>()
        whenever(streamHeadRepository.lockOrCreate(any())).thenAnswer { invocation ->
            val streamId = invocation.getArgument<String>(0)
            streamHeads.getOrPut(streamId) { StreamHead().apply { this.streamId = streamId } }
        }
        whenever(streamHeadRepository.advance(any())).thenAnswer { it.getArgument(0) }
        val drainResult = LedgerProcessor(outboxRepository, ledgerRepository, streamHeadRepository).drain()
        assertEquals(3, drainResult.appended)
        assertEquals(listOf(1L, 2L, 3L), ledgerEvents.map { it.streamSequence })

        val streamId = ledgerEvents.first().streamId
        val storage = InMemoryStorage()
        val (segments, segmentRepository) = archiveSegments(
            streamId,
            ledgerEvents,
            segmentSize = 3,
            tempDir = tempDir,
            storage = storage,
        )
        assertEquals(1, segments.size)

        val exportRepository = mock<AuditExportRepository>()
        var storedExport: AuditExport? = null
        whenever(exportRepository.insert(any())).thenAnswer { invocation ->
            invocation.getArgument<AuditExport>(0).also { export ->
                export.id = UUID.randomUUID()
                storedExport = export
            }
        }
        whenever(exportRepository.findById(any())).thenAnswer { storedExport }
        whenever(exportRepository.findByIdForUpdate(any())).thenAnswer { storedExport }
        whenever(exportRepository.update(any())).thenAnswer { invocation ->
            invocation.getArgument<AuditExport>(0).also { storedExport = it }
        }
        val exportBuilder = builder(
            streamId,
            organizationId,
            ledgerEvents,
            segmentRepository,
            storage,
            tempDir,
            exportRepository,
        )

        val approvals = mutableListOf<AuditExportApproval>()
        val approvalRepository = mock<AuditExportApprovalRepository>()
        whenever(approvalRepository.hasApprovalFrom(any(), any())).thenAnswer { invocation ->
            val exportId = invocation.getArgument<UUID>(0)
            val userId = invocation.getArgument<UUID>(1)
            approvals.any { it.exportId == exportId && it.approvedByUserId == userId }
        }
        whenever(approvalRepository.insert(any())).thenAnswer { invocation ->
            invocation.getArgument<AuditExportApproval>(0).also(approvals::add)
        }
        whenever(approvalRepository.countByExport(any())).thenAnswer { invocation ->
            val exportId = invocation.getArgument<UUID>(0)
            approvals.count { it.exportId == exportId }.toLong()
        }
        val exportConfig = mock<AuditExportConfigService>()
        whenever(exportConfig.isDualControlRequired()).thenReturn(true)
        whenever(exportConfig.getRequiredApprovals()).thenReturn(1)
        whenever(exportConfig.getDefaultDownloadLimit()).thenReturn(2)
        whenever(exportConfig.getDownloadLifetimeHours()).thenReturn(72)
        whenever(exportConfig.getMaxRangeDays()).thenReturn(31)
        val appUserService = mock<AppUserService>()
        whenever(appUserService.getById(any())).thenReturn(AppUser())
        val exportService = AuditExportService(
            exportRepository,
            approvalRepository,
            exportBuilder,
            storage,
            exportConfig,
            mock<OrganizationService>(),
            appUserService,
            mock(),
            recorder,
            mock(),
            mock(),
        )
        val requester = AuditAccessActor(
            principal = PrincipalRef.user(requesterId),
            context = AuthorizationContext(mfaSatisfied = true),
            capabilities = setOf(
                Capability.ORG_POLICY_MANAGE,
                Capability.ORG_AUDIT_EXPORT,
                Capability.ORG_AUDIT_VIEW_SENSITIVE,
            ),
        )
        val now = Instant.now()
        val export = exportService.requestExport(
            AuditExportService.ExportRequest(
                organizationId = organizationId,
                categories = setOf(AuditCategory.EXCHANGE),
                occurredAfter = now.minusSeconds(3600),
                occurredBefore = now.plusSeconds(3600),
                purpose = "end-to-end evidence path",
            ),
            requester,
        )
        assertEquals(AuditExportStatus.APPROVAL_PENDING, export.status)

        exportService.approveExport(export.id, approverId, "approved for evidence test")
        assertEquals(AuditExportStatus.BUILDING, export.status)
        exportService.processBuilding(export)
        assertEquals(AuditExportStatus.READY, export.status)

        val downloadedBundle = exportService.downloadBundle(export.id, requester)
        assertEquals(storage.getObject(export.bundleObjectKey!!).toList(), downloadedBundle.toList())
        assertEquals(MerkleTree.sha256Hex(downloadedBundle), export.bundleDigest)

        val entries = readZipEntries(downloadedBundle)
        val manifestBytes = entries.getValue("manifest.json")
        val signedManifest = Json.decodeFromString(
            SignedAuditExportManifest.serializer(),
            String(entries.getValue("signature.json"), StandardCharsets.UTF_8),
        )
        val verifierKeyProvider = LocalAuditArchiveSigningKeyProvider(archiveConfig(3, tempDir))
        assertTrue(
            verifierKeyProvider.verify(
                manifestBytes,
                Base64.getDecoder().decode(signedManifest.signatureBase64),
                export.signingKeyId!!,
            ),
        )
        val manifest = Json.decodeFromString(
            AuditExportManifestEnvelope.serializer(),
            String(manifestBytes, StandardCharsets.UTF_8),
        )
        manifest.entryDigests.forEach { digest ->
            val entryBytes = entries.getValue(digest.name)
            assertEquals(digest.byteLength, entryBytes.size.toLong())
            assertEquals(digest.sha256Hex, MerkleTree.sha256Hex(entryBytes))
        }
        assertEquals(3, manifest.eventCount)
    }

    /**
     * The signed manifest's `entryDigests` must exactly content-address every protected entry
     * other than itself and `signature.json`, and `eventsMerkleRoot` must be an independent,
     * per-event cross-check derived from the exported events' own hashes - not merely delegate to
     * `events.jsonl`'s own digest. Any one-byte change to a protected entry, or to the event list
     * used to compute the root, must therefore be individually detectable.
     */
    @Test
    fun `the signed manifest's entryDigests and eventsMerkleRoot exactly bind every protected bundle entry`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = ledgerEvents(streamId, 1L..6L, organizationId)
        val storage = InMemoryStorage()
        val (segments, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        assertEquals(2, segments.size)

        val exportRepo = mock<AuditExportRepository>()
        val builder = builder(streamId, organizationId, events, segmentRepo, storage, tempDir, exportRepo)
        val export = exportFor(organizationId)
        val result = builder.build(export)

        val entries = readZipEntries(storage.getObject(result.bundleObjectKey))
        val manifest = kotlinx.serialization.json.Json.decodeFromString(
            AuditExportManifestEnvelope.serializer(),
            String(entries.getValue("manifest.json"), StandardCharsets.UTF_8),
        )

        val digestedNames = setOf("events.jsonl", "events.csv", "integrity.json", "README.txt", "verify.mjs")
        assertEquals(digestedNames, manifest.entryDigests.map { it.name }.toSet())
        for (entryDigest in manifest.entryDigests)
        {
            val actualBytes = entries.getValue(entryDigest.name)
            assertEquals(actualBytes.size.toLong(), entryDigest.byteLength)
            assertEquals(MerkleTree.sha256Hex(actualBytes), entryDigest.sha256Hex)
        }

        val expectedRoot = MerkleTree.computeRoot(events.map { it.eventHash })
        assertEquals(expectedRoot, manifest.eventsMerkleRoot)
    }

    /**
     * Modifying any protected entry after signing must be detectable from its declared digest.
     * after the bundle has already been built and signed, must be detectable by recomputing its
     * digest against the value recorded in the signed manifest.
     * `manifest.json` itself is covered separately below because tampering it invalidates the
     * signature rather than an entry digest.
     */
    @Test
    fun `tampering events, integrity, or README entries after the bundle is built is detected by recomputing digests against the signed manifest`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = ledgerEvents(streamId, 1L..6L, organizationId)
        val storage = InMemoryStorage()
        val (segments, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        assertEquals(2, segments.size)

        val exportRepo = mock<AuditExportRepository>()
        val builder = builder(streamId, organizationId, events, segmentRepo, storage, tempDir, exportRepo)
        val export = exportFor(organizationId)
        val result = builder.build(export)

        val entries = readZipEntries(storage.getObject(result.bundleObjectKey))
        val manifest = kotlinx.serialization.json.Json.decodeFromString(
            AuditExportManifestEnvelope.serializer(),
            String(entries.getValue("manifest.json"), StandardCharsets.UTF_8),
        )
        val declaredDigests = manifest.entryDigests.associateBy { it.name }

        for (name in listOf("events.jsonl", "events.csv", "integrity.json", "README.txt", "verify.mjs"))
        {
            val tamperedBytes = entries.getValue(name) + "tampered".toByteArray(StandardCharsets.UTF_8)
            assertTrue(
                MerkleTree.sha256Hex(tamperedBytes) != declaredDigests.getValue(name).sha256Hex,
                "tampering '$name' must change its digest away from the value the signed manifest recorded",
            )
        }
    }

    @Test
    fun `tampering manifest json after signing is detected because the signature no longer verifies`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = ledgerEvents(streamId, 1L..6L, organizationId)
        val storage = InMemoryStorage()
        val (segments, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        assertEquals(2, segments.size)

        val config = archiveConfig(3, tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val exportRepo = mock<AuditExportRepository>()
        val ledgerEventRepo = mock<AuditLedgerEventRepository>()
        whenever(ledgerEventRepo.findDistinctStreamIdsForExport(any(), any(), any(), any())).thenReturn(listOf(streamId))
        whenever(ledgerEventRepo.findForExport(any(), any(), any(), any(), any())).thenReturn(events)
        whenever(ledgerEventRepo.findLatestByStream(streamId)).thenReturn(events.last())
        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val auditRecorder = mock<AuditRecorder>()
        val integrityService = AuditIntegrityService(ledgerEventRepo, segmentRepo, verifier, auditRecorder, mock())
        val auditArchiver = AuditArchiver(ledgerEventRepo, segmentRepo, storage, signingProvider, config)
        whenever(exportRepo.update(any())).thenAnswer { it.getArgument(0) }
        val builder = AuditExportBuilder(ledgerEventRepo, integrityService, storage, signingProvider, auditArchiver)
        val export = exportFor(organizationId)
        val result = builder.build(export)

        val entries = readZipEntries(storage.getObject(result.bundleObjectKey))
        val signed = kotlinx.serialization.json.Json.decodeFromString(
            SignedAuditExportManifest.serializer(),
            String(entries.getValue("signature.json"), StandardCharsets.UTF_8),
        )
        val tamperedManifestBytes = (String(entries.getValue("manifest.json"), StandardCharsets.UTF_8) + " ")
            .toByteArray(StandardCharsets.UTF_8)

        val signatureStillValid = signingProvider.verify(
            tamperedManifestBytes,
            java.util.Base64.getDecoder().decode(signed.signatureBase64),
            signed.signatureAlgorithm.let { signingProvider.keyId() },
        )
        assertTrue(!signatureStillValid, "a one-byte change to manifest.json must invalidate the detached signature")
    }

    /** Reads every entry of an in-memory ZIP into a name-to-bytes map for bundle assertions. */
    private fun readZipEntries(bundleBytes: ByteArray): Map<String, ByteArray>
    {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(bundleBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null)
            {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun zipEntries(entries: List<Pair<String, ByteArray>>): ByteArray
    {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun renameCentralDirectoryEntry(bundle: ByteArray, from: String, to: String): ByteArray
    {
        val fromBytes = from.toByteArray(StandardCharsets.UTF_8)
        val toBytes = to.toByteArray(StandardCharsets.UTF_8)
        require(fromBytes.size == toBytes.size)
        val renamed = bundle.copyOf()
        for (offset in 0..renamed.size - 46)
        {
            val isCentralHeader = renamed[offset] == 0x50.toByte() &&
                renamed[offset + 1] == 0x4b.toByte() &&
                renamed[offset + 2] == 0x01.toByte() &&
                renamed[offset + 3] == 0x02.toByte()
            if (!isCentralHeader)
            {
                continue
            }
            val nameLength = (renamed[offset + 28].toInt() and 0xff) or
                ((renamed[offset + 29].toInt() and 0xff) shl 8)
            val nameOffset = offset + 46
            if (nameLength == fromBytes.size && renamed.copyOfRange(nameOffset, nameOffset + nameLength).contentEquals(fromBytes))
            {
                toBytes.copyInto(renamed, nameOffset)
                return renamed
            }
        }
        throw IllegalArgumentException("ZIP central-directory entry not found: $from")
    }

    private fun runOfflineVerifier(
        verifierPath: Path,
        bundlePath: Path,
        trustedKeyPath: Path,
        bundle: ByteArray,
    ): Pair<Int, String>
    {
        Files.write(bundlePath, bundle)
        val process = ProcessBuilder(
            "node",
            verifierPath.toString(),
            bundlePath.toString(),
            trustedKeyPath.toString(),
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return process.waitFor() to output
    }

    @Test
    fun `a broken segment chain fails the build even though the remaining segments individually still hash correctly`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = ledgerEvents(streamId, 1L..9L, organizationId)
        val storage = InMemoryStorage()
        val (segments, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        assertEquals(3, segments.size)

        // Simulate a deleted middle segment: the remaining two segments both still verify their
        // own content/signature in isolation, but the chain from segment 1 to segment 3 is broken.
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenReturn(listOf(segments[0], segments[2]))

        val exportRepo = mock<AuditExportRepository>()
        val builder = builder(streamId, organizationId, events, segmentRepo, storage, tempDir, exportRepo)
        val export = exportFor(organizationId)

        assertThrows(AuditExportIntegrityFailedException::class.java) {
            builder.build(export)
        }
        assertEquals(AuditExportStatus.BUILDING, export.status)
        assertTrue(!storage.objectExists("exports/${export.id}/bundle.zip"))
    }

    @Test
    fun `a small unarchived trailing range is force-closed so the export still builds`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = ledgerEvents(streamId, 1L..4L, organizationId)

        val storage = InMemoryStorage()
        val segments = mutableListOf<AuditArchiveSegment>()
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenAnswer { segments.lastOrNull() }
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenAnswer { segments.toList() }
        whenever(segmentRepo.insert(any())).thenAnswer { invocation -> (invocation.getArgument(0) as AuditArchiveSegment).also { segments.add(it) } }

        val config = archiveConfig(3, tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val closingLedgerRepo = mock<AuditLedgerEventRepository>()
        whenever(closingLedgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events)
        AuditArchiver(closingLedgerRepo, segmentRepo, storage, signingProvider, config).closeSegmentIfReady(streamId, force = false)
        assertEquals(1, segments.size, "only the first 3 of 4 events should have closed on their own")

        val exportLedgerRepo = mock<AuditLedgerEventRepository>()
        whenever(exportLedgerRepo.findDistinctStreamIdsForExport(any(), any(), any(), any())).thenReturn(listOf(streamId))
        whenever(exportLedgerRepo.findForExport(any(), any(), any(), any(), any())).thenReturn(events)
        whenever(exportLedgerRepo.findLatestByStream(streamId)).thenReturn(events.last())
        whenever(exportLedgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events)

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val auditRecorder = mock<AuditRecorder>()
        val integrityService = AuditIntegrityService(exportLedgerRepo, segmentRepo, verifier, auditRecorder, mock())
        val auditArchiver = AuditArchiver(exportLedgerRepo, segmentRepo, storage, signingProvider, config)
        val exportRepo = mock<AuditExportRepository>()
        whenever(exportRepo.update(any())).thenAnswer { it.getArgument(0) }

        val exportBuilder = AuditExportBuilder(exportLedgerRepo, integrityService, storage, signingProvider, auditArchiver)
        val export = exportFor(organizationId)

        val result = exportBuilder.build(export)

        assertEquals(4, result.eventCount)
        assertEquals(AuditExportStatus.BUILDING, export.status)
        assertEquals(2, segments.size, "the pending 4th event should have been force-closed into its own segment before the coverage gate ran")
    }

    @Test
    fun `an export range with a trailing gap that force-close cannot cover is rejected`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val events = ledgerEvents(streamId, 1L..6L, organizationId)

        val storage = InMemoryStorage()
        val segments = mutableListOf<AuditArchiveSegment>()
        val segmentRepo = mock<AuditArchiveSegmentRepository>()
        whenever(segmentRepo.findLatestByStream(streamId)).thenAnswer { segments.lastOrNull() }
        whenever(segmentRepo.findByStreamOrderBySequence(streamId)).thenAnswer { segments.toList() }
        whenever(segmentRepo.insert(any())).thenAnswer { invocation -> (invocation.getArgument(0) as AuditArchiveSegment).also { segments.add(it) } }

        val config = archiveConfig(3, tempDir)
        val signingProvider = LocalAuditArchiveSigningKeyProvider(config)
        val closingLedgerRepo = mock<AuditLedgerEventRepository>()
        whenever(closingLedgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events.take(3))
        AuditArchiver(closingLedgerRepo, segmentRepo, storage, signingProvider, config).closeSegmentIfReady(streamId, force = false)
        assertEquals(1, segments.size)

        // The export's own query sees all 6 events, but the archiver's view of the stream (used
        // to force-close a trailing range before the coverage gate runs) still only reports the
        // first 3 - events 4..6 cannot be closed into a checkpoint by this build attempt.
        val exportLedgerRepo = mock<AuditLedgerEventRepository>()
        whenever(exportLedgerRepo.findDistinctStreamIdsForExport(any(), any(), any(), any())).thenReturn(listOf(streamId))
        whenever(exportLedgerRepo.findForExport(any(), any(), any(), any(), any())).thenReturn(events)
        whenever(exportLedgerRepo.findLatestByStream(streamId)).thenReturn(events.last())
        whenever(exportLedgerRepo.findByStreamOrderBySequence(streamId)).thenReturn(events.take(3))

        val verifier = AuditArchiveVerifier(segmentRepo, storage, signingProvider)
        val auditRecorder = mock<AuditRecorder>()
        val integrityService = AuditIntegrityService(exportLedgerRepo, segmentRepo, verifier, auditRecorder, mock())
        val auditArchiver = AuditArchiver(exportLedgerRepo, segmentRepo, storage, signingProvider, config)
        val exportRepo = mock<AuditExportRepository>()
        whenever(exportRepo.update(any())).thenAnswer { it.getArgument(0) }

        val exportBuilder = AuditExportBuilder(exportLedgerRepo, integrityService, storage, signingProvider, auditArchiver)
        val export = exportFor(organizationId)

        assertThrows(AuditExportIntegrityFailedException::class.java) {
            exportBuilder.build(export)
        }
        assertEquals(AuditExportStatus.BUILDING, export.status)
        assertTrue(!storage.objectExists("exports/${export.id}/bundle.zip"))
    }

    // --- events.csv formula-injection neutralization -------------------------------------------

    /**
     * A malicious actor or organization label beginning with `=`, `+`, `-`, or `@` would be
     * interpreted as a formula by common spreadsheet tools if exported verbatim. `events.csv` must
     * neutralize this; `events.jsonl` (the canonical evidence) must be completely unaffected.
     */
    @Test
    fun `events csv neutralizes formula-injection prefixes while events jsonl remains unaltered`(@TempDir tempDir: Path)
    {
        val organizationId = UUID.randomUUID()
        val streamId = "$organizationId:2026-01"
        val actorLabels = mapOf(1L to "=1+1", 2L to "+cmd|' /c calc'!A0", 3L to "-2+3")
        val events = ledgerEvents(
            streamId, 1L..3L, organizationId,
            actorLabelFor = { sequence -> actorLabels.getValue(sequence) },
            targetLabelFor = { "@SUM(A1:A2)" },
        )
        val storage = InMemoryStorage()
        val (segments, segmentRepo) = archiveSegments(streamId, events, segmentSize = 3, tempDir = tempDir, storage = storage)
        assertEquals(1, segments.size)

        val exportRepo = mock<AuditExportRepository>()
        val builder = builder(streamId, organizationId, events, segmentRepo, storage, tempDir, exportRepo)
        val export = exportFor(organizationId)
        val result = builder.build(export)

        val entries = readZipEntries(storage.getObject(result.bundleObjectKey))
        val csv = String(entries.getValue("events.csv"), StandardCharsets.UTF_8)
        val jsonl = String(entries.getValue("events.jsonl"), StandardCharsets.UTF_8)

        assertTrue(csv.contains("'=1+1"), "a leading apostrophe must neutralize a formula-triggering value in events.csv")
        assertTrue(csv.contains("'+cmd"))
        assertTrue(csv.contains("'-2+3"))
        assertTrue(csv.contains("'@SUM(A1:A2)"))

        assertTrue(jsonl.contains("\"=1+1\""), "events.jsonl must keep the original, unaltered actorLabel value")
        assertTrue(jsonl.contains("\"@SUM(A1:A2)\""), "events.jsonl must keep the original, unaltered targetLabel value")
    }

}
