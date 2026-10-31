package com.docuhyphen.app.api.model.informationrequest.capability

import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestOwnerStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestPlanQuotas
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestStandingReason

data class InformationRequestCapabilities(
    val scope: InformationRequestOwnerStanding,
    val quotas: InformationRequestPlanQuotas,
    val newWorkUnavailableReason: InformationRequestStandingReason?,
    val typedAnswersAvailable: Boolean,
    val personalTemplatesAvailable: Boolean,
    val assignedWork: Boolean,
    val holdsRequests: Boolean,
) {
    val newWorkAvailable: Boolean get() = newWorkUnavailableReason == null
}
