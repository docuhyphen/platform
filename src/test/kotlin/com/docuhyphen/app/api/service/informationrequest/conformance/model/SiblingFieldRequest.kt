package com.docuhyphen.app.api.service.informationrequest.conformance.model

import java.util.*

internal data class SiblingFieldRequest(
    val requestId: UUID,
    val fieldRequirementId: UUID,
    val documentRequirementId: UUID,
    val attestationRequirementId: UUID,
)
