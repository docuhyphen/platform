package com.docuhyphen.app.api.model.informationrequest.capability

import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestOwnerStanding

data class InformationRequestCapabilities(
    val scope: InformationRequestOwnerStanding,
    val typedAnswersAvailable: Boolean,
    val personalTemplatesAvailable: Boolean,
    val assignedWork: Boolean,
    val holdsRequests: Boolean,
)
