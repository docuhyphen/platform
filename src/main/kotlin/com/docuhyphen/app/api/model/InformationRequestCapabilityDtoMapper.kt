package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestCapabilitiesDto
import com.docuhyphen.app.api.model.informationrequest.capability.InformationRequestCapabilities

object InformationRequestCapabilityDtoMapper
{
    fun toDto(capabilities: InformationRequestCapabilities): InformationRequestCapabilitiesDto =
        InformationRequestCapabilitiesDto(
            ownerType = capabilities.scope.ownerType,
            planCode = capabilities.scope.planCode,
            subscriptionStatus = capabilities.scope.status,
            enforcementMode = capabilities.scope.enforcementMode,
            featureIncluded = capabilities.scope.featureIncluded,
            newWorkAvailable = capabilities.scope.newWorkAvailable,
            newWorkUnavailableReason = capabilities.scope.newWorkUnavailableReason,
            operationallySuspended = capabilities.scope.operationallySuspended,
            typedAnswersAvailable = capabilities.typedAnswersAvailable,
            personalTemplatesAvailable = capabilities.personalTemplatesAvailable,
            assignedWork = capabilities.assignedWork,
            holdsRequests = capabilities.holdsRequests,
        )
}
