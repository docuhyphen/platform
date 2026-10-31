package com.docuhyphen.app.api.service.informationrequest.condition

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestConditionVisibility @Inject constructor(
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
) {
    fun hiddenAmong(
        request: InformationRequest,
        requirements: Collection<InformationRequestRequirement>,
    ): List<InformationRequestRequirement> {
        if (requirements.isEmpty()) return emptyList()
        val bindingsById = bindingRepository.findOrdered(request.templateVersionId).associateBy { it.id }
        val governed = requirements.filter {
            InformationRequestGoverningCondition.ruleKeyOf(bindingsById[it.sourceTemplateBindingId]) != null
        }
        if (governed.isEmpty()) return emptyList()
        val conditions = InformationRequestGoverningCondition.byScope(conditionEvaluationService.evaluate(request.id))
        return governed.filter {
            InformationRequestGoverningCondition.hides(
                bindingsById[it.sourceTemplateBindingId],
                it.occurrencePath,
                conditions
            )
        }
    }

    fun shows(request: InformationRequest, requirement: InformationRequestRequirement): Boolean =
        hiddenAmong(request, listOf(requirement)).isEmpty()

    fun requireShown(request: InformationRequest, requirements: Collection<InformationRequestRequirement>) {
        val hidden = hiddenAmong(request, requirements)
        if (hidden.isEmpty()) return
        throw InformationRequestItemRefusalException(
            reasonCode = InformationRequestErrorCatalog.REQUIREMENT_HIDDEN_BY_CONDITION,
            message = "A Requirement this change addresses is not asked because of an earlier answer",
            problems = hidden.map { requirement ->
                InformationRequestItemProblem(
                    code = InformationRequestErrorCatalog.REQUIREMENT_HIDDEN_BY_CONDITION,
                    message = "This Requirement is not asked because of an earlier answer",
                    requirementId = requirement.id,
                    occurrencePath = requirement.occurrencePath,
                )
            },
        )
    }
}
