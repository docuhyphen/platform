package com.docuhyphen.app.api.resource.informationrequest.model

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestSignedInCaller
import com.docuhyphen.app.api.service.informationrequest.conformance.model.PublishedRequest

internal data class UnknownTargetFixture(
    val request: PublishedRequest,
    val caller: InformationRequestSignedInCaller,
)
