package com.docuhyphen.app.api.service.informationrequest.model

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationProjection
import java.util.UUID

enum class InformationRequestCompletenessItemState
{
    COMPLETE,
    INCOMPLETE,
    OPTIONAL_UNANSWERED,
    HIDDEN,
    REJECTED,
}

data class InformationRequestCompletenessContribution(
    val itemKey: String,
    val requirementId: UUID? = null,
    val occurrencePath: String? = null,
    val state: InformationRequestCompletenessItemState,
    val contributesToDenominator: Boolean,
    val contributesToNumerator: Boolean,
)

data class InformationRequestProgressProjection(
    val completedCount: Int,
    val totalCount: Int,
    val percentComplete: Int,
    val items: List<InformationRequestCompletenessContribution>,
)

data class InformationRequestCompletenessProgressContext(
    val request: InformationRequest,
    val requirements: List<InformationRequestRequirement>,
    val bindings: List<InformationRequestTemplateRequirementBinding>,
    val activeResponses: List<InformationRequestResponse>,
    val conditionEvaluations: List<InformationRequestConditionEvaluationProjection>,
)
