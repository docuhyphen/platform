package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.service.fields.CanonicalValueCodec
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessProgressContext
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestProgressProjection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Any
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import java.util.UUID

interface InformationRequestCompletenessContributionEvaluator
{
    fun evaluate(context: InformationRequestCompletenessProgressContext): List<InformationRequestCompletenessContribution>
}

@ApplicationScoped
class InformationRequestCompletenessProgressService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val fieldValueSelectionRepository: FieldValueSelectionRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val responseStore: InformationRequestResponseStore,
    private val conditionEvaluationService: InformationRequestConditionEvaluationService,
    @Any private val contributionEvaluators: Instance<InformationRequestCompletenessContributionEvaluator>,
)
{
    fun evaluate(requestId: UUID): InformationRequestProgressProjection
    {
        val request = requestRepository.findById(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        val activeOccurrencePaths = occurrenceRepository.findForRequest(requestId).map { it.occurrencePath }.toSet()
        val requirements = requirementRepository.findForRequest(requestId)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths) }
        val bindings = bindingRepository.findOrdered(request.templateVersionId)
        val activeResponses = responseStore.findCurrentForRequest(requestId)
        val conditionEvaluations = conditionEvaluationService.evaluate(requestId)
        val context = InformationRequestCompletenessProgressContext(
            request = request,
            requirements = requirements,
            bindings = bindings,
            activeResponses = activeResponses,
            conditionEvaluations = conditionEvaluations,
        )
        val extensionContributions = contributionEvaluators.toList().flatMap { it.evaluate(context) }
        val extensionRequirementIds = extensionContributions.mapNotNull { it.requirementId }.toSet()
        val structuredContributions = structuredContributions(
            request = request,
            requirements = requirements.filter { it.id !in extensionRequirementIds },
            bindings = bindings,
            activeResponses = activeResponses,
            conditionEvaluations = conditionEvaluations,
        )
        val items = structuredContributions + extensionContributions
        val total = items.count { it.contributesToDenominator }
        val completed = items.count { it.contributesToNumerator }
        return InformationRequestProgressProjection(
            completedCount = completed,
            totalCount = total,
            percentComplete = percent(completed, total),
            items = items,
        )
    }

    private fun structuredContributions(
        request: InformationRequest,
        requirements: List<InformationRequestRequirement>,
        bindings: List<InformationRequestTemplateRequirementBinding>,
        activeResponses: List<InformationRequestResponse>,
        conditionEvaluations: List<InformationRequestConditionEvaluationProjection>,
    ): List<InformationRequestCompletenessContribution>
    {
        val bindingsById = bindings.associateBy { it.id }
        val responseByRequirementId = activeResponses.associateBy { it.informationRequestRequirementId }
        val conditionByScope = conditionEvaluations.associateBy { it.ruleKey to it.occurrencePath }
        val collectedFieldAnswers = currentCollectedFieldAnswers(bindings, activeResponses)
        return requirements.map { requirement ->
            val binding = bindingsById[requirement.sourceTemplateBindingId]
                ?: throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.STATE_INVALID,
                    "Information Request Requirement has no Template binding",
                )
            contributionFor(
                request = request,
                requirement = requirement,
                binding = binding,
                response = responseByRequirementId[requirement.id],
                conditionByScope = conditionByScope,
                collectedFieldAnswers = collectedFieldAnswers,
            )
        }
    }

    private fun contributionFor(
        request: InformationRequest,
        requirement: InformationRequestRequirement,
        binding: InformationRequestTemplateRequirementBinding,
        response: InformationRequestResponse?,
        conditionByScope: Map<Pair<String, String>, InformationRequestConditionEvaluationProjection>,
        collectedFieldAnswers: Map<Pair<UUID, UUID>, Boolean>,
    ): InformationRequestCompletenessContribution
    {
        val answerComplete = response.hasStructuredResponse(binding, collectedFieldAnswers)
        val conditionState = binding.conditionalRuleKey?.let { ruleKey ->
            val atOccurrence = conditionByScope[ruleKey to requirement.occurrencePath]
            val atRoot = conditionByScope[ruleKey to InformationRequestOccurrencePath.ROOT]
            (atOccurrence ?: atRoot)?.state
        }
        val state = when
        {
            binding.requiredness == InformationRequestRequiredness.CONDITIONAL &&
                conditionState != InformationRequestConditionEvaluationState.TRUE ->
                InformationRequestCompletenessItemState.HIDDEN
            binding.requiredness == InformationRequestRequiredness.OPTIONAL && !answerComplete ->
                InformationRequestCompletenessItemState.OPTIONAL_UNANSWERED
            answerComplete -> InformationRequestCompletenessItemState.COMPLETE
            else -> InformationRequestCompletenessItemState.INCOMPLETE
        }
        return InformationRequestCompletenessContribution(
            itemKey = "structured:${request.id}:${requirement.id}",
            requirementId = requirement.id,
            occurrencePath = requirement.occurrencePath,
            state = state,
            contributesToDenominator = state == InformationRequestCompletenessItemState.COMPLETE ||
                state == InformationRequestCompletenessItemState.INCOMPLETE,
            contributesToNumerator = state == InformationRequestCompletenessItemState.COMPLETE,
        )
    }

    private fun currentCollectedFieldAnswers(
        bindings: List<InformationRequestTemplateRequirementBinding>,
        activeResponses: List<InformationRequestResponse>,
    ): Map<Pair<UUID, UUID>, Boolean>
    {
        val collectedFieldDefinitionIds = bindings.mapNotNull { it.collectedFieldDefinitionId }.toSet()
        val valueSetIds = activeResponses.mapNotNull { it.fieldValueSetId }.distinct()
        if (collectedFieldDefinitionIds.isEmpty() || valueSetIds.isEmpty()) return emptyMap()

        val values = valueSetIds.flatMap { fieldValueRepository.findByValueSet(it) }
        val fieldDefinitionIdByContractId = fieldContractRepository.findByIds(values.map { it.fieldContractId }.distinct())
            .associate { it.id to it.fieldDefinitionId }
        return values.mapNotNull { value ->
            val fieldDefinitionId = fieldDefinitionIdByContractId[value.fieldContractId] ?: return@mapNotNull null
            if (fieldDefinitionId !in collectedFieldDefinitionIds) return@mapNotNull null
            val selectionCodes = fieldValueSelectionRepository.findByValue(value.id).map { it.optionCode }
            (value.fieldValueSetId to fieldDefinitionId) to !CanonicalValueCodec.isEmpty(value, selectionCodes)
        }.toMap()
    }

    private fun InformationRequestResponse?.hasStructuredResponse(
        binding: InformationRequestTemplateRequirementBinding,
        collectedFieldAnswers: Map<Pair<UUID, UUID>, Boolean>,
    ): Boolean
    {
        if (this == null) return false
        val collectedFieldDefinitionId = binding.collectedFieldDefinitionId
        if (collectedFieldDefinitionId != null)
        {
            val hasCollectedAnswer = fieldValueSetId?.let {
                collectedFieldAnswers[it to collectedFieldDefinitionId]
            } == true
            return hasCollectedAnswer || disposition.isCompletingException()
        }
        return disposition != InformationRequestResponseDisposition.NOT_ANSWERED
    }

    private fun InformationRequestResponseDisposition.isCompletingException(): Boolean =
        this != InformationRequestResponseDisposition.NOT_ANSWERED &&
            this != InformationRequestResponseDisposition.PROVIDED

    private fun percent(completed: Int, total: Int): Int =
        if (total == 0) 100 else completed * 100 / total
}
