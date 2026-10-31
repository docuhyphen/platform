package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestStructuredResponseValidationStage
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestActiveFieldValues
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestCanonicalFieldValueReader
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class InformationRequestStructuredResponseValidationContextLoader @Inject constructor(
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val responseStore: InformationRequestResponseStore,
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
    private val fieldValueReader: InformationRequestCanonicalFieldValueReader,
) {
    fun load(
        request: InformationRequest,
        stage: InformationRequestStructuredResponseValidationStage,
        patches: List<InformationRequestResponsePatch> = emptyList(),
    ): InformationRequestStructuredResponseValidationContext {
        val occurrences = occurrenceRepository.findForRequest(request.id)
        val activePaths = occurrences.map { it.occurrencePath }.toSet()
        val requirements = requirementRepository.findForRequest(request.id)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activePaths) }
        val bindingsById = bindingRepository.findOrdered(request.templateVersionId).associateBy { it.id }
        val snapshot = conditionEvaluationService.snapshot(request.id)
        val activeRequirements = requirements.filter { it.id !in snapshot.inactiveRequirementIds }
        val activeRequirementIds = activeRequirements.map { it.id }.toSet()
        return InformationRequestStructuredResponseValidationContext(
            stage = stage,
            request = request,
            requirementsById = activeRequirements.associateBy { it.id },
            bindingsById = bindingsById,
            occurrences = occurrences,
            responses = responseStore.findCurrentForRequest(request.id)
                .filter { it.informationRequestRequirementId in activeRequirementIds },
            fieldValuesByOccurrencePath = InformationRequestActiveFieldValues.withoutInactiveFields(
                fieldValueReader.valuesByOccurrencePath(
                    request.id,
                    activePaths + InformationRequestOccurrencePath.ROOT
                ),
                requirements,
                snapshot.inactiveRequirementIds,
                bindingsById,
            ),
            conditionEvaluations = snapshot.evaluations,
            patches = patches,
        )
    }
}
