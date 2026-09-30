package com.docuhyphen.app.api.model.informationrequest

data class InformationRequestCapabilities(
    val scope: InformationRequestOwnerStanding,
    val typedAnswersAvailable: Boolean,
    val personalTemplatesAvailable: Boolean,
    val assignedWork: Boolean,
    val holdsRequests: Boolean,
)
