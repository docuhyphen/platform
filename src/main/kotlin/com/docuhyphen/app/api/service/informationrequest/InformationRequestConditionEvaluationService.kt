package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.InformationRequestTemplateDtoMapper
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.fields.CanonicalFieldValue
import com.docuhyphen.app.api.service.fields.CanonicalValueCodec
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestConditionEvaluationService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val responseStore: InformationRequestResponseStore,
    private val schemaAssignmentRepository: SchemaAssignmentRepository,
    private val fieldValueSetRepository: FieldValueSetRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val fieldValueSelectionRepository: FieldValueSelectionRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val templateProjectionLoader: InformationRequestTemplateProjectionLoader,
    private val evaluator: InformationRequestConditionEvaluator,
)
{
    fun evaluate(requestId: UUID): List<InformationRequestConditionEvaluationProjection>
    {
        val request = requestRepository.findById(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        val rules = templateProjectionLoader.loadConditionRules(request.templateVersionId)
        if (rules.isEmpty()) return emptyList()

        val activeOccurrencePaths = occurrenceRepository.findForRequest(requestId).map { it.occurrencePath }.toSet()
        val requirements = requirementRepository.findForRequest(requestId)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths) }
        val scopesByRuleKey = scopesByRuleKey(request.templateVersionId, requirements)
        val scopes = scopesByRuleKey.values.flatten().toSet() + InformationRequestOccurrencePath.ROOT
        val fieldValuesByScope = fieldValuesByScope(requestId, scopes)
        val dispositionsByScope = dispositionsByScope(requestId, request.templateVersionId, requirements, scopes)

        return rules.flatMap { rule ->
            val evaluationRequest = InformationRequestTemplateDtoMapper.toEvaluationRequest(rule)
            val ruleScopes = scopesByRuleKey[rule.ruleKey] ?: listOf(InformationRequestOccurrencePath.ROOT)
            ruleScopes.map { scope ->
                evaluator.evaluate(
                    rules = listOf(evaluationRequest),
                    fieldValuesByDefinitionId = fieldValuesByScope[scope].orEmpty(),
                    dispositionsByRequirementKey = dispositionsByScope[scope].orEmpty(),
                    occurrencePath = scope,
                ).single()
            }
        }
    }

    /**
     * The occurrence paths each rule has to be answered for, being the paths of the runtime
     * Requirements that name it. A rule no Requirement names is answered once at the root.
     */
    private fun scopesByRuleKey(
        templateVersionId: UUID,
        requirements: List<InformationRequestRequirement>,
    ): Map<String, List<String>>
    {
        val ruleKeyByBindingId = bindingRepository.findOrdered(templateVersionId)
            .mapNotNull { binding -> binding.conditionalRuleKey?.let { binding.id to it } }
            .toMap()
        if (ruleKeyByBindingId.isEmpty()) return emptyMap()
        return requirements
            .mapNotNull { requirement ->
                ruleKeyByBindingId[requirement.sourceTemplateBindingId]?.let { it to requirement.occurrencePath }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, paths) -> paths.distinct().sorted() }
    }

    /**
     * Values visible from each occurrence, being that occurrence's own Value Set laid over the root
     * set so a predicate on a root-collected Field still resolves while inside a repeated group.
     */
    private fun fieldValuesByScope(
        requestId: UUID,
        scopes: Set<String>,
    ): Map<String, Map<UUID, CanonicalFieldValue?>>
    {
        val assignment = schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, requestId)
            ?: return emptyMap()
        val rootValues = fieldValueSetRepository.findRoot(assignment.id)
            ?.let { canonicalValues(it.id) }
            .orEmpty()
        return scopes.associateWith { scope ->
            if (InformationRequestOccurrencePath.isRoot(scope))
                rootValues
            else
                occurrenceScopePaths(scope).fold(rootValues) { values, occurrencePath ->
                    values + fieldValueSetRepository.findOccurrence(assignment.id, occurrencePath)
                        ?.let { canonicalValues(it.id) }
                        .orEmpty()
                }
        }
    }

    private fun canonicalValues(valueSetId: UUID): Map<UUID, CanonicalFieldValue?>
    {
        val values = fieldValueRepository.findByValueSet(valueSetId)
        if (values.isEmpty()) return emptyMap()
        val fieldDefinitionIdByContractId = fieldContractRepository
            .findByIds(values.map { it.fieldContractId }.distinct())
            .associate { it.id to it.fieldDefinitionId }
        return values.mapNotNull { value ->
            fieldDefinitionIdByContractId[value.fieldContractId]?.let { fieldDefinitionId ->
                fieldDefinitionId to CanonicalValueCodec.toCanonical(value, selectionCodes(value))
            }
        }.toMap()
    }

    /**
     * Dispositions readable from each occurrence. A source Requirement answered once per occurrence
     * resolves to that occurrence's own answer, a root-collected source resolves to its single
     * answer, and a source that resolves to several answers from this occurrence is left out so the
     * predicate stays unknown instead of picking one arbitrarily.
     */
    private fun dispositionsByScope(
        requestId: UUID,
        templateVersionId: UUID,
        requirements: List<InformationRequestRequirement>,
        scopes: Set<String>,
    ): Map<String, Map<String, InformationRequestResponseDisposition?>>
    {
        if (requirements.isEmpty()) return emptyMap()
        val version = templateVersionRepository.findById(templateVersionId) ?: return emptyMap()
        val requirementKeyById = templateRequirementRepository
            .findAllByDefinition(version.templateDefinitionId)
            .associate { it.id to it.requirementKey }
        val dispositionByRequirementId = responseStore.findCurrentForRequest(requestId)
            .associate { it.informationRequestRequirementId to it.disposition }
        val answersByRequirementKey = requirements
            .mapNotNull { requirement ->
                requirementKeyById[requirement.sourceTemplateRequirementId]?.let { key ->
                    key to (
                        requirement.occurrencePath to (
                            dispositionByRequirementId[requirement.id]
                                ?: InformationRequestResponseDisposition.NOT_ANSWERED
                            )
                        )
                }
            }
            .groupBy({ it.first }, { it.second })

        return scopes.associateWith { scope ->
            answersByRequirementKey.mapNotNull { (requirementKey, answers) ->
                val atRoot = answers.filter { InformationRequestOccurrencePath.isRoot(it.first) }
                val resolved = resolveScopedDisposition(scope, answers, atRoot)
                    ?: return@mapNotNull null
                requirementKey to resolved
            }.toMap()
        }
    }

    private fun resolveScopedDisposition(
        scope: String,
        answers: List<Pair<String, InformationRequestResponseDisposition>>,
        atRoot: List<Pair<String, InformationRequestResponseDisposition>>,
    ): InformationRequestResponseDisposition?
    {
        occurrenceScopePaths(scope).asReversed().forEach { occurrencePath ->
            val atOccurrence = answers.filter { it.first == occurrencePath }
            if (atOccurrence.size == 1) return atOccurrence.single().second
            if (atOccurrence.size > 1) return null
        }
        return if (atRoot.size == 1) atRoot.single().second else null
    }

    private fun occurrenceScopePaths(scope: String): List<String>
    {
        if (InformationRequestOccurrencePath.isRoot(scope)) return emptyList()
        val segments = scope.split("/").filter { it.isNotBlank() }
        return segments.indices.map { index ->
            segments.take(index + 1).joinToString("/")
        }
    }

    private fun selectionCodes(value: FieldValue): List<String> =
        if (value.valueType == FieldValueType.SINGLE_SELECT || value.valueType == FieldValueType.MULTI_SELECT)
            fieldValueSelectionRepository.findByValue(value.id).map { it.optionCode }
        else
            emptyList()
}
