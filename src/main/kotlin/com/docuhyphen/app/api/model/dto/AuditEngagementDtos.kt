package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuditEngagementCreateRequestDto(
    val resourceType: String? = null,
    val resourceId: String? = null,
    val auditorUserId: String? = null,
    val principalGroupId: String? = null,
    val categories: List<String>,
    val sensitivityLevel: String,
    val startsAt: String,
    val expiresAt: String,
    val purpose: String,
    val caseReference: String? = null,
    val legalBasis: String,
    val exportPermitted: Boolean = false,
    val maxQueryRangeDays: Int? = null,
    val downloadLimit: Int? = null,
)

@Serializable
data class AuditEngagementDto(
    val engagementId: String,
    val organizationId: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val auditorUserId: String? = null,
    val principalGroupId: String? = null,
    val categories: List<String>,
    val sensitivityLevel: String,
    val startsAt: String,
    val expiresAt: String,
    val purpose: String,
    val caseReference: String? = null,
    val legalBasis: String,
    val exportPermitted: Boolean,
    val maxQueryRangeDays: Int? = null,
    val downloadLimit: Int? = null,
    val status: String,
    val requestedByUserId: String,
    val requestedAt: String,
    val approvedByUserId: String? = null,
    val approvedAt: String? = null,
    val revokedByUserId: String? = null,
    val revokedAt: String? = null,
)
