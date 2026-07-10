package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditExportCreateRequestDto(
    val categories: List<String>,
    val occurredAfter: String,
    val occurredBefore: String,
    val purpose: String,
    val caseReference: String? = null,
    val legalBasis: String? = null,
    val downloadLimit: Int? = null,
)

@Serializable
data class AuditExportApprovalRequestDto(
    val note: String? = null,
)

@Serializable
data class AuditExportDto(
    val exportId: String,
    val organizationId: String? = null,
    val requestedByUserId: String,
    val requestedAt: String,
    val categories: List<String>,
    val occurredAfter: String,
    val occurredBefore: String,
    val purpose: String,
    val caseReference: String? = null,
    val legalBasis: String? = null,
    val status: String,
    val requiredApprovals: Int,
    val approvalCount: Int,
    val readyAt: String? = null,
    val expiresAt: String? = null,
    val failedAt: String? = null,
    val failureReason: String? = null,
    val revokedAt: String? = null,
    val downloadCount: Int,
    val downloadLimit: Int? = null,
    val eventCount: Int? = null,
    val bundleDigest: String? = null,
    val signingKeyId: String? = null,
)

@Serializable
data class AuditExportApprovalDto(
    val approvedByUserId: String,
    val approvedAt: String,
    val note: String? = null,
)

@Serializable
data class AuditStreamIntegrityDto(
    val streamId: String,
    val chainValid: Boolean,
    val chainNote: String,
    val segmentsChecked: Int,
    val segmentsValid: Int,
    val segmentFailureNotes: List<String> = emptyList(),
)

@Serializable
data class AuditOrganizationIntegrityDto(
    val organizationId: String? = null,
    val platformOnly: Boolean,
    val allValid: Boolean,
    val streams: List<AuditStreamIntegrityDto>,
)
