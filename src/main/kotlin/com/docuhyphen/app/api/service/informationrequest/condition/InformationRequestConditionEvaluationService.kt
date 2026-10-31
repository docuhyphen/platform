package com.docuhyphen.app.api.service.informationrequest.condition

import com.docuhyphen.app.api.model.InformationRequestTemplateDtoMapper
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.CanonicalFieldValue
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionSnapshot
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestDocumentFileCompletion
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStore
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateProjectionLoader
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestConditionEvaluationService @Inject constructor(
    private val requestRepository: InformationRequestRepository,
    private val templateVersionRepository: InformationRequestTemplateVersionRepository,
    private val templateRequirementRepository: InformationRequestTemplateRequirementRepository,
    private val requirementRepository: InformationRequestRequirementRepository,
    private val occurrenceRepository: InformationRequestGroupOccurrenceRepository,
    private val bindingRepository: InformationRequestTemplateRequirementBindingRepository,
    private val responseStore: InformationRequestResponseStore,
    private val fieldValueReader: InformationRequestCanonicalFieldValueReader,
    private val templateProjectionLoader: InformationRequestTemplateProjectionLoader,
    private val evaluator: InformationRequestConditionEvaluator,
    private val documentFiles: InformationRequestDocumentFileCompletion,
)
{
    fun evaluate(requestId: UUID): List<InformationRequestConditionEvaluationProjection> =
        snapshot(requestId).evaluations

    fun snapshot(requestId: UUID): InformationRequestConditionSnapshot
    {
        val request = requestRepository.findById(requestId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Information Request not found",
            )
        val rules = templateProjectionLoader.loadConditionRules(request.templateVersionId)
        if (rules.isEmpty()) return InformationRequestConditionSnapshot(emptyList())

        val activeOccurrencePaths = occurrenceRepository.findForRequest(requestId).map { it.occurrencePath }.toSet()
        val requirements = requirementRepository.findForRequest(requestId)
            .filter { InformationRequestOccurrencePath.isActiveOccurrence(it.occurrencePath, activeOccurrencePaths) }
        val bindingsById = bindingRepository.findOrdered(request.templateVersionId).associateBy { it.id }
        val governingRules = requirements
            .mapNotNull { requirement ->
                InformationRequestGoverningCondition.ruleKeyOf(bindingsById[requirement.sourceTemplateBindingId])
                    ?.let { requirement.id to it }
            }
            .toMap()
        val scopesByRuleKey = requirements
            .filter { it.id in governingRules }
            .groupBy({ governingRules.getValue(it.id) }, { it.occurrencePath })
            .mapValues { (_, paths) -> paths.distinct().sorted() }
        val scopes = scopesByRuleKey.values.flatten().toSet() + InformationRequestOccurrencePath.ROOT
        val storedValues = fieldValueReader.valuesByOccurrencePath(
            requestId,
            scopes.flatMap { occurrenceScopePaths(it) }.toSet() + InformationRequestOccurrencePath.ROOT,
        )
        val responsesByRequirement = responseStore.findAllForRequest(requestId)
            .associateBy { it.informationRequestRequirementId }
        val hiddenEnvelopes = requirements
            .filter { it.id in governingRules && responsesByRequirement[it.id]?.activeInResponse == false }
            .map { it.id }
            .toSet()
        val templateRequirementsById = templateRequirementsOf(request.templateVersionId)
        val requirementKeyById =
            templateRequirementsById.mapValues { (_, templateRequirement) -> templateRequirement.requirementKey }
        val documentsFiledComplete = documentsCompletedByOwnFiles(rules, requirements, templateRequirementsById)
        val round = { inactive: Set<UUID> ->
            evaluateRound(
                rules = rules,
                scopesByRuleKey = scopesByRuleKey,
                scopes = scopes,
                fieldValuesByOccurrencePath = InformationRequestActiveFieldValues.withoutInactiveFields(
                    storedValues,
                    requirements,
                    inactive,
                    bindingsById,
                ),
                dispositionsByScope = dispositionsByScope(
                    requirements,
                    requirementKeyById,
                    responsesByRequirement,
                    inactive,
                    scopes,
                    documentsFiledComplete,
                ),
            )
        }

        var inactive = hiddenEnvelopes
        var evaluations = round(inactive)
        repeat(governingRules.size + 1) {
            val next = hiddenEnvelopes + requirements
                .filter { requirement ->
                    governingRules[requirement.id]?.let { ruleKey ->
                        stateAt(
                            evaluations,
                            ruleKey,
                            requirement.occurrencePath
                        ) != InformationRequestConditionEvaluationState.TRUE
                    } == true
                }
                .map { it.id }
            if (next == inactive) return InformationRequestConditionSnapshot(evaluations, inactive)
            inactive = next
            evaluations = round(inactive)
        }
        return InformationRequestConditionSnapshot(evaluations, inactive)
    }

    private fun evaluateRound(
        rules: List<InformationRequestTemplateConditionRuleDto>,
        scopesByRuleKey: Map<String, List<String>>,
        scopes: Set<String>,
        fieldValuesByOccurrencePath: Map<String, Map<UUID, CanonicalFieldValue?>>,
        dispositionsByScope: Map<String, Map<String, InformationRequestResponseDisposition?>>,
    ): List<InformationRequestConditionEvaluationProjection> {
        val fieldValuesByScope = scopes.associateWith { scope -> overlay(scope, fieldValuesByOccurrencePath) }
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

    private fun stateAt(
        evaluations: List<InformationRequestConditionEvaluationProjection>,
        ruleKey: String,
        occurrencePath: String,
    ): InformationRequestConditionEvaluationState? =
        (evaluations.firstOrNull { it.ruleKey == ruleKey && it.occurrencePath == occurrencePath }
            ?: evaluations.firstOrNull { it.ruleKey == ruleKey && InformationRequestOccurrencePath.isRoot(it.occurrencePath) })
            ?.state

    private fun overlay(
        scope: String,
        valuesByOccurrencePath: Map<String, Map<UUID, CanonicalFieldValue?>>,
    ): Map<UUID, CanonicalFieldValue?> =
        occurrenceScopePaths(scope).fold(valuesByOccurrencePath[InformationRequestOccurrencePath.ROOT].orEmpty()) { values, path ->
            values + valuesByOccurrencePath[path].orEmpty()
        }

    private fun templateRequirementsOf(templateVersionId: UUID): Map<UUID, InformationRequestTemplateRequirement> {
        val version = templateVersionRepository.findById(templateVersionId) ?: return emptyMap()
        return templateRequirementRepository
            .findAllByDefinition(version.templateDefinitionId)
            .associateBy { it.id }
    }

    private fun documentsCompletedByOwnFiles(
        rules: List<InformationRequestTemplateConditionRuleDto>,
        requirements: List<InformationRequestRequirement>,
        templateRequirementsById: Map<UUID, InformationRequestTemplateRequirement>,
    ): Set<UUID>
    {
        val sourceKeys = rules.flatMap { it.predicates }.mapNotNull { it.sourceRequirementKey }.toSet()
        if (sourceKeys.isEmpty()) return emptySet()
        return requirements
            .filter { requirement ->
                templateRequirementsById[requirement.sourceTemplateRequirementId]?.let { templateRequirement ->
                    templateRequirement.requirementKey in sourceKeys &&
                            templateRequirement.requirementType == InformationRequestRequirementType.DOCUMENT
                } == true
            }
            .filter(documentFiles::completedByOwnFiles)
            .map { it.id }
            .toSet()
    }

    private fun dispositionsByScope(
        requirements: List<InformationRequestRequirement>,
        requirementKeyById: Map<UUID, String>,
        responsesByRequirement: Map<UUID, InformationRequestResponse>,
        inactive: Set<UUID>,
        scopes: Set<String>,
        documentsFiledComplete: Set<UUID>,
    ): Map<String, Map<String, InformationRequestResponseDisposition?>>
    {
        if (requirements.isEmpty()) return emptyMap()
        val answersByRequirementKey = requirements
            .mapNotNull { requirement ->
                requirementKeyById[requirement.sourceTemplateRequirementId]?.let { key ->
                    val stated = responsesByRequirement[requirement.id]
                        ?.takeIf { it.activeInResponse && requirement.id !in inactive }
                        ?.disposition
                    val disposition = when {
                        stated != null && stated != InformationRequestResponseDisposition.NOT_ANSWERED -> stated
                        requirement.id !in inactive && requirement.id in documentsFiledComplete ->
                            InformationRequestResponseDisposition.PROVIDED

                        else -> InformationRequestResponseDisposition.NOT_ANSWERED
                    }
                    key to (requirement.occurrencePath to disposition)
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
}
