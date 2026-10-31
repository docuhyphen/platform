package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestGoverningCondition
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestActiveResponseReader @Inject constructor(
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val responseStore: InformationRequestResponseStore,
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
) {
    fun activeResponses(requestId: UUID): List<InformationRequestResponse> {
        val activeOccurrencePaths = occurrenceRepository.findForRequest(requestId).map { it.occurrencePath }.toSet()
        val requirements = requirementRepository.findForRequest(requestId)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths) }
            .associateBy { it.id }
        val evaluations = conditionEvaluationService.evaluate(requestId)
        return responseStore.findCurrentForRequest(requestId).filter { response ->
            val requirement = requirements[response.informationRequestRequirementId] ?: return@filter false
            InformationRequestActiveResponseProjection.isActive(
                InformationRequestGoverningCondition.ruleKeyOf(bindingOf(requirement)),
                requirement.occurrencePath,
                evaluations,
                response.activeInResponse,
            )
        }
    }

    fun activeFieldProjection(
        requestId: UUID,
        requirement: InformationRequestRequirement,
        projection: SchemaAssignmentDto,
    ): SchemaAssignmentDto {
        val binding = bindingOf(requirement)
        val response = responseStore.findAllForRequest(requestId)
            .firstOrNull { it.informationRequestRequirementId == requirement.id }
        val active = InformationRequestActiveResponseProjection.isActive(
            InformationRequestGoverningCondition.ruleKeyOf(binding),
            requirement.occurrencePath,
            conditionEvaluationService.evaluate(requestId),
            response?.activeInResponse != false
        )
        return InformationRequestActiveResponseProjection.field(
            projection,
            binding?.collectedFieldDefinitionId.takeIf { active })
    }

    private fun bindingOf(requirement: InformationRequestRequirement): InformationRequestTemplateRequirementBinding? =
        bindingRepository.findById(requirement.sourceTemplateBindingId)
            ?: bindingRepository.findOrdered(requirement.sourceTemplateVersionId)
                .firstOrNull { it.id == requirement.sourceTemplateBindingId }
}
