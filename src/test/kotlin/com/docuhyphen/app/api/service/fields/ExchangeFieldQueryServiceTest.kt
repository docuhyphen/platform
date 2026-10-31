package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.fields.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class ExchangeFieldQueryServiceTest {
    private val exchangeId = UUID.randomUUID()
    private val assignmentId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val rootSetId = UUID.randomUUID()

    private val referenceFieldId = UUID.randomUUID()
    private val referenceContractV1 = contract(referenceFieldId, 1, FieldValueType.SHORT_TEXT)
    private val referenceContractV2 = contract(referenceFieldId, 2, FieldValueType.SHORT_TEXT)
    private val unboundFieldId = UUID.randomUUID()
    private val unboundContract = contract(unboundFieldId, 1, FieldValueType.SHORT_TEXT)
    private val choiceFieldId = UUID.randomUUID()
    private val choiceContract = contract(choiceFieldId, 1, FieldValueType.MULTI_SELECT)
    private val emptyFieldId = UUID.randomUUID()
    private val emptyContract = contract(emptyFieldId, 1, FieldValueType.SHORT_TEXT)

    private val referenceBinding = binding(referenceContractV2, 0)
    private val choiceBinding = binding(choiceContract, 1)
    private val emptyBinding = binding(emptyContract, 2)

    private val boundAnswer = answer(referenceContractV2, referenceBinding.id, "Bound answer")
    private val orphanedAnswer = answer(referenceContractV1, null, "Orphaned answer")
    private val unboundAnswer = answer(unboundContract, null, "Unbound answer")
    private val choiceAnswer = answer(choiceContract, choiceBinding.id, null)

    private val assignmentRepository = mock<SchemaAssignmentRepository>()
    private val valueSetRepository = mock<FieldValueSetRepository>()
    private val valueRepository = mock<FieldValueRepository>()
    private val bindingRepository = mock<SchemaFieldBindingRepository>()
    private val contractRepository = mock<FieldContractRepository>()
    private val definitionRepository = mock<FieldDefinitionRepository>()
    private val selectionRepository = mock<FieldValueSelectionRepository>()

    init {
        whenever(assignmentRepository.findByResource("EXCHANGE", exchangeId)).thenReturn(
            SchemaAssignment().apply {
                id = assignmentId
                resourceType = "EXCHANGE"
                resourceId = exchangeId
                schemaVersionId = versionId
            },
        )
        val rootSet = FieldValueSet().apply {
            id = rootSetId
            schemaAssignmentId = assignmentId
            setKind = FieldValueSetKind.ROOT
        }
        whenever(valueSetRepository.findRoot(assignmentId)).thenReturn(rootSet)
        whenever(bindingRepository.findByVersion(versionId)).thenReturn(
            listOf(
                referenceBinding,
                choiceBinding,
                emptyBinding
            )
        )
        val contracts = listOf(referenceContractV1, referenceContractV2, unboundContract, choiceContract, emptyContract)
        contracts.forEach { whenever(contractRepository.findById(it.id)).thenReturn(it) }
        whenever(contractRepository.findByIds(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<UUID>>(0).toSet()
            contracts.filter { it.id in ids }
        }
        listOf(referenceFieldId, unboundFieldId, choiceFieldId, emptyFieldId).forEach { fieldId ->
            whenever(definitionRepository.findById(fieldId)).thenReturn(
                FieldDefinition().apply {
                    id = fieldId
                    namespace = "process"
                    fieldKey = "field-${fieldId.toString().take(8)}"
                    status = FieldLifecycleStatus.PUBLISHED
                },
            )
        }
        whenever(selectionRepository.findByValue(any())).thenAnswer { invocation ->
            if (invocation.getArgument<UUID>(0) == choiceAnswer.id)
                listOf("first-option", "second-option").mapIndexed { index, code ->
                    FieldValueSelection().apply {
                        fieldValueId = choiceAnswer.id
                        optionCode = code
                        displayOrder = index
                    }
                }
            else emptyList<FieldValueSelection>()
        }
    }

    @Test
    fun `a condition reads the answer the assigned version binds for a stable field in whatever order answers are read`() {
        listOf(
            listOf(boundAnswer, orphanedAnswer, unboundAnswer, choiceAnswer),
            listOf(orphanedAnswer, unboundAnswer, choiceAnswer, boundAnswer),
        ).forEach { stored ->
            whenever(valueRepository.findByValueSet(rootSetId)).thenReturn(stored)

            val snapshot = queryService().getCanonicalValues(exchangeId)

            assertEquals(
                "Bound answer", snapshot.valuesByFieldDefinitionId[referenceFieldId]?.textValue,
                "An answer kept for a contract the version no longer binds must never stand in for the bound one",
            )
        }
    }

    @Test
    fun `an answer to a contract the assigned version does not bind is invisible to a condition`() {
        whenever(valueRepository.findByValueSet(rootSetId))
            .thenReturn(listOf(unboundAnswer, boundAnswer, orphanedAnswer, choiceAnswer))

        val snapshot = queryService().getCanonicalValues(exchangeId)

        assertTrue(snapshot.hasAssignedSchema)
        assertNull(snapshot.valuesByFieldDefinitionId[unboundFieldId])
        assertEquals(setOf(referenceFieldId, choiceFieldId), snapshot.valuesByFieldDefinitionId.keys)
    }

    @Test
    fun `a bound field nobody answered is absent and a chosen option carries its codes`() {
        whenever(valueRepository.findByValueSet(rootSetId)).thenReturn(listOf(boundAnswer, choiceAnswer))

        val snapshot = queryService().getCanonicalValues(exchangeId)

        assertNull(snapshot.valuesByFieldDefinitionId[emptyFieldId])
        val choice = snapshot.valuesByFieldDefinitionId.getValue(choiceFieldId)
        assertEquals(listOf("first-option", "second-option"), choice.selectionCodes)
        assertFalse(choice.isEmpty)
    }

    @Test
    fun `a resource with no schema has nothing for a condition to read`() {
        val snapshot = queryService().getCanonicalValues(UUID.randomUUID())

        assertFalse(snapshot.hasAssignedSchema)
        assertTrue(snapshot.valuesByFieldDefinitionId.isEmpty())
    }

    private fun queryService() = ExchangeFieldQueryService(
        assignmentRepository = assignmentRepository,
        fieldValueSetRepository = valueSetRepository,
        projectionLoader = FieldsProjectionLoader(
            bindingRepository, contractRepository, definitionRepository, valueRepository, selectionRepository,
        ),
    )

    private fun contract(fieldId: UUID, version: Int, type: FieldValueType) = FieldContract().apply {
        id = UUID.randomUUID()
        fieldDefinitionId = fieldId
        contractVersion = version
        valueType = type
        label = "Recorded value"
    }

    private fun binding(contract: FieldContract, order: Int) = SchemaFieldBinding().apply {
        id = UUID.randomUUID()
        schemaVersionId = versionId
        fieldContractId = contract.id
        fieldDefinitionId = contract.fieldDefinitionId
        displayOrder = order
        visibility = FieldDataClassification.INTERNAL
    }

    private fun answer(contract: FieldContract, bindingId: UUID?, text: String?) = FieldValue().apply {
        id = UUID.randomUUID()
        fieldValueSetId = rootSetId
        schemaAssignmentId = assignmentId
        bindingId?.let { schemaFieldBindingId = it }
        fieldContractId = contract.id
        resourceType = "EXCHANGE"
        resourceId = exchangeId
        valueType = contract.valueType
        textValue = text
    }
}
