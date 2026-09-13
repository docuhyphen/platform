package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaAssignment
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
import com.docuhyphen.app.api.service.fields.FieldOperator
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class InformationRequestConditionEvaluationServiceTest
{
    @Test
    fun `a field predicate resolves the request's current root field value by field definition`()
    {
        val fixture = Fixture()
        fixture.withConditionRules(
            InformationRequestTemplateConditionRuleDto(
                id = UUID.randomUUID(),
                ruleKey = "when-amount-is-large",
                expressionVersion = 1,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateDto(
                        id = UUID.randomUUID(),
                        fieldDefinitionId = fixture.fieldDefinitionId,
                        valueType = FieldValueType.DECIMAL,
                        operator = FieldOperator.GREATER_THAN_OR_EQUAL,
                        value = JsonPrimitive("10"),
                    ),
                ),
            ),
        )
        fixture.withRootFieldValue(BigDecimal("25.00"))

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(1, projections.size)
        assertEquals(InformationRequestConditionEvaluationState.TRUE, projections.single().state)
    }

    @Test
    fun `a disposition predicate defaults an unanswered requirement to NOT_ANSWERED rather than unknown`()
    {
        val fixture = Fixture()
        fixture.withConditionRules(
            InformationRequestTemplateConditionRuleDto(
                id = UUID.randomUUID(),
                ruleKey = "when-prior-answer-provided",
                expressionVersion = 1,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateDto(
                        id = UUID.randomUUID(),
                        sourceRequirementKey = fixture.templateRequirement.requirementKey,
                        operator = FieldOperator.EQUALS,
                        expectedDisposition = InformationRequestResponseDisposition.PROVIDED,
                    ),
                ),
            ),
        )
        whenever(fixture.responseStore.findCurrentForRequest(fixture.request.id)).thenReturn(emptyList())

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(InformationRequestConditionEvaluationState.FALSE, projections.single().state)
    }

    @Test
    fun `a version with no condition rules evaluates to no projections without reading response or field state`()
    {
        val fixture = Fixture()
        fixture.withConditionRules()

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(emptyList<InformationRequestConditionEvaluationProjection>(), projections)
        verifyNoInteractions(fixture.responseStore)
        verifyNoInteractions(fixture.fieldValueRepository)
    }

    @Test
    fun `a field predicate resolves each occurrence's own value set rather than the root set`()
    {
        val fixture = Fixture()
        fixture.withConditionRules(
            InformationRequestTemplateConditionRuleDto(
                id = UUID.randomUUID(),
                ruleKey = "when-amount-is-large",
                expressionVersion = 1,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateDto(
                        id = UUID.randomUUID(),
                        fieldDefinitionId = fixture.fieldDefinitionId,
                        valueType = FieldValueType.DECIMAL,
                        operator = FieldOperator.GREATER_THAN_OR_EQUAL,
                        value = JsonPrimitive("10"),
                    ),
                ),
            ),
        )
        fixture.withConditionalBindingAnchoredTo("items", "when-amount-is-large", "items[0]", "items[1]")
        fixture.withOccurrenceFieldValue("items[0]", BigDecimal("25.00"))
        fixture.withOccurrenceFieldValue("items[1]", BigDecimal("5.00"))

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(
            listOf(
                "items[0]" to InformationRequestConditionEvaluationState.TRUE,
                "items[1]" to InformationRequestConditionEvaluationState.FALSE,
            ),
            projections.map { it.occurrencePath to it.state },
        )
    }

    @Test
    fun `a disposition predicate resolves each occurrence's own response instead of collapsing them`()
    {
        val fixture = Fixture()
        fixture.withConditionRules(
            InformationRequestTemplateConditionRuleDto(
                id = UUID.randomUUID(),
                ruleKey = "when-prior-answer-provided",
                expressionVersion = 1,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateDto(
                        id = UUID.randomUUID(),
                        sourceRequirementKey = fixture.templateRequirement.requirementKey,
                        operator = FieldOperator.EQUALS,
                        expectedDisposition = InformationRequestResponseDisposition.PROVIDED,
                    ),
                ),
            ),
        )
        val answered = fixture.withAnchoredSourceRequirement("items[0]")
        fixture.withAnchoredSourceRequirement("items[1]")
        fixture.withConditionalBindingAnchoredTo("items", "when-prior-answer-provided", "items[0]", "items[1]")
        fixture.withResponse(answered, InformationRequestResponseDisposition.PROVIDED)

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(
            listOf(
                "items[0]" to InformationRequestConditionEvaluationState.TRUE,
                "items[1]" to InformationRequestConditionEvaluationState.FALSE,
            ),
            projections.map { it.occurrencePath to it.state },
        )
    }

    @Test
    fun `an occurrence scope still reads a root collected field the occurrence does not carry`()
    {
        val fixture = Fixture()
        fixture.withConditionRules(
            InformationRequestTemplateConditionRuleDto(
                id = UUID.randomUUID(),
                ruleKey = "when-amount-is-large",
                expressionVersion = 1,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateDto(
                        id = UUID.randomUUID(),
                        fieldDefinitionId = fixture.fieldDefinitionId,
                        valueType = FieldValueType.DECIMAL,
                        operator = FieldOperator.GREATER_THAN_OR_EQUAL,
                        value = JsonPrimitive("10"),
                    ),
                ),
            ),
        )
        fixture.withConditionalBindingAnchoredTo("items", "when-amount-is-large", "items[0]")
        fixture.withRootFieldValue(BigDecimal("25.00"))

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals("items[0]", projections.single().occurrencePath)
        assertEquals(InformationRequestConditionEvaluationState.TRUE, projections.single().state)
    }

    @Test
    fun `a nested occurrence scope reads ancestor occurrence fields without crossing sibling branches`()
    {
        val fixture = Fixture()
        fixture.withConditionRules(
            InformationRequestTemplateConditionRuleDto(
                id = UUID.randomUUID(),
                ruleKey = "when-parent-amount-is-large",
                expressionVersion = 1,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateDto(
                        id = UUID.randomUUID(),
                        fieldDefinitionId = fixture.fieldDefinitionId,
                        valueType = FieldValueType.DECIMAL,
                        operator = FieldOperator.GREATER_THAN_OR_EQUAL,
                        value = JsonPrimitive("10"),
                    ),
                ),
            ),
        )
        fixture.withConditionalBindingAnchoredTo(
            "entries",
            "when-parent-amount-is-large",
            "items[0]/entries[0]",
            "items[1]/entries[0]",
        )
        fixture.withOccurrenceFieldValue("items[0]", BigDecimal("25.00"))
        fixture.withOccurrenceFieldValue("items[1]", BigDecimal("5.00"))

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(
            listOf(
                "items[0]/entries[0]" to InformationRequestConditionEvaluationState.TRUE,
                "items[1]/entries[0]" to InformationRequestConditionEvaluationState.FALSE,
            ),
            projections.map { it.occurrencePath to it.state },
        )
    }

    @Test
    fun `a removed occurrence is excluded from a rule's scopes and evaluation falls back to root`()
    {
        val fixture = Fixture()
        fixture.withConditionRules(
            InformationRequestTemplateConditionRuleDto(
                id = UUID.randomUUID(),
                ruleKey = "when-amount-is-large",
                expressionVersion = 1,
                predicates = listOf(
                    InformationRequestTemplateConditionPredicateDto(
                        id = UUID.randomUUID(),
                        fieldDefinitionId = fixture.fieldDefinitionId,
                        valueType = FieldValueType.DECIMAL,
                        operator = FieldOperator.GREATER_THAN_OR_EQUAL,
                        value = JsonPrimitive("10"),
                    ),
                ),
            ),
        )
        fixture.withConditionalBindingAnchoredTo("items", "when-amount-is-large", "items[0]")
        fixture.withRootFieldValue(BigDecimal("25.00"))
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(InformationRequestOccurrencePath.ROOT, projections.single().occurrencePath)
        assertEquals(InformationRequestConditionEvaluationState.TRUE, projections.single().state)
    }

    private class Fixture
    {
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val request = InformationRequest().apply {
            templateVersionId = UUID.randomUUID()
        }
        val templateVersion = InformationRequestTemplateVersion().apply {
            id = request.templateVersionId
            templateDefinitionId = UUID.randomUUID()
        }
        val templateRequirement = InformationRequestTemplateRequirement().apply {
            templateDefinitionId = this@Fixture.templateVersion.templateDefinitionId
            requirementKey = "prior-answer"
        }
        val requirement = InformationRequestRequirement().apply {
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = templateRequirement.id
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "root"
        }
        val schemaAssignment = SchemaAssignment().apply {
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = request.id
            schemaVersionId = UUID.randomUUID()
        }
        val rootValueSet = FieldValueSet().apply {
            schemaAssignmentId = schemaAssignment.id
            setKind = FieldValueSetKind.ROOT
        }
        val fieldContract = FieldContract().apply {
            this.fieldDefinitionId = this@Fixture.fieldDefinitionId
            valueType = FieldValueType.DECIMAL
            label = "Amount"
        }

        val requestRepository = mock<InformationRequestRepository>()
        val templateVersionRepository = mock<InformationRequestTemplateVersionRepository>()
        val templateRequirementRepository = mock<InformationRequestTemplateRequirementRepository>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val responseStore = mock<InformationRequestResponseStore>()
        val schemaAssignmentRepository = mock<SchemaAssignmentRepository>()
        val fieldValueSetRepository = mock<FieldValueSetRepository>()
        val fieldValueRepository = mock<FieldValueRepository>()
        val fieldValueSelectionRepository = mock<FieldValueSelectionRepository>()
        val fieldContractRepository = mock<FieldContractRepository>()
        val templateProjectionLoader = mock<InformationRequestTemplateProjectionLoader>()

        val service = InformationRequestConditionEvaluationService(
            requestRepository = requestRepository,
            templateVersionRepository = templateVersionRepository,
            templateRequirementRepository = templateRequirementRepository,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            bindingRepository = bindingRepository,
            responseStore = responseStore,
            schemaAssignmentRepository = schemaAssignmentRepository,
            fieldValueSetRepository = fieldValueSetRepository,
            fieldValueRepository = fieldValueRepository,
            fieldValueSelectionRepository = fieldValueSelectionRepository,
            fieldContractRepository = fieldContractRepository,
            templateProjectionLoader = templateProjectionLoader,
            evaluator = InformationRequestConditionEvaluator(),
        )

        private val runtimeRequirements = mutableListOf(requirement)
        private val bindings = mutableListOf<InformationRequestTemplateRequirementBinding>()
        private val responses = mutableListOf<InformationRequestResponse>()

        init
        {
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(templateVersionRepository.findById(templateVersion.id)).thenReturn(templateVersion)
            whenever(templateRequirementRepository.findAllByDefinition(templateVersion.templateDefinitionId))
                .thenReturn(listOf(templateRequirement))
            whenever(requirementRepository.findForRequest(request.id)).thenReturn(runtimeRequirements)
            whenever(occurrenceRepository.findForRequest(request.id)).thenAnswer {
                runtimeRequirements.map { it.occurrencePath }
                    .filterNot { InformationRequestOccurrencePath.isRoot(it) }
                    .distinct()
                    .map { path ->
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            occurrencePath = path
                        }
                    }
            }
            whenever(bindingRepository.findOrdered(templateVersion.id)).thenReturn(bindings)
            whenever(schemaAssignmentRepository.findByResource(ResourceType.INFORMATION_REQUEST.name, request.id))
                .thenReturn(schemaAssignment)
            whenever(fieldValueSetRepository.findRoot(schemaAssignment.id)).thenReturn(rootValueSet)
            whenever(fieldValueRepository.findByValueSet(rootValueSet.id)).thenReturn(emptyList())
            whenever(responseStore.findCurrentForRequest(request.id)).thenReturn(responses)
        }

        fun withConditionRules(vararg rules: InformationRequestTemplateConditionRuleDto)
        {
            whenever(templateProjectionLoader.loadConditionRules(templateVersion.id)).thenReturn(rules.toList())
        }

        fun withRootFieldValue(amount: BigDecimal)
        {
            whenever(fieldValueRepository.findByValueSet(rootValueSet.id))
                .thenReturn(listOf(fieldValue(rootValueSet.id, amount)))
            whenever(fieldContractRepository.findByIds(listOf(fieldContract.id))).thenReturn(listOf(fieldContract))
        }

        fun withConditionalBindingAnchoredTo(groupKey: String, ruleKey: String, vararg occurrencePaths: String)
        {
            val binding = InformationRequestTemplateRequirementBinding().apply {
                templateVersionId = templateVersion.id
                templateDefinitionId = templateVersion.templateDefinitionId
                templateRequirementId = UUID.randomUUID()
                templateSectionId = UUID.randomUUID()
                displayOrder = bindings.size
                prompt = "Provide the conditional answer"
                conditionalRuleKey = ruleKey
                occurrenceAnchorKey = groupKey
            }
            bindings += binding
            occurrencePaths.forEach { path ->
                runtimeRequirements += InformationRequestRequirement().apply {
                    informationRequestId = request.id
                    sourceTemplateVersionId = request.templateVersionId
                    sourceTemplateRequirementId = binding.templateRequirementId
                    sourceTemplateBindingId = binding.id
                    occurrencePath = path
                }
            }
        }

        fun withAnchoredSourceRequirement(occurrencePath: String): InformationRequestRequirement
        {
            val anchored = InformationRequestRequirement().apply {
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = templateRequirement.id
                sourceTemplateBindingId = UUID.randomUUID()
                this.occurrencePath = occurrencePath
            }
            runtimeRequirements += anchored
            return anchored
        }

        fun withResponse(
            requirement: InformationRequestRequirement,
            disposition: InformationRequestResponseDisposition,
        )
        {
            responses += InformationRequestResponse().apply {
                informationRequestId = request.id
                informationRequestRequirementId = requirement.id
                occurrencePath = requirement.occurrencePath
                this.disposition = disposition
            }
        }

        fun withOccurrenceFieldValue(occurrencePath: String, amount: BigDecimal)
        {
            val occurrenceSet = FieldValueSet().apply {
                schemaAssignmentId = schemaAssignment.id
                setKind = FieldValueSetKind.OCCURRENCE
                this.occurrencePath = occurrencePath
            }
            whenever(fieldValueSetRepository.findOccurrence(schemaAssignment.id, occurrencePath))
                .thenReturn(occurrenceSet)
            whenever(fieldValueRepository.findByValueSet(occurrenceSet.id))
                .thenReturn(listOf(fieldValue(occurrenceSet.id, amount)))
            whenever(fieldContractRepository.findByIds(listOf(fieldContract.id))).thenReturn(listOf(fieldContract))
        }

        private fun fieldValue(valueSetId: UUID, amount: BigDecimal) = FieldValue().apply {
            fieldValueSetId = valueSetId
            schemaAssignmentId = schemaAssignment.id
            fieldContractId = fieldContract.id
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = request.id
            valueType = FieldValueType.DECIMAL
            numberValue = amount
        }
    }
}
