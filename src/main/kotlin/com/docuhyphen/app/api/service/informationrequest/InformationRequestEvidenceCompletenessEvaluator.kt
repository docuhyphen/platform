package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessProgressContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestEvidenceCompletenessEvaluator @Inject constructor(
    private val evaluationService: InformationRequestEvidenceEvaluationService,
) : InformationRequestCompletenessContributionEvaluator
{
    override fun evaluate(context: InformationRequestCompletenessProgressContext): List<InformationRequestCompletenessContribution>
    {
        val bindingsById = context.bindings.associateBy { it.id }
        val responsesByRequirement = context.activeResponses.associateBy { it.informationRequestRequirementId }
        val conditionByScope = context.conditionEvaluations.associateBy { it.ruleKey to it.occurrencePath }

        return context.requirements.mapNotNull { requirement ->
            val binding = bindingsById[requirement.sourceTemplateBindingId] ?: return@mapNotNull null
            val disposition = responsesByRequirement[requirement.id]?.disposition
            val evaluation = evaluationService.evaluate(requirement, disposition) ?: return@mapNotNull null
            val complete = completes(evaluation.state, disposition)
            val conditionState = binding.conditionalRuleKey?.let { ruleKey ->
                (conditionByScope[ruleKey to requirement.occurrencePath]
                    ?: conditionByScope[ruleKey to InformationRequestOccurrencePath.ROOT])?.state
            }
            val state = when
            {
                binding.requiredness == InformationRequestRequiredness.CONDITIONAL &&
                    conditionState != InformationRequestConditionEvaluationState.TRUE ->
                    InformationRequestCompletenessItemState.HIDDEN
                binding.requiredness == InformationRequestRequiredness.OPTIONAL && !complete ->
                    InformationRequestCompletenessItemState.OPTIONAL_UNANSWERED
                complete -> InformationRequestCompletenessItemState.COMPLETE
                else -> InformationRequestCompletenessItemState.INCOMPLETE
            }
            InformationRequestCompletenessContribution(
                itemKey = "evidence:${context.request.id}:${requirement.id}",
                requirementId = requirement.id,
                occurrencePath = requirement.occurrencePath,
                state = state,
                contributesToDenominator = state == InformationRequestCompletenessItemState.COMPLETE ||
                    state == InformationRequestCompletenessItemState.INCOMPLETE,
                contributesToNumerator = state == InformationRequestCompletenessItemState.COMPLETE,
            )
        }
    }

    private fun completes(
        evidence: InformationRequestEvidenceRequirementState,
        disposition: InformationRequestResponseDisposition?,
    ): Boolean =
        when (disposition)
        {
            null,
            InformationRequestResponseDisposition.NOT_ANSWERED,
            InformationRequestResponseDisposition.PROVIDED,
            InformationRequestResponseDisposition.WAIVED -> evidence.completesWork
            InformationRequestResponseDisposition.PARTIALLY_PROVIDED ->
                evidence.completesWork || evidence == InformationRequestEvidenceRequirementState.INCOMPLETE
            InformationRequestResponseDisposition.NOT_APPLICABLE,
            InformationRequestResponseDisposition.UNAVAILABLE,
            InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
            InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE -> true
        }
}
