package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditRetentionPolicyDto(
    val category: String,
    val ledgerRetentionDays: Int,
    val archiveRetentionDays: Int,
    val legalHoldEligible: Boolean,
    val identityTreatment: String,
    val isOverride: Boolean,
)

@Serializable
data class AuditRetentionPolicyUpdateRequestDto(
    val ledgerRetentionDays: Int,
    val archiveRetentionDays: Int,
    val legalHoldEligible: Boolean = true,
    val identityTreatment: String = "READABLE",
)

@Serializable
data class AuditLegalHoldCreateRequestDto(
    val resourceType: String,
    val resourceId: String,
    val reason: String,
    val caseReference: String? = null,
)

@Serializable
data class AuditLegalHoldDto(
    val holdId: String,
    val organizationId: String? = null,
    val resourceType: String,
    val resourceId: String,
    val reason: String,
    val caseReference: String? = null,
    val status: String,
    val placedByUserId: String,
    val placedAt: String,
    val releasedByUserId: String? = null,
    val releasedAt: String? = null,
)

@Serializable
data class AuditAnalyticsReconciliationDto(
    val organizationId: String? = null,
    val platformOnly: Boolean,
    val ledgerEventCount: Long,
    val analyticsFactCount: Long,
    val matches: Boolean,
    val missingLedgerEventIds: List<String>,
)
