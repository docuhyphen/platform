package com.docuhyphen.app.api.model.audit

import com.docuhyphen.app.api.model.entity.AuditEngagementSensitivity
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import java.time.Instant
import java.util.UUID

data class AuditEngagementRequest(
    val organizationId: UUID?,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val auditorUserId: UUID? = null,
    val principalGroupId: UUID? = null,
    val categories: Set<AuditCategory>,
    val sensitivityLevel: AuditEngagementSensitivity,
    val startsAt: Instant,
    val expiresAt: Instant,
    val purpose: String,
    val caseReference: String? = null,
    val legalBasis: String,
    val exportPermitted: Boolean = false,
    val maxQueryRangeDays: Int? = null,
    val downloadLimit: Int? = null,
)

data class AuditEngagementAccess(
    val engagementId: UUID,
    val sensitivityLevel: AuditEngagementSensitivity,
    val exportPermitted: Boolean,
    val maxQueryRangeDays: Int?,
    val downloadLimit: Int?,
)
