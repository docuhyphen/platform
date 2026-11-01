package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.audit.archive.ArchiveCoverageState
import com.docuhyphen.app.api.model.audit.export.AuditExportBundleResult
import com.docuhyphen.app.api.model.audit.export.AuditExportManifestEnvelope
import com.docuhyphen.app.api.model.audit.export.ExportStreamIntegrityRecord
import com.docuhyphen.app.api.model.audit.export.ExportedLedgerEventRecord
import com.docuhyphen.app.api.model.audit.export.ManifestEntryDigest
import com.docuhyphen.app.api.model.audit.export.SignedAuditExportManifest
import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.audit.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveSigningKeyProvider
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.archive.AuditArchiver
import com.docuhyphen.app.api.service.audit.archive.MerkleTree
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AuditExportIntegrityFailedException(message: String) : RuntimeException(message)

@ApplicationScoped
class AuditExportBuilder @Inject constructor(
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditIntegrityService: AuditIntegrityService,
    private val archiveStorage: AuditArchiveStorage,
    private val signingKeyProvider: AuditArchiveSigningKeyProvider,
    private val auditArchiver: AuditArchiver,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditExportBuilder::class.java)
        private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@')
        private const val MAX_BUNDLE_CONTENT_BYTES = 256L * 1024L * 1024L
        private const val FIDELITY_NOTE =
            "events.csv flattens each event to scalar columns plus a single JSON-encoded payload " +
                    "column; it is a convenience projection, not canonical evidence. events.jsonl is " +
                    "the byte-for-byte record of every exported event and is what the manifest hash " +
                    "and signature cover."
        private const val README_TEXT =
            "DocuHyphen audit evidence export\n" +
                    "=================================\n\n" +
                    "Files in this bundle:\n" +
                    "  manifest.json   - canonical, signed description of this export (range, event\n" +
                    "                    count, schema versions, signing key id, per-entry digests,\n" +
                    "                    events Merkle root).\n" +
                    "  events.jsonl    - every exported ledger event, one JSON object per line, in\n" +
                    "                    (streamId, streamSequence) order. This is the canonical\n" +
                    "                    evidence; events.csv is a convenience projection only.\n" +
                    "  events.csv      - flattened projection of the same events for spreadsheet\n" +
                    "                    tools. Values that could be interpreted as a spreadsheet\n" +
                    "                    formula are neutralized with a leading apostrophe; events.jsonl\n" +
                    "                    is unaltered. See the fidelityNote in manifest.json.\n" +
                    "  integrity.json  - per-stream boundary-checkpoint (segment chain) and segment\n" +
                    "                    verification results collected before this bundle was built.\n" +
                    "  signature.json  - detached SHA256withRSA signature over the exact manifest.json\n" +
                    "                    bytes, plus a convenience copy of the signing public key. Do not\n" +
                    "                    trust that embedded key by itself - obtain the trusted key for\n" +
                    "                    manifest.json's signingKeyId through an independent channel.\n" +
                    "  verify.mjs      - dependency-free Node.js verifier for entry digests, the\n" +
                    "                    trusted-key signature, event hashes, chains, and Merkle root.\n" +
                    "Verify manifest.json and signature.json using a trusted public key obtained\n" +
                    "through an independent channel. Recompute each declared entry digest and the\n" +
                    "events Merkle root before relying on the evidence.\n"
    }

    fun build(export: AuditExport): AuditExportBundleResult
    {
        val organizationId = export.organizationId
        val ownerUserId = export.ownerId.takeIf { export.ownerType == "USER" }
        val platformOnly = export.ownerType == "PLATFORM"
        val categories = export.categoriesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val streamIds = if (ownerUserId != null)
        {
            auditLedgerEventRepository.findDistinctStreamIdsForPersonalExport(
                ownerUserId, export.occurredAfter, export.occurredBefore,
            )
        }
        else
        {
            auditLedgerEventRepository.findDistinctStreamIdsForExport(
                organizationId, platformOnly, export.occurredAfter, export.occurredBefore,
            )
        }

        for (streamId in streamIds)
        {
            while (auditArchiver.closeSegmentIfReady(streamId, force = true))
            {
            }
        }

        val streamReports = streamIds.map { streamId -> auditIntegrityService.checkStream(streamId) }
        val failedStream = streamReports.firstOrNull { !it.chainValid || it.segmentFailureNotes.isNotEmpty() }
        if (failedStream != null)
        {
            throw AuditExportIntegrityFailedException(
                "Stream ${failedStream.streamId} failed integrity verification: " +
                        (failedStream.segmentFailureNotes.firstOrNull() ?: failedStream.chainNote),
            )
        }

        val events = if (ownerUserId != null)
        {
            auditLedgerEventRepository.findForPersonalExport(
                ownerUserId, categories, export.occurredAfter, export.occurredBefore,
            )
        }
        else
        {
            auditLedgerEventRepository.findForExport(
                organizationId, platformOnly, categories, export.occurredAfter, export.occurredBefore,
            )
        }

        val coverageFailure = events.groupBy { it.streamId }.entries.firstNotNullOfOrNull { (streamId, streamEvents) ->
            val minSequence = streamEvents.minOf { it.streamSequence }
            val maxSequence = streamEvents.maxOf { it.streamSequence }
            val report = auditIntegrityService.checkCoverage(streamId, minSequence, maxSequence)
            report.takeIf { it.state != ArchiveCoverageState.COMPLETE }
        }
        if (coverageFailure != null)
        {
            throw AuditExportIntegrityFailedException(
                "Stream ${coverageFailure.streamId} does not have complete verified archive coverage for the " +
                        "requested range: ${coverageFailure.note}",
            )
        }

        val integrityRecords = streamReports.map { report ->
            ExportStreamIntegrityRecord(
                streamId = report.streamId,
                chainValid = report.chainValid,
                chainNote = report.chainNote,
                segmentsChecked = report.segmentsChecked,
                segmentsValid = report.segmentsValid,
            )
        }

        val schemaVersions = events.map { it.schemaVersion }.distinct().sorted().joinToString(",")
        val signingKeyId = signingKeyProvider.keyId()
        val generatedAt = Instant.now().toString()

        val eventsJsonlBytes = events.joinToString("\n") { event ->
            Json.encodeToString(ExportedLedgerEventRecord.serializer(), toRecord(event))
        }.toByteArray(StandardCharsets.UTF_8)
        val eventsCsvBytes = toCsv(events).toByteArray(StandardCharsets.UTF_8)
        val integrityJsonBytes = Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExportStreamIntegrityRecord.serializer()),
            integrityRecords,
        ).toByteArray(StandardCharsets.UTF_8)
        val readmeBytes = README_TEXT.toByteArray(StandardCharsets.UTF_8)
        val verifierBytes = requireNotNull(javaClass.getResourceAsStream("/audit/verify.mjs")) {
            "Bundled audit verifier resource is missing"
        }.use { it.readBytes() }

        val digestedEntries = listOf(
            "events.jsonl" to eventsJsonlBytes,
            "events.csv" to eventsCsvBytes,
            "integrity.json" to integrityJsonBytes,
            "README.txt" to readmeBytes,
            "verify.mjs" to verifierBytes,
        )
        val contentBytes = digestedEntries.sumOf { it.second.size.toLong() }
        if (contentBytes > MAX_BUNDLE_CONTENT_BYTES)
        {
            throw AuditExportIntegrityFailedException(
                "Export content exceeds the maximum bundle size of $MAX_BUNDLE_CONTENT_BYTES bytes",
            )
        }
        val entryDigests = digestedEntries.map { (name, bytes) ->
            ManifestEntryDigest(name = name, sha256Hex = MerkleTree.sha256Hex(bytes), byteLength = bytes.size.toLong())
        }
        val eventsMerkleRoot = MerkleTree.computeRoot(events.map { it.eventHash })

        val manifestEnvelope = AuditExportManifestEnvelope(
            formatVersion = 3,
            exportId = export.id.toString(),
            ownerType = export.ownerType,
            ownerId = export.ownerId?.toString(),
            organizationId = organizationId?.toString(),
            platformOnly = platformOnly,
            categories = export.categoriesCsv,
            occurredAfter = export.occurredAfter.toInstant().toString(),
            occurredBefore = export.occurredBefore.toInstant().toString(),
            eventCount = events.size,
            schemaVersions = schemaVersions,
            catalogVersion = events.maxOfOrNull { it.schemaVersion } ?: 0,
            entryDigests = entryDigests,
            eventsMerkleRoot = eventsMerkleRoot,
            signingKeyId = signingKeyId,
            generatedAt = generatedAt,
            fidelityNote = FIDELITY_NOTE,
        )
        val manifestJson = Json.encodeToString(AuditExportManifestEnvelope.serializer(), manifestEnvelope)
        val manifestJsonBytes = manifestJson.toByteArray(StandardCharsets.UTF_8)
        val signatureBytes = signingKeyProvider.sign(manifestJsonBytes)
        val signedManifest = SignedAuditExportManifest(
            manifestJson = manifestJson,
            signatureAlgorithm = "SHA256withRSA",
            signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes),
            publicKeyPem = signingKeyProvider.activePublicKeyPem(),
        )
        val signatureJsonBytes = Json.encodeToString(SignedAuditExportManifest.serializer(), signedManifest)
            .toByteArray(StandardCharsets.UTF_8)

        val bundleBytes = buildZip(
            "manifest.json" to manifestJsonBytes,
            "events.jsonl" to eventsJsonlBytes,
            "events.csv" to eventsCsvBytes,
            "integrity.json" to integrityJsonBytes,
            "signature.json" to signatureJsonBytes,
            "README.txt" to readmeBytes,
            "verify.mjs" to verifierBytes,
        )
        val bundleDigest = MerkleTree.sha256Hex(bundleBytes)

        val buildAttempt = export.buildWorkerId?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "unclaimed"
        val bundleObjectKey = "exports/${export.id}/$buildAttempt/bundle.zip"
        archiveStorage.putObject(bundleObjectKey, bundleBytes)

        logger.info(
            "audit export bundle built exportId={} eventCount={} bundleDigest={}",
            export.id, events.size, bundleDigest,
        )

        return AuditExportBundleResult(bundleObjectKey, bundleDigest, events.size, signingKeyId)
    }

    private fun toRecord(event: AuditLedgerEvent): ExportedLedgerEventRecord = ExportedLedgerEventRecord(
        eventId = event.eventId.toString(),
        eventTypeKey = event.eventTypeKey,
        category = event.category,
        outcome = event.outcome,
        schemaVersion = event.schemaVersion,
        occurredAt = event.occurredAt.toInstant().toString(),
        recordedAt = event.recordedAt.toInstant().toString(),
        ledgerTime = event.ledgerTime.toInstant().toString(),
        streamId = event.streamId,
        streamSequence = event.streamSequence,
        actorKind = event.actorKind,
        actorId = event.actorId?.toString(),
        actorRole = event.actorRole,
        actorLabel = event.actorLabel,
        sessionId = event.sessionId,
        serverTraceId = event.serverTraceId,
        correlationId = event.correlationId,
        causationId = event.causationId,
        ownerType = event.ownerType,
        ownerId = event.ownerId?.toString(),
        organizationId = event.organizationId?.toString(),
        organizationLabel = event.organizationLabel,
        targetType = event.targetType,
        targetId = event.targetId,
        targetLabel = event.targetLabel,
        reason = event.reason,
        payloadJson = event.payloadJson,
        prevHash = event.prevHash,
        eventHash = event.eventHash,
    )

    private fun toCsv(events: List<AuditLedgerEvent>): String
    {
        val header = listOf(
            "eventId", "eventTypeKey", "category", "outcome", "occurredAt", "streamId",
            "streamSequence",
            "actorKind",
            "actorId",
            "actorRole",
            "actorLabel",
            "ownerType",
            "ownerId",
            "organizationId",
            "organizationLabel", "targetType", "targetId", "targetLabel", "eventHash", "payloadJson",
        )
        val rows = events.map { event ->
            listOf(
                event.eventId.toString(), event.eventTypeKey, event.category, event.outcome,
                event.occurredAt.toInstant().toString(), event.streamId, event.streamSequence.toString(),
                event.actorKind, event.actorId?.toString().orEmpty(), event.actorRole.orEmpty(),
                event.actorLabel.orEmpty(), event.ownerType, event.ownerId?.toString().orEmpty(),
                event.organizationId?.toString().orEmpty(),
                event.organizationLabel.orEmpty(), event.targetType.orEmpty(), event.targetId.orEmpty(),
                event.targetLabel.orEmpty(),
                event.eventHash, event.payloadJson,
            ).joinToString(",") { csvEscape(it) }
        }
        return (listOf(header.joinToString(",") { csvEscape(it) }) + rows).joinToString("\n")
    }

    private fun neutralizeFormula(value: String): String =
        if (value.isNotEmpty() && value[0] in FORMULA_TRIGGER_CHARS) "'$value" else value

    private fun csvEscape(value: String): String
    {
        val neutralized = neutralizeFormula(value)
        return if (neutralized.contains(',') || neutralized.contains('"') || neutralized.contains('\n'))
        {
            "\"${neutralized.replace("\"", "\"\"")}\""
        }
        else
        {
            neutralized
        }
    }

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray
    {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries)
            {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
