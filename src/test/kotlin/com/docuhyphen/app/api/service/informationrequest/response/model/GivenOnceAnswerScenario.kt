package com.docuhyphen.app.api.service.informationrequest.response.model

import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices

internal data class GivenOnceAnswerScenario(
    val request: PublishedRequest,
    val services: InformationRequestRuntimeServices,
)
