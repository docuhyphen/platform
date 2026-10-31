package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessProgressContext
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestGoverningCondition
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestCorrectionCompletenessEvaluator @Inject constructor(
    private val lockService: InformationRequestSubmissionLockService,
) : InformationRequestCompletenessContributionEvaluator {
    override fun evaluate(context: InformationRequestCompletenessProgressContext): List<InformationRequestCompletenessContribution> {
        val returned = lockService.returnedItems(context.request.id)
            .filter { it.requirementType == InformationRequestRequirementType.FIELD }
        if (returned.isEmpty()) return emptyList()
        val requirementsById = context.requirements.associateBy { it.id }
        val bindingsById = context.bindings.associateBy { it.id }
        val currentRevisionByRequirement = context.activeResponses
            .associate { it.informationRequestRequirementId to it.responseRevision }
        return returned.mapNotNull { item ->
            val requirement = requirementsById[item.requirementId] ?: return@mapNotNull null
            if (hiddenByCondition(
                    requirement,
                    bindingsById[requirement.sourceTemplateBindingId],
                    context.conditionEvaluations
                )
            )
                return@mapNotNull null
            if (currentRevisionByRequirement[requirement.id] != item.submittedResponseRevision) return@mapNotNull null
            InformationRequestCompletenessContribution(
                itemKey = "correction:${context.request.id}:${requirement.id}",
                requirementId = requirement.id,
                occurrencePath = requirement.occurrencePath,
                state = InformationRequestCompletenessItemState.REJECTED,
                contributesToDenominator = true,
                contributesToNumerator = false,
            )
        }
    }

    private fun hiddenByCondition(
        requirement: InformationRequestRequirement,
        binding: InformationRequestTemplateRequirementBinding?,
        evaluations: List<InformationRequestConditionEvaluationProjection>,
    ): Boolean {
        val ruleKey = InformationRequestGoverningCondition.ruleKeyOf(binding) ?: return false
        val evaluation =
            evaluations.firstOrNull { it.ruleKey == ruleKey && it.occurrencePath == requirement.occurrencePath }
                ?: evaluations.firstOrNull { it.ruleKey == ruleKey && InformationRequestOccurrencePath.isRoot(it.occurrencePath) }
        return evaluation?.state != InformationRequestConditionEvaluationState.TRUE
    }
}
