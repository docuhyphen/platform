package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.informationrequest.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestStandingReason
import kotlinx.serialization.Serializable

@Serializable
data class InformationRequestExecutionStandingDto(
    val kind: InformationRequestExecutionStandingKind,
    val reason: InformationRequestStandingReason? = null,
)
