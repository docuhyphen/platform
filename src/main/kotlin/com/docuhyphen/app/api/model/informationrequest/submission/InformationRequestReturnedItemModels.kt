package com.docuhyphen.app.api.model.informationrequest.submission

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import java.util.*

data class InformationRequestReturnedItem(
    val requirementId: UUID,
    val requirementType: InformationRequestRequirementType,
    val submittedResponseRevision: Long?,
)
