package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.identity.OrganizationIdpRotationPreviewCandidate
import com.docuhyphen.app.api.model.identity.OrganizationIdpRotationPreviewResult
import com.docuhyphen.app.api.model.identity.OrganizationIdpRotationRunResult
import com.docuhyphen.app.api.model.identity.OrganizationIdpRotationStatusItem
import com.docuhyphen.app.api.model.identity.OrganizationIdpRotationStatusResult
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationPreviewCandidate
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationPreviewResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationRunResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationStatusItemResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationStatusResponse

object OrganizationIdpSecretRotationRunbookDtoMapper
{
    fun toStatusResponse(status: OrganizationIdpRotationStatusResult): OrganizationIdpSecretRotationStatusResponse
    {
        return OrganizationIdpSecretRotationStatusResponse(
            intervalDays = status.intervalDays,
            evaluated = status.evaluated,
            dueCount = status.dueCount,
            overlapActiveCount = status.overlapActiveCount,
            disabledCount = status.disabledCount,
            retirePhaseCount = status.retirePhaseCount,
            items = status.items.map(::toStatusItemResponse),
        )
    }

    fun toPreviewResponse(preview: OrganizationIdpRotationPreviewResult): OrganizationIdpSecretRotationPreviewResponse
    {
        return OrganizationIdpSecretRotationPreviewResponse(
            intervalDays = preview.intervalDays,
            evaluated = preview.evaluated,
            dueCount = preview.dueCount,
            candidates = preview.candidates.map(::toPreviewCandidate),
        )
    }

    fun toRunResponse(result: OrganizationIdpRotationRunResult): OrganizationIdpSecretRotationRunResponse
    {
        return OrganizationIdpSecretRotationRunResponse(
            evaluated = result.evaluated,
            rotated = result.rotated,
            failed = result.failed,
            skipped = result.skipped,
        )
    }

    private fun toStatusItemResponse(item: OrganizationIdpRotationStatusItem): OrganizationIdpSecretRotationStatusItemResponse
    {
        return OrganizationIdpSecretRotationStatusItemResponse(
            configId = item.configId.toString(),
            provider = item.provider,
            secretRef = item.secretRef,
            disabled = item.disabled,
            rotationPhase = item.rotationPhase,
            currentVersionId = item.currentVersionId,
            previousVersionId = item.previousVersionId,
            pendingVersionId = item.pendingVersionId,
            overlapUntilEpochMillis = item.overlapUntilEpochMillis,
            overlapActive = item.overlapActive,
            due = item.due,
            overdueByDays = item.overdueByDays,
        )
    }

    private fun toPreviewCandidate(candidate: OrganizationIdpRotationPreviewCandidate): OrganizationIdpSecretRotationPreviewCandidate
    {
        return OrganizationIdpSecretRotationPreviewCandidate(
            configId = candidate.configId.toString(),
            provider = candidate.provider,
            secretRef = candidate.secretRef,
            updatedDate = candidate.updatedDate.toString(),
            due = candidate.due,
            overdueByDays = candidate.overdueByDays,
        )
    }
}
