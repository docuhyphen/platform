package com.docuhyphen.app.api.model.identity

import java.time.Instant
import java.util.UUID

data class OrganizationIdpRotationPreviewCandidate(
    val configId: UUID,
    val provider: String,
    val secretRef: String,
    val updatedDate: Instant,
    val due: Boolean,
    val overdueByDays: Long,
)

data class OrganizationIdpRotationPreviewResult(
    val intervalDays: Long,
    val evaluated: Int,
    val dueCount: Int,
    val candidates: List<OrganizationIdpRotationPreviewCandidate>,
)

data class OrganizationIdpRotationRunResult(
    val evaluated: Int,
    val rotated: Int,
    val failed: Int,
    val skipped: Int,
)

data class OrganizationIdpRotationStatusItem(
    val configId: UUID,
    val provider: String,
    val secretRef: String,
    val disabled: Boolean,
    val rotationPhase: String? = null,
    val currentVersionId: String? = null,
    val previousVersionId: String? = null,
    val pendingVersionId: String? = null,
    val overlapUntilEpochMillis: Long? = null,
    val overlapActive: Boolean,
    val due: Boolean,
    val overdueByDays: Long,
)

data class OrganizationIdpRotationStatusResult(
    val intervalDays: Long,
    val evaluated: Int,
    val dueCount: Int,
    val overlapActiveCount: Int,
    val disabledCount: Int,
    val retirePhaseCount: Int,
    val items: List<OrganizationIdpRotationStatusItem>,
)
