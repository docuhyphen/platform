package com.docuhyphen.app.api.service.informationrequest.response.model

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse

internal data class HiddenConditionalResponseFixture(
    val requirement: InformationRequestRequirement,
    val response: InformationRequestResponse,
)
