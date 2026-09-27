package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.RecordPreservationHold
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

    fun toDto(hold: RecordPreservationHold): AuditLegalHoldDto = AuditLegalHoldDto(
        holdId = hold.id.toString(),
        ownerKind = hold.ownerKind.name,
        ownerId = hold.ownerId?.toString(),
        resourceType = hold.resourceType,
        resourceId = hold.resourceId,
        reason = hold.reason,
        caseReference = hold.caseReference,
        status = hold.status.name,
        scope = hold.scope.name,
        effectiveFrom = hold.effectiveFrom.toInstant().toString(),
        placedByPrincipalKind = hold.placedByPrincipalKind.name,
        placedByPrincipalId = hold.placedByPrincipalId.toString(),
        placedAt = hold.placedAt.toInstant().toString(),
        releasedByPrincipalKind = hold.releasedByPrincipalKind?.name,
        releasedByPrincipalId = hold.releasedByPrincipalId?.toString(),
        releasedAt = hold.releasedAt?.toInstant()?.toString(),
        releaseReason = hold.releaseReason,
        holdRevision = hold.holdRevision,
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
