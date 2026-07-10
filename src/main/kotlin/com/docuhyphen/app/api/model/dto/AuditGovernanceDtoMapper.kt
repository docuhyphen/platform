package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.AuditLegalHold
import com.docuhyphen.app.api.service.audit.AnalyticsReconciliationReport
import com.docuhyphen.app.api.service.audit.RetentionPolicySpec

/** All retention/legal-hold/reconciliation entity-and-report -> DTO mapping, per this codebase's `toDto` convention. */
object AuditGovernanceDtoMapper
{
    fun toDto(spec: RetentionPolicySpec): AuditRetentionPolicyDto = AuditRetentionPolicyDto(
        category = spec.category.name,
        ledgerRetentionDays = spec.ledgerRetentionDays,
        archiveRetentionDays = spec.archiveRetentionDays,
        legalHoldEligible = spec.legalHoldEligible,
        identityTreatment = spec.identityTreatment.name,
        isOverride = spec.isOverride,
    )

    fun toDto(hold: AuditLegalHold): AuditLegalHoldDto = AuditLegalHoldDto(
        holdId = hold.id.toString(),
        organizationId = hold.organizationId?.toString(),
        resourceType = hold.resourceType,
        resourceId = hold.resourceId,
        reason = hold.reason,
        caseReference = hold.caseReference,
        status = hold.status.name,
        placedByUserId = hold.placedByUserId.toString(),
        placedAt = hold.placedAt.toInstant().toString(),
        releasedByUserId = hold.releasedByUserId?.toString(),
        releasedAt = hold.releasedAt?.toInstant()?.toString(),
    )

    fun toDto(report: AnalyticsReconciliationReport): AuditAnalyticsReconciliationDto = AuditAnalyticsReconciliationDto(
        organizationId = report.organizationId?.toString(),
        platformOnly = report.platformOnly,
        ledgerEventCount = report.ledgerEventCount,
        analyticsFactCount = report.analyticsFactCount,
        matches = report.matches,
        missingLedgerEventIds = report.missingLedgerEventIds.map { it.toString() },
    )
}
