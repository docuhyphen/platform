package com.docuhyphen.app.api.service.informationrequest.externalsource.model

import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.informationrequest.conformance.model.ConformanceRequest
import com.docuhyphen.app.api.service.informationrequest.externalsource.MutableClock
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices

internal data class ImportedValueSetup(
    val request: ConformanceRequest,
    val services: InformationRequestRuntimeServices,
    val clock: MutableClock,
    val owner: RequestAccessContext,
    val reviewer: RequestAccessContext,
)
