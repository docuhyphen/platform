package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.AuditExport
import com.docuhyphen.app.api.model.entity.AuditExportApproval
import com.docuhyphen.app.api.service.audit.export.OrganizationIntegrityReport
import com.docuhyphen.app.api.service.audit.export.StreamIntegrityReport

/** All `AuditExport`/integrity-report -> DTO mapping in one dedicated class, per this codebase's `toDto` convention (resources/services never inline entity->DTO mapping). */
object AuditExportDtoMapper
{
    fun toDto(export: AuditExport): AuditExportDto = AuditExportDto(
        exportId = export.id.toString(),
        organizationId = export.organizationId?.toString(),
        requestedByUserId = export.requestedByUserId.toString(),
        requestedAt = export.requestedAt.toInstant().toString(),
        categories = export.categoriesCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
        occurredAfter = export.occurredAfter.toInstant().toString(),
        occurredBefore = export.occurredBefore.toInstant().toString(),
        purpose = export.purpose,
        caseReference = export.caseReference,
        legalBasis = export.legalBasis,
        status = export.status.name,
        requiredApprovals = export.requiredApprovals,
        approvalCount = export.approvalCount,
        readyAt = export.readyAt?.toInstant()?.toString(),
        expiresAt = export.expiresAt?.toInstant()?.toString(),
        failedAt = export.failedAt?.toInstant()?.toString(),
        failureReason = export.failureReason,
        revokedAt = export.revokedAt?.toInstant()?.toString(),
        downloadCount = export.downloadCount,
        downloadLimit = export.downloadLimit,
        eventCount = export.eventCount,
        bundleDigest = export.bundleDigest,
        signingKeyId = export.signingKeyId,
    )

    fun toApprovalDto(approval: AuditExportApproval): AuditExportApprovalDto = AuditExportApprovalDto(
        approvedByUserId = approval.approvedByUserId.toString(),
        approvedAt = approval.approvedAt.toInstant().toString(),
        note = approval.note,
    )

    fun toIntegrityDto(report: OrganizationIntegrityReport): AuditOrganizationIntegrityDto = AuditOrganizationIntegrityDto(
        organizationId = report.organizationId?.toString(),
        platformOnly = report.platformOnly,
        allValid = report.allValid,
        streams = report.streams.map(::toStreamIntegrityDto),
    )

    private fun toStreamIntegrityDto(report: StreamIntegrityReport): AuditStreamIntegrityDto = AuditStreamIntegrityDto(
        streamId = report.streamId,
        chainValid = report.chainValid,
        chainNote = report.chainNote,
        segmentsChecked = report.segmentsChecked,
        segmentsValid = report.segmentsValid,
        segmentFailureNotes = report.segmentFailureNotes,
    )
}
