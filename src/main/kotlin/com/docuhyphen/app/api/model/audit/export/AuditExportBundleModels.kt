package com.docuhyphen.app.api.model.audit.export

import kotlinx.serialization.Serializable

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
    val ownerType: String,
    val ownerId: String?,
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

@Serializable
data class ExportStreamIntegrityRecord(
    val streamId: String,
    val chainValid: Boolean,
    val chainNote: String,
    val segmentsChecked: Int,
    val segmentsValid: Int,
)

@Serializable
data class ManifestEntryDigest(
    val name: String,
    val sha256Hex: String,
    val byteLength: Long,
)

@Serializable
data class AuditExportManifestEnvelope(
    val formatVersion: Int,
    val exportId: String,
    val ownerType: String,
    val ownerId: String?,
    val organizationId: String?,
    val platformOnly: Boolean,
    val categories: String,
    val occurredAfter: String,
    val occurredBefore: String,
    val eventCount: Int,
    val schemaVersions: String,
    val catalogVersion: Int,
    val entryDigests: List<ManifestEntryDigest>,
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

data class AuditExportBundleResult(
    val bundleObjectKey: String,
    val bundleDigest: String,
    val eventCount: Int,
    val signingKeyId: String,
)
