package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessProgressContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestAttestationCompletenessEvaluator @Inject constructor(
    private val attestationEvaluationService: InformationRequestAttestationEvaluationService,
) : InformationRequestCompletenessContributionEvaluator
{
    override fun evaluate(context: InformationRequestCompletenessProgressContext): List<InformationRequestCompletenessContribution>
    {
        val evaluations = attestationEvaluationService.evaluate(context.request)
        if (evaluations.isEmpty()) return emptyList()
        val bindingsById = context.bindings.associateBy { it.id }
        val responsesByRequirement = context.activeResponses.associateBy { it.informationRequestRequirementId }
        val conditionByScope = context.conditionEvaluations.associateBy { it.ruleKey to it.occurrencePath }

        return context.requirements.mapNotNull { requirement ->
            val evaluation = evaluations[requirement.id] ?: return@mapNotNull null
            val binding = bindingsById[requirement.sourceTemplateBindingId] ?: return@mapNotNull null
            val disposition = responsesByRequirement[requirement.id]?.disposition
            val complete = evaluation.evaluation.state == InformationRequestAttestationState.SATISFIED ||
                disposition.isCompletingException()
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
                itemKey = "attestation:${context.request.id}:${requirement.id}",
                requirementId = requirement.id,
                occurrencePath = requirement.occurrencePath,
                state = state,
                contributesToDenominator = state == InformationRequestCompletenessItemState.COMPLETE ||
                    state == InformationRequestCompletenessItemState.INCOMPLETE,
                contributesToNumerator = state == InformationRequestCompletenessItemState.COMPLETE,
            )
        }
    }

    private fun InformationRequestResponseDisposition?.isCompletingException(): Boolean =
        this != null &&
            this != InformationRequestResponseDisposition.NOT_ANSWERED &&
            this != InformationRequestResponseDisposition.PROVIDED
}
