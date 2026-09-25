package com.docuhyphen.app.api.model.informationrequest

import java.util.UUID

data class InformationRequestSupportingEvidenceLinkTarget(
    val templateLinkId: UUID,
    val supportedRequirementId: UUID,
    val supportingRequirementId: UUID,
)
