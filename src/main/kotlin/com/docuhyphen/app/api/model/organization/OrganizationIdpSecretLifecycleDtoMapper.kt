package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.identity.OrganizationIdpSecretRollbackOutcome
import com.docuhyphen.app.api.model.identity.OrganizationIdpSecretRotationOutcome
import com.docuhyphen.app.api.model.identity.OrganizationIdpSecretStatusOutcome
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRollbackResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretRotationResponse
import com.docuhyphen.app.api.resource.model.OrganizationIdpSecretStatusResponse

object OrganizationIdpSecretLifecycleDtoMapper
{
    fun toRotationResponse(outcome: OrganizationIdpSecretRotationOutcome): OrganizationIdpSecretRotationResponse
    {
        return OrganizationIdpSecretRotationResponse(
            secretRef = outcome.secretRef,
            previousVersionId = outcome.previousVersionId,
            activeVersionId = outcome.activeVersionId,
            overlapUntilEpochMillis = outcome.overlapUntilEpochMillis,
        )
    }

    fun toRollbackResponse(outcome: OrganizationIdpSecretRollbackOutcome): OrganizationIdpSecretRollbackResponse
    {
        return OrganizationIdpSecretRollbackResponse(
            secretRef = outcome.secretRef,
            rolledBackToVersionId = outcome.rolledBackToVersionId,
            previousCurrentVersionId = outcome.previousCurrentVersionId,
        )
    }

    fun toStatusResponse(status: OrganizationIdpSecretStatusOutcome): OrganizationIdpSecretStatusResponse
    {
        return OrganizationIdpSecretStatusResponse(
            secretRef = status.secretRef,
            disabled = status.disabled,
            rotationPhase = status.rotationPhase,
            currentVersionId = status.currentVersionId,
            previousVersionId = status.previousVersionId,
            pendingVersionId = status.pendingVersionId,
            overlapUntilEpochMillis = status.overlapUntilEpochMillis,
            overlapActive = status.overlapActive,
        )
    }
}
