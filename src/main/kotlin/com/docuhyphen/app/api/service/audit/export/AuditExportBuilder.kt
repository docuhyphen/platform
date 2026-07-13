package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.archive.ArchiveCoverageState
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveSigningKeyProvider
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.archive.AuditArchiver
import com.docuhyphen.app.api.service.audit.archive.MerkleTree
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One exported ledger event row, at full fidelity (no redaction - the requester's access was already gated by approval/authorization before the bundle was built). */
@Serializable
data class ExportedLedgerEventRecord(
    val eventId: String,
    val eventTypeKey: String,
    val category: String,
    val outcome: String,
    val schemaVersion: Int,
    val occurredAt: String,
    val recordedAt: String,
    val ledgerTime: String,
    val streamId: String,
    val streamSequence: Long,
    val actorKind: String,
    val actorId: String?,
    val actorRole: String?,
    val actorLabel: String?,
    val sessionId: String?,
    val serverTraceId: String?,
    val correlationId: String?,
    val causationId: String?,
    val organizationId: String?,
    val organizationLabel: String?,
    val targetType: String?,
    val targetId: String?,
    val targetLabel: String?,
    val reason: String?,
    val payloadJson: String,
    val prevHash: String?,
    val eventHash: String,
)

/** Per-stream integrity summary embedded in `integrity.json`, so the bundle discloses exactly what was checked and its result - never a false blanket "verified" claim. */
@Serializable
data class ExportStreamIntegrityRecord(
    val streamId: String,
    val chainValid: Boolean,
    val chainNote: String,
    val segmentsChecked: Int,
    val segmentsValid: Int,
)

/** One protected bundle entry's content address, enabling independent detection of modifications, deletions, or substitutions without trusting ZIP metadata. */
@Serializable
data class ManifestEntryDigest(
    val name: String,
    val sha256Hex: String,
    val byteLength: Long,
)

/** Canonical, signed manifest fields for one export bundle. Field order is fixed by declaration order so the same export always produces byte-identical manifest JSON (what the detached signature is computed over). */
@Serializable
data class AuditExportManifestEnvelope(
    val formatVersion: Int,
    val exportId: String,
    val organizationId: String?,
    val platformOnly: Boolean,
    val categories: String,
    val occurredAfter: String,
    val occurredBefore: String,
    val eventCount: Int,
    val schemaVersions: String,
    val catalogVersion: Int,
    /** Digest and length of every bundle entry other than `manifest.json` and `signature.json`, enabling independent bundle verification. */
    val entryDigests: List<ManifestEntryDigest>,
    /** Merkle root over every exported event's own `eventHash`, in the exact order written to `events.jsonl` - an independent, per-event cross-check that does not rely on `events.jsonl`'s own digest alone. */
    val eventsMerkleRoot: String,
    val signingKeyId: String,
    val generatedAt: String,
    val fidelityNote: String,
)

@Serializable
data class SignedAuditExportManifest(
    val manifestJson: String,
    val signatureAlgorithm: String,
    val signatureBase64: String,
    val publicKeyPem: String,
)

class AuditExportIntegrityFailedException(message: String) : RuntimeException(message)

data class AuditExportBundleResult(
    val bundleObjectKey: String,
    val bundleDigest: String,
    val eventCount: Int,
    val signingKeyId: String,
)

/**
 * Builds the signed, independently verifiable evidence bundle for one [AuditExport]. Fail-closed: verifies every touched
 * stream's segment-chain boundary checkpoint (not just that inner events link, which the
 * architecture explicitly calls out as insufficient - see [AuditArchiveVerifier.verifyStreamChain])
 * and every archived segment's own content/signature before a single bundle byte is written; any
 * failure throws [AuditExportIntegrityFailedException] rather than emit unverifiable evidence.
 *
 * The bundle is a single `bundle.zip` object containing `manifest.json`, `events.jsonl`,
 * `events.csv` (with an explicit fidelity note - CSV cannot represent the nested payload as
 * faithfully as JSON), `integrity.json`, `signature.json` (detached manifest signature plus the
 * PEM public key needed to verify it), and a `README.txt`. Storage reuses
 * [AuditArchiveStorage] (same S3 bucket/local directory,
 * different key prefix) - no new AWS service.
 */
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

    /**
     * Builds and archives the bundle for [export], then persists `READY` status plus bundle
     * metadata. Throws [AuditExportIntegrityFailedException] (caller marks the export `FAILED`)
     * if any touched stream fails its boundary-checkpoint or segment verification.
     */
    fun build(export: AuditExport): AuditExportBundleResult
    {
        val organizationId = export.organizationId
        val platformOnly = organizationId == null
        val categories = export.categoriesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val streamIds = auditLedgerEventRepository.findDistinctStreamIdsForExport(
            organizationId, platformOnly, export.occurredAfter, export.occurredBefore,
        )

        // Flush any small trailing range that has not yet reached the segment-size threshold, so
        // a legitimately recent-but-unarchived tail does not by itself block the coverage gate
        // below. This is a best-effort attempt: if the archiver's own view of the stream still
        // cannot close a checkpoint over the full requested range, the gate rejects the export.
        for (streamId in streamIds)
        {
            while (auditArchiver.closeSegmentIfReady(streamId, force = true))
            {
                // keep flushing until the stream has no more unarchived candidate events
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

        val events = auditLedgerEventRepository.findForExport(
            organizationId, platformOnly, categories, export.occurredAfter, export.occurredBefore,
        )

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

        // Every entry below other than manifest.json (this file) and signature.json (not yet
        // computed - it wraps the signature over the manifest that includes these digests) is
        // built first, so its content-address can be bound into the signed manifest.
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
            formatVersion = 2,
            exportId = export.id.toString(),
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
        // The digest covers the final immutable bundle object itself (not just the manifest text),
        // so a byte-level modification of the stored ZIP after it is written - not merely a
        // modification of one entry's parsed content - is detectable by re-hashing the object at
        // download time without needing to open it.
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
            "streamSequence", "actorKind", "actorId", "actorRole", "actorLabel", "organizationId",
            "organizationLabel", "targetType", "targetId", "targetLabel", "eventHash", "payloadJson",
        )
        val rows = events.map { event ->
            listOf(
                event.eventId.toString(), event.eventTypeKey, event.category, event.outcome,
                event.occurredAt.toInstant().toString(), event.streamId, event.streamSequence.toString(),
                event.actorKind, event.actorId?.toString().orEmpty(), event.actorRole.orEmpty(),
                event.actorLabel.orEmpty(), event.organizationId?.toString().orEmpty(),
                event.organizationLabel.orEmpty(), event.targetType.orEmpty(), event.targetId.orEmpty(),
                event.targetLabel.orEmpty(),
                event.eventHash, event.payloadJson,
            ).joinToString(",") { csvEscape(it) }
        }
        return (listOf(header.joinToString(",") { csvEscape(it) }) + rows).joinToString("\n")
    }

    /**
     * Neutralizes spreadsheet formula injection (a value beginning with `=`, `+`, `-`, or `@` is
     * interpreted as a formula by common spreadsheet tools) by prefixing an escaping apostrophe.
     * Applies only to the CSV convenience projection - [ExportedLedgerEventRecord] in
     * `events.jsonl` is never altered, since that file is the canonical evidence.
     */
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
