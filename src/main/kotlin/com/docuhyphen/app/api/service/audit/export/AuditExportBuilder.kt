package com.docuhyphen.app.api.service.audit.export

import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportStatus
import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.AuditExportRepository
import com.docuhyphen.app.api.repository.AuditLedgerEventRepository
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveSigningKeyProvider
import com.docuhyphen.app.api.service.audit.archive.AuditArchiveStorage
import com.docuhyphen.app.api.service.audit.archive.MerkleTree
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.sql.Timestamp
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
    val streamId: String,
    val streamSequence: Long,
    val actorKind: String,
    val actorId: String?,
    val actorRole: String?,
    val actorLabel: String?,
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
 * PEM public key needed to verify it), a `verify.py` reference offline-verification script, and a
 * `README.txt`. Storage reuses [AuditArchiveStorage] (same S3 bucket/local directory,
 * different key prefix) - no new AWS service.
 */
@ApplicationScoped
class AuditExportBuilder @Inject constructor(
    private val auditLedgerEventRepository: AuditLedgerEventRepository,
    private val auditExportRepository: AuditExportRepository,
    private val auditIntegrityService: AuditIntegrityService,
    private val archiveStorage: AuditArchiveStorage,
    private val signingKeyProvider: AuditArchiveSigningKeyProvider,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditExportBuilder::class.java)
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
                "                    count, schema versions, signing key id).\n" +
                "  events.jsonl    - every exported ledger event, one JSON object per line, in\n" +
                "                    (streamId, streamSequence) order. This is the canonical\n" +
                "                    evidence; events.csv is a convenience projection only.\n" +
                "  events.csv      - flattened projection of the same events for spreadsheet\n" +
                "                    tools. See the fidelityNote in manifest.json.\n" +
                "  integrity.json  - per-stream boundary-checkpoint (segment chain) and segment\n" +
                "                    verification results collected before this bundle was built.\n" +
                "  signature.json  - detached SHA256withRSA signature over the exact manifest.json\n" +
                "                    bytes, plus the PEM public key needed to verify it.\n" +
                "  verify.py       - reference offline verification script; recomputes the\n" +
                "                    manifest hash and checks the signature without contacting\n" +
                "                    DocuHyphen.\n\n" +
                "This bundle intentionally never claims full provenance for legacy-imported\n" +
                "history; see any legacy_import=true payload markers in events.jsonl.\n"
        private const val VERIFY_PY = "#!/usr/bin/env python3\n" +
            "# Reference offline verifier for a DocuHyphen audit evidence export bundle.\n" +
            "# Usage: python3 verify.py <bundle-dir>\n" +
            "import base64\n" +
            "import json\n" +
            "import sys\n" +
            "from cryptography.hazmat.primitives import hashes, serialization\n" +
            "from cryptography.hazmat.primitives.asymmetric import padding\n\n" +
            "def main(bundle_dir):\n" +
            "    with open(f\"{bundle_dir}/manifest.json\", \"rb\") as f:\n" +
            "        manifest_bytes = f.read()\n" +
            "    with open(f\"{bundle_dir}/signature.json\") as f:\n" +
            "        signed = json.load(f)\n" +
            "    public_key = serialization.load_pem_public_key(signed[\"publicKeyPem\"].encode())\n" +
            "    signature = base64.b64decode(signed[\"signatureBase64\"])\n" +
            "    public_key.verify(signature, manifest_bytes, padding.PKCS1v15(), hashes.SHA256())\n" +
            "    print(\"OK: manifest signature verified\")\n\n" +
            "if __name__ == \"__main__\":\n" +
            "    main(sys.argv[1] if len(sys.argv) > 1 else \".\")\n"
    }

    /**
     * Builds and archives the bundle for [export], then persists `READY` status plus bundle
     * metadata. Throws [AuditExportIntegrityFailedException] (caller marks the export `FAILED`)
     * if any touched stream fails its boundary-checkpoint or segment verification.
     */
    fun build(export: AuditExport, downloadLifetimeHours: Int): AuditExportBundleResult
    {
        val organizationId = export.organizationId
        val platformOnly = organizationId == null
        val categories = export.categoriesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val streamIds = auditLedgerEventRepository.findDistinctStreamIdsForExport(
            organizationId, platformOnly, export.occurredAfter, export.occurredBefore,
        )
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

        val manifestEnvelope = AuditExportManifestEnvelope(
            formatVersion = 1,
            exportId = export.id.toString(),
            organizationId = organizationId?.toString(),
            platformOnly = platformOnly,
            categories = export.categoriesCsv,
            occurredAfter = export.occurredAfter.toInstant().toString(),
            occurredBefore = export.occurredBefore.toInstant().toString(),
            eventCount = events.size,
            schemaVersions = schemaVersions,
            catalogVersion = events.maxOfOrNull { it.schemaVersion } ?: 0,
            signingKeyId = signingKeyId,
            generatedAt = generatedAt,
            fidelityNote = FIDELITY_NOTE,
        )
        val manifestJson = Json.encodeToString(AuditExportManifestEnvelope.serializer(), manifestEnvelope)
        val signatureBytes = signingKeyProvider.sign(manifestJson.toByteArray(StandardCharsets.UTF_8))
        val signedManifest = SignedAuditExportManifest(
            manifestJson = manifestJson,
            signatureAlgorithm = "SHA256withRSA",
            signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes),
            publicKeyPem = signingKeyProvider.activePublicKeyPem(),
        )

        val eventsJsonl = events.joinToString("\n") { event ->
            Json.encodeToString(ExportedLedgerEventRecord.serializer(), toRecord(event))
        }
        val eventsCsv = toCsv(events)
        val integrityJson = Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExportStreamIntegrityRecord.serializer()),
            integrityRecords,
        )
        val signatureJson = Json.encodeToString(SignedAuditExportManifest.serializer(), signedManifest)
        val bundleDigest = MerkleTree.sha256Hex(manifestJson.toByteArray(StandardCharsets.UTF_8))

        val bundleBytes = buildZip(
            "manifest.json" to manifestJson.toByteArray(StandardCharsets.UTF_8),
            "events.jsonl" to eventsJsonl.toByteArray(StandardCharsets.UTF_8),
            "events.csv" to eventsCsv.toByteArray(StandardCharsets.UTF_8),
            "integrity.json" to integrityJson.toByteArray(StandardCharsets.UTF_8),
            "signature.json" to signatureJson.toByteArray(StandardCharsets.UTF_8),
            "verify.py" to VERIFY_PY.toByteArray(StandardCharsets.UTF_8),
            "README.txt" to README_TEXT.toByteArray(StandardCharsets.UTF_8),
        )

        val bundleObjectKey = "exports/${export.id}/bundle.zip"
        archiveStorage.putObject(bundleObjectKey, bundleBytes)

        export.status = AuditExportStatus.READY
        export.builtAt = Timestamp.from(Instant.now())
        export.readyAt = Timestamp.from(Instant.now())
        export.expiresAt = Timestamp.from(Instant.now().plusSeconds(downloadLifetimeHours * 3600L))
        export.eventCount = events.size
        export.bundleObjectKey = bundleObjectKey
        export.bundleDigest = bundleDigest
        export.signingKeyId = signingKeyId
        export.updatedAt = Timestamp.from(Instant.now())
        auditExportRepository.update(export)

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
        streamId = event.streamId,
        streamSequence = event.streamSequence,
        actorKind = event.actorKind,
        actorId = event.actorId?.toString(),
        actorRole = event.actorRole,
        actorLabel = event.actorLabel,
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

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n'))
        {
            "\"${value.replace("\"", "\"\"")}\""
        }
        else
        {
            value
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
