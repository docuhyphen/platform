package com.docuhyphen.app.api.service.informationrequest.condition

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementDto
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath

object InformationRequestGoverningCondition {
    fun ruleKeyOf(binding: InformationRequestTemplateRequirementBinding?): String? =
        binding?.let { ruleKeyOf(it.requiredness, it.conditionalRuleKey) }

    fun ruleKeyOf(requirement: InformationRequestTemplateRequirementDto?): String? =
        requirement?.let { ruleKeyOf(it.requiredness, it.conditionalRuleKey) }

    fun ruleKeyOf(requiredness: InformationRequestRequiredness, conditionalRuleKey: String?): String? =
        conditionalRuleKey?.takeIf { requiredness == InformationRequestRequiredness.CONDITIONAL }

    fun byScope(
        evaluations: List<InformationRequestConditionEvaluationProjection>,
    ): Map<Pair<String, String>, InformationRequestConditionEvaluationProjection> =
        evaluations.associateBy { it.ruleKey to it.occurrencePath }

    fun hides(
        binding: InformationRequestTemplateRequirementBinding?,
        occurrencePath: String,
        conditionsByScope: Map<Pair<String, String>, InformationRequestConditionEvaluationProjection>,
    ): Boolean {
        val ruleKey = ruleKeyOf(binding) ?: return false
        val state = (conditionsByScope[ruleKey to occurrencePath]
            ?: conditionsByScope[ruleKey to InformationRequestOccurrencePath.ROOT])?.state
        return state != InformationRequestConditionEvaluationState.TRUE
    }
}
