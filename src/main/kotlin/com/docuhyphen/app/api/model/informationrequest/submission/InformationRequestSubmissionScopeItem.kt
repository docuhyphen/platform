package com.docuhyphen.app.api.model.informationrequest.submission

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.fields.FieldValueRevisionValue
import java.util.*

data class InformationRequestSubmissionScopeItem(
    val requirementId: UUID,
    val templateBindingId: UUID,
    val requirementKey: String,
    val requirementType: InformationRequestRequirementType,
    val prompt: String,
    val occurrencePath: String,
    val disposition: InformationRequestResponseDisposition,
    val narrative: String?,
    val fieldValue: FieldValueRevisionValue?,
    val evidenceFileCount: Int,
)