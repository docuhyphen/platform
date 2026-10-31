package com.docuhyphen.app.api.model.audit.archive

import kotlinx.serialization.Serializable

@Serializable
data class AuditArchiveManifestEnvelope(
    val formatVersion: Int,
    val streamId: String,
    val firstSequence: Long,
    val lastSequence: Long,
    val eventCount: Int,
    val merkleRoot: String,
    val segmentDigest: String,
    val prevSegmentDigest: String?,
    val schemaVersions: String,
    val signingKeyId: String,
    val contentObjectKey: String,
    val contentSha256: String,
    val contentLength: Long,
    val createdAt: String,
)

@Serializable
data class SignedAuditArchiveManifest(
    val manifestJson: String,
    val signatureAlgorithm: String,
    val signatureBase64: String,
)

@Serializable
data class ArchivedLedgerEventRecord(
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
    val sessionId: String?,
    val serverTraceId: String?,
    val correlationId: String?,
    val causationId: String?,
    val ownerType: String = "PLATFORM",
    val ownerId: String? = null,
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

data class SegmentCloseResult(val closed: Int, val skipped: Int, val failed: Int)
