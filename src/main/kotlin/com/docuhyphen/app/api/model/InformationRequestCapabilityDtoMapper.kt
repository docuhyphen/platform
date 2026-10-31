package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestCapabilitiesDto
import com.docuhyphen.app.api.model.dto.InformationRequestPlanQuotasDto
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilities
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestPlanQuotas

object InformationRequestCapabilityDtoMapper
{
    fun toDto(capabilities: InformationRequestCapabilities): InformationRequestCapabilitiesDto =
        InformationRequestCapabilitiesDto(
            ownerType = capabilities.scope.ownerType,
            planCode = capabilities.scope.planCode,
            subscriptionStatus = capabilities.scope.status,
            enforcementMode = capabilities.scope.enforcementMode,
            featureIncluded = capabilities.scope.featureIncluded,
            newWorkAvailable = capabilities.newWorkAvailable,
            newWorkUnavailableReason = capabilities.newWorkUnavailableReason,
            operationallySuspended = capabilities.scope.operationallySuspended,
            typedAnswersAvailable = capabilities.typedAnswersAvailable,
            personalTemplatesAvailable = capabilities.personalTemplatesAvailable,
            assignedWork = capabilities.assignedWork,
            holdsRequests = capabilities.holdsRequests,
            quotas = toDto(capabilities.quotas),
        )

    private fun toDto(quotas: InformationRequestPlanQuotas): InformationRequestPlanQuotasDto =
        InformationRequestPlanQuotasDto(
            maxOpenRequests = quotas.maxOpenRequests,
            openRequests = quotas.openRequests,
            maxActingPartiesPerRequest = quotas.maxActingPartiesPerRequest,
            maxEvidenceFilesPerRequest = quotas.maxEvidenceFilesPerRequest,
            maxEvidenceBytesPerRequest = quotas.maxEvidenceBytesPerRequest,
            maxCommittedEvidenceBytes = quotas.maxCommittedEvidenceBytes,
            committedEvidenceBytes = quotas.committedEvidenceBytes,
        )
}
