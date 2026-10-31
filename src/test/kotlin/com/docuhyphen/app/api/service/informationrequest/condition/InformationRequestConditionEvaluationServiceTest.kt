package com.docuhyphen.app.api.service.informationrequest.condition

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionPredicateDto
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateConditionRuleDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOperator
import com.docuhyphen.app.api.model.fields.FieldValueSetRef
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.occurrence.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestDocumentFileCompletion
import com.docuhyphen.app.api.service.informationrequest.occurrence.InformationRequestOccurrencePath
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestResponseStore
import com.docuhyphen.app.api.service.informationrequest.template.InformationRequestTemplateProjectionLoader
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.*

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
        verify(fixture.fieldsReader, never()).valuesIn(any())
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

    @Test
    fun `a Field kept on a hidden Requirement is treated as absent by a downstream condition`() {
        val fixture = Fixture()
        fixture.withConditionRules(fixture.priorAnswerProvided(), fixture.firstAmountIsLarge())
        fixture.withRootConditionalRequirement(PRIOR_ANSWER_PROVIDED, fixture.fieldDefinitionId)
        fixture.withRootConditionalRequirement(FIRST_AMOUNT_IS_LARGE, null)
        fixture.withRootFieldValues(fixture.fieldContract to BigDecimal("25.00"))

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(InformationRequestConditionEvaluationState.FALSE, projections.state(PRIOR_ANSWER_PROVIDED))
        assertEquals(InformationRequestConditionEvaluationState.FALSE, projections.state(FIRST_AMOUNT_IS_LARGE))
    }

    @Test
    fun `hiding cascades through a chain of conditions that read Fields collected by hidden Requirements`() {
        val fixture = Fixture()
        fixture.withConditionRules(
            fixture.priorAnswerProvided(),
            fixture.firstAmountIsLarge(),
            fixture.secondAmountIsEmpty()
        )
        fixture.withRootConditionalRequirement(PRIOR_ANSWER_PROVIDED, fixture.fieldDefinitionId)
        fixture.withRootConditionalRequirement(FIRST_AMOUNT_IS_LARGE, fixture.secondFieldDefinitionId)
        fixture.withRootConditionalRequirement(SECOND_AMOUNT_IS_EMPTY, null)
        fixture.withRootFieldValues(
            fixture.fieldContract to BigDecimal("25.00"),
            fixture.secondFieldContract to BigDecimal("5.00"),
        )

        val snapshot = fixture.service.snapshot(fixture.request.id)

        assertEquals(
            InformationRequestConditionEvaluationState.FALSE,
            snapshot.evaluations.state(PRIOR_ANSWER_PROVIDED)
        )
        assertEquals(
            InformationRequestConditionEvaluationState.FALSE,
            snapshot.evaluations.state(FIRST_AMOUNT_IS_LARGE)
        )
        assertEquals(
            InformationRequestConditionEvaluationState.TRUE,
            snapshot.evaluations.state(SECOND_AMOUNT_IS_EMPTY)
        )
        assertEquals(2, snapshot.inactiveRequirementIds.size)
    }

    @Test
    fun `a disposition is read only from a Requirement that is still active`() {
        val fixture = Fixture()
        fixture.withConditionRules(fixture.firstAmountIsLarge(), fixture.priorAnswerProvided())
        fixture.governRequirement(fixture.requirement, FIRST_AMOUNT_IS_LARGE)
        fixture.withRootConditionalRequirement(PRIOR_ANSWER_PROVIDED, null)
        fixture.withResponse(fixture.requirement, InformationRequestResponseDisposition.PROVIDED)

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(InformationRequestConditionEvaluationState.UNKNOWN, projections.state(FIRST_AMOUNT_IS_LARGE))
        assertEquals(InformationRequestConditionEvaluationState.FALSE, projections.state(PRIOR_ANSWER_PROVIDED))
    }

    @Test
    fun `a Requirement whose response is hidden keeps its retained Field out of later conditions`() {
        val fixture = Fixture()
        fixture.withConditionRules(fixture.priorAnswerProvided(), fixture.firstAmountIsLarge())
        val collecting = fixture.withRootConditionalRequirement(PRIOR_ANSWER_PROVIDED, fixture.fieldDefinitionId)
        fixture.withRootConditionalRequirement(FIRST_AMOUNT_IS_LARGE, null)
        fixture.withResponse(fixture.requirement, InformationRequestResponseDisposition.PROVIDED)
        fixture.withResponse(collecting, InformationRequestResponseDisposition.PROVIDED, active = false)
        fixture.withRootFieldValues(fixture.fieldContract to BigDecimal("25.00"))

        val snapshot = fixture.service.snapshot(fixture.request.id)

        assertEquals(InformationRequestConditionEvaluationState.TRUE, snapshot.evaluations.state(PRIOR_ANSWER_PROVIDED))
        assertEquals(
            InformationRequestConditionEvaluationState.FALSE,
            snapshot.evaluations.state(FIRST_AMOUNT_IS_LARGE)
        )
        assertTrue(collecting.id in snapshot.inactiveRequirementIds)
    }

    @Test
    fun `a rule kept on a Requirement that is not conditional never hides that Requirement's Field`() {
        val fixture = Fixture()
        fixture.withConditionRules(fixture.priorAnswerProvided(), fixture.firstAmountIsLarge())
        fixture.withRootConditionalRequirement(
            PRIOR_ANSWER_PROVIDED,
            fixture.fieldDefinitionId,
            InformationRequestRequiredness.REQUIRED,
        )
        fixture.withRootConditionalRequirement(FIRST_AMOUNT_IS_LARGE, null)
        fixture.withRootFieldValues(fixture.fieldContract to BigDecimal("25.00"))

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(InformationRequestConditionEvaluationState.TRUE, projections.state(FIRST_AMOUNT_IS_LARGE))
    }

    @Test
    fun `a Field an active Requirement collects but nobody answered is empty, so an is-empty condition asks its Requirement`() {
        val fixture = Fixture()
        fixture.withConditionRules(fixture.secondAmountIsEmpty(), fixture.firstAmountIsLarge())
        fixture.withRootConditionalRequirement(
            "unused-rule",
            fixture.secondFieldDefinitionId,
            InformationRequestRequiredness.OPTIONAL
        )
        fixture.withRootConditionalRequirement(
            "unused-rule",
            fixture.fieldDefinitionId,
            InformationRequestRequiredness.OPTIONAL
        )
        val explanation = fixture.withRootConditionalRequirement(SECOND_AMOUNT_IS_EMPTY, null)

        val snapshot = fixture.service.snapshot(fixture.request.id)

        assertEquals(
            InformationRequestConditionEvaluationState.TRUE,
            snapshot.evaluations.state(SECOND_AMOUNT_IS_EMPTY)
        )
        assertEquals(
            InformationRequestConditionEvaluationState.FALSE,
            snapshot.evaluations.state(FIRST_AMOUNT_IS_LARGE)
        )
        assertTrue(explanation.id !in snapshot.inactiveRequirementIds)
    }

    @Test
    fun `a Field no Requirement of the request collects stays unknown`() {
        val fixture = Fixture()
        fixture.withConditionRules(fixture.secondAmountIsEmpty())

        val projections = fixture.service.evaluate(fixture.request.id)

        assertEquals(InformationRequestConditionEvaluationState.UNKNOWN, projections.state(SECOND_AMOUNT_IS_EMPTY))
    }

    private fun List<InformationRequestConditionEvaluationProjection>.state(ruleKey: String) =
        single { it.ruleKey == ruleKey }.state

    private class Fixture
    {
        val fieldDefinitionId: UUID = UUID.randomUUID()
        val secondFieldDefinitionId: UUID = UUID.randomUUID()
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
            requirementType = InformationRequestRequirementType.FIELD
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
        val secondFieldContract = FieldContract().apply {
            this.fieldDefinitionId = this@Fixture.secondFieldDefinitionId
            valueType = FieldValueType.DECIMAL
            label = "Second amount"
        }

        val requestRepository = mock<InformationRequestRepository>()
        val templateVersionRepository = mock<InformationRequestTemplateVersionRepository>()
        val templateRequirementRepository = mock<InformationRequestTemplateRequirementRepository>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val responseStore = mock<InformationRequestResponseStore>()
        val fieldsReader = mock<FieldsReadFacade>()
        val templateProjectionLoader = mock<InformationRequestTemplateProjectionLoader>()
        val documentFiles = mock<InformationRequestDocumentFileCompletion>()

        val service = InformationRequestConditionEvaluationService(
            requestRepository = requestRepository,
            templateVersionRepository = templateVersionRepository,
            templateRequirementRepository = templateRequirementRepository,
            requirementRepository = requirementRepository,
            occurrenceRepository = occurrenceRepository,
            bindingRepository = bindingRepository,
            responseStore = responseStore,
            fieldValueReader = InformationRequestCanonicalFieldValueReader(fieldsReader = fieldsReader),
            templateProjectionLoader = templateProjectionLoader,
            evaluator = InformationRequestConditionEvaluator(),
            documentFiles = documentFiles,
        )

        private val runtimeRequirements = mutableListOf(requirement)
        private val templateRequirements = mutableListOf(templateRequirement)
        private val bindings = mutableListOf<InformationRequestTemplateRequirementBinding>()
        private val responses = mutableListOf<InformationRequestResponse>()

        init
        {
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(templateVersionRepository.findById(templateVersion.id)).thenReturn(templateVersion)
            whenever(templateRequirementRepository.findAllByDefinition(templateVersion.templateDefinitionId))
                .thenReturn(templateRequirements)
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
            whenever(fieldsReader.assignmentFor(FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, request.id)))
                .thenReturn(schemaAssignment)
            whenever(fieldsReader.valueSetOf(schemaAssignment.id, FieldValueSetRef.Root)).thenReturn(rootValueSet)
            whenever(fieldsReader.valuesIn(rootValueSet.id)).thenReturn(emptyList())
            whenever(responseStore.findCurrentForRequest(request.id)).thenAnswer { responses.filter { it.activeInResponse } }
            whenever(responseStore.findAllForRequest(request.id)).thenAnswer { responses.toList() }
        }

        fun withConditionRules(vararg rules: InformationRequestTemplateConditionRuleDto)
        {
            whenever(templateProjectionLoader.loadConditionRules(templateVersion.id)).thenReturn(rules.toList())
        }

        fun priorAnswerProvided() = InformationRequestTemplateConditionRuleDto(
            id = UUID.randomUUID(),
            ruleKey = PRIOR_ANSWER_PROVIDED,
            expressionVersion = 1,
            predicates = listOf(
                InformationRequestTemplateConditionPredicateDto(
                    id = UUID.randomUUID(),
                    sourceRequirementKey = templateRequirement.requirementKey,
                    operator = FieldOperator.EQUALS,
                    expectedDisposition = InformationRequestResponseDisposition.PROVIDED,
                ),
            ),
        )

        fun firstAmountIsLarge() = InformationRequestTemplateConditionRuleDto(
            id = UUID.randomUUID(),
            ruleKey = FIRST_AMOUNT_IS_LARGE,
            expressionVersion = 1,
            predicates = listOf(
                InformationRequestTemplateConditionPredicateDto(
                    id = UUID.randomUUID(),
                    fieldDefinitionId = fieldDefinitionId,
                    valueType = FieldValueType.DECIMAL,
                    operator = FieldOperator.GREATER_THAN_OR_EQUAL,
                    value = JsonPrimitive("10"),
                ),
            ),
        )

        fun secondAmountIsEmpty() = InformationRequestTemplateConditionRuleDto(
            id = UUID.randomUUID(),
            ruleKey = SECOND_AMOUNT_IS_EMPTY,
            expressionVersion = 1,
            predicates = listOf(
                InformationRequestTemplateConditionPredicateDto(
                    id = UUID.randomUUID(),
                    fieldDefinitionId = secondFieldDefinitionId,
                    valueType = FieldValueType.DECIMAL,
                    operator = FieldOperator.IS_EMPTY,
                ),
            ),
        )

        fun withRootFieldValue(amount: BigDecimal)
        {
            whenever(fieldsReader.valuesIn(rootValueSet.id))
                .thenReturn(listOf(fieldValue(rootValueSet.id, amount)))
            whenever(fieldsReader.contracts(listOf(fieldContract.id))).thenReturn(listOf(fieldContract))
        }

        fun withRootFieldValues(vararg values: Pair<FieldContract, BigDecimal>) {
            whenever(fieldsReader.valuesIn(rootValueSet.id))
                .thenReturn(values.map { (contract, amount) -> fieldValue(rootValueSet.id, amount, contract) })
            whenever(fieldsReader.contracts(any())).thenReturn(listOf(fieldContract, secondFieldContract))
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
                requiredness = InformationRequestRequiredness.CONDITIONAL
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

        fun withRootConditionalRequirement(
            ruleKey: String,
            collectedFieldDefinitionId: UUID?,
            requiredness: InformationRequestRequiredness = InformationRequestRequiredness.CONDITIONAL,
        ): InformationRequestRequirement {
            val authored = InformationRequestTemplateRequirement().apply {
                templateDefinitionId = templateVersion.templateDefinitionId
                requirementKey = "conditional-answer-${templateRequirements.size}"
                requirementType = InformationRequestRequirementType.FIELD
            }
            templateRequirements += authored
            val binding = InformationRequestTemplateRequirementBinding().apply {
                templateVersionId = templateVersion.id
                templateDefinitionId = templateVersion.templateDefinitionId
                templateRequirementId = authored.id
                templateSectionId = UUID.randomUUID()
                displayOrder = bindings.size
                prompt = "Provide the conditional answer"
                this.requiredness = requiredness
                conditionalRuleKey = ruleKey
                this.collectedFieldDefinitionId = collectedFieldDefinitionId
            }
            bindings += binding
            return InformationRequestRequirement().apply {
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = authored.id
                sourceTemplateBindingId = binding.id
                occurrencePath = "root"
            }.also { runtimeRequirements += it }
        }

        fun governRequirement(governed: InformationRequestRequirement, ruleKey: String) {
            bindings += InformationRequestTemplateRequirementBinding().apply {
                id = governed.sourceTemplateBindingId
                templateVersionId = templateVersion.id
                templateDefinitionId = templateVersion.templateDefinitionId
                templateRequirementId = governed.sourceTemplateRequirementId
                templateSectionId = UUID.randomUUID()
                displayOrder = bindings.size
                prompt = "Provide the governed answer"
                requiredness = InformationRequestRequiredness.CONDITIONAL
                conditionalRuleKey = ruleKey
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
            active: Boolean = true,
        )
        {
            responses += InformationRequestResponse().apply {
                informationRequestId = request.id
                informationRequestRequirementId = requirement.id
                occurrencePath = requirement.occurrencePath
                this.disposition = disposition
                activeInResponse = active
                if (!active) {
                    hiddenByConditionRuleKey = "previously-hidden"
                    hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY
                    hiddenAt = java.sql.Timestamp.from(java.time.Instant.now())
                }
            }
        }

        fun withOccurrenceFieldValue(occurrencePath: String, amount: BigDecimal)
        {
            val occurrenceSet = FieldValueSet().apply {
                schemaAssignmentId = schemaAssignment.id
                setKind = FieldValueSetKind.OCCURRENCE
                this.occurrencePath = occurrencePath
            }
            whenever(fieldsReader.valueSetOf(schemaAssignment.id, FieldValueSetRef.Occurrence(occurrencePath)))
                .thenReturn(occurrenceSet)
            whenever(fieldsReader.valuesIn(occurrenceSet.id))
                .thenReturn(listOf(fieldValue(occurrenceSet.id, amount)))
            whenever(fieldsReader.contracts(listOf(fieldContract.id))).thenReturn(listOf(fieldContract))
        }

        private fun fieldValue(valueSetId: UUID, amount: BigDecimal, contract: FieldContract = fieldContract) =
            FieldValue().apply {
            fieldValueSetId = valueSetId
            schemaAssignmentId = schemaAssignment.id
                fieldContractId = contract.id
            resourceType = ResourceType.INFORMATION_REQUEST.name
            resourceId = request.id
            valueType = FieldValueType.DECIMAL
            numberValue = amount
        }
    }

    private companion object {
        const val PRIOR_ANSWER_PROVIDED = "when-prior-answer-provided"
        const val FIRST_AMOUNT_IS_LARGE = "when-first-amount-is-large"
        const val SECOND_AMOUNT_IS_EMPTY = "when-second-amount-is-empty"
    }
}
