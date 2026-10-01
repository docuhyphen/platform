package com.docuhyphen.app.api.model.informationrequest.condition

import com.docuhyphen.app.api.model.entity.InformationRequestConditionHiddenDataPolicy
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import java.util.*

enum class InformationRequestConditionEvaluationState
{
    TRUE,
    FALSE,
    UNKNOWN,
}

data class InformationRequestConditionEvaluationProjection(
    val ruleKey: String,
    val expressionVersion: Int,
    val state: InformationRequestConditionEvaluationState,
    val hiddenDataPolicy: InformationRequestConditionHiddenDataPolicy =
        InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY,
    val sourceRequirementKeys: Set<String>,
    val fieldDefinitionIds: Set<UUID>,
    val occurrencePath: String = InformationRequestOccurrencePath.ROOT,
)
