package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueSelection
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Gathering the records a Fields projection is built from. The loader is the only part of the
 * projection that reaches a repository, so what it reads, what it leaves out, and what it declines
 * to read at all are pinned here.
 *
 * The schema under test asks three questions in a deliberately unsorted binding order, so an
 * assertion about order is answered by the loader rather than by the repository's own ordering.
 */
class FieldsProjectionLoaderTest
{
    private val schemaVersionId: UUID = UUID.randomUUID()
    private val valueSetId: UUID = UUID.randomUUID()

    private val firstQuestion = question(order = 0, key = "recorded-note")
    private val secondQuestion = question(order = 1, key = "recorded-second-note")
    private val choiceQuestion = question(order = 2, key = "recorded-options", type = FieldValueType.MULTI_SELECT)

    private val bindingRepository: SchemaFieldBindingRepository = mock()
    private val contractRepository: FieldContractRepository = mock()
    private val definitionRepository: FieldDefinitionRepository = mock()
    private val valueRepository: FieldValueRepository = mock()
    private val selectionRepository: FieldValueSelectionRepository = mock()

    private val valueSet = FieldValueSet().apply {
        id = valueSetId
        schemaAssignmentId = UUID.randomUUID()
        setKind = FieldValueSetKind.ROOT
    }

    @Test
    fun `questions are gathered in the order the schema asks them`()
    {
        val loader = loaderAsking(choiceQuestion, firstQuestion, secondQuestion)

        val resolved = loader.resolveBindings(schemaVersionId)

        assertEquals(
            listOf("recorded-note", "recorded-second-note", "recorded-options"),
            resolved.map { it.definition.fieldKey },
        )
    }

    @Test
    fun `each gathered question carries its binding, its contract, and its stable definition`()
    {
        val loader = loaderAsking(firstQuestion)

        val resolved = loader.resolveBindings(schemaVersionId).single()

        assertEquals(firstQuestion.binding.id, resolved.binding.id)
        assertEquals(firstQuestion.contract.id, resolved.contract.id)
        assertEquals(firstQuestion.definition.id, resolved.definition.id)
    }

    @Test
    fun `a question the caller may not be shown is left out`()
    {
        val loader = loaderAsking(firstQuestion, secondQuestion, choiceQuestion)

        val resolved = loader.resolveBindings(schemaVersionId) { it.fieldContractId == secondQuestion.contract.id }

        assertEquals(listOf("recorded-second-note"), resolved.map { it.definition.fieldKey })
    }

    @Test
    fun `a question whose contract cannot be loaded is left out rather than half reported`()
    {
        val loader = loaderAsking(firstQuestion, secondQuestion, withoutContract = setOf(secondQuestion.contract.id))

        val resolved = loader.resolveBindings(schemaVersionId)

        assertEquals(listOf("recorded-note"), resolved.map { it.definition.fieldKey })
    }

    @Test
    fun `a question whose stable definition cannot be loaded is left out rather than half reported`()
    {
        val loader = loaderAsking(firstQuestion, secondQuestion, withoutDefinition = setOf(secondQuestion.definition.id))

        val resolved = loader.resolveBindings(schemaVersionId)

        assertEquals(listOf("recorded-note"), resolved.map { it.definition.fieldKey })
    }

    @Test
    fun `every question is paired with the answer the addressed set holds for it`()
    {
        val loader = loaderAsking(firstQuestion, secondQuestion)
        storeAnswer(firstQuestion, "Recorded answer")

        val resolved = loader.resolveValues(schemaVersionId, valueSet)

        assertEquals(2, resolved.size)
        assertEquals("Recorded answer", resolved.first().stored?.textValue)
        assertNull(resolved.last().stored, "an unanswered question is paired with nothing")
    }

    @Test
    fun `a set holding no answers still gathers every question`()
    {
        val loader = loaderAsking(firstQuestion, secondQuestion)

        val resolved = loader.resolveValues(schemaVersionId, valueSet)

        assertEquals(2, resolved.size)
        assertTrue(resolved.all { it.stored == null })
    }

    @Test
    fun `no answers are read at all when the resource holds no set of them`()
    {
        val loader = loaderAsking(firstQuestion)

        val resolved = loader.resolveValues(schemaVersionId, valueSet = null)

        assertEquals(1, resolved.size)
        assertNull(resolved.single().stored)
        verify(valueRepository, never()).findByValueSet(any())
    }

    @Test
    fun `option codes are read for a stored choice, in the order they were stored`()
    {
        val loader = loaderAsking(choiceQuestion)
        val stored = storeAnswer(choiceQuestion, text = null)
        storeSelections(stored, "first-option", "third-option")

        val resolved = loader.resolveValues(schemaVersionId, valueSet).single()

        assertEquals(listOf("first-option", "third-option"), resolved.selectionCodes)
    }

    @Test
    fun `option codes are not looked up for a question that cannot hold any`()
    {
        val loader = loaderAsking(firstQuestion)
        storeAnswer(firstQuestion, "Recorded answer")

        val resolved = loader.resolveValues(schemaVersionId, valueSet).single()

        assertTrue(resolved.selectionCodes.isEmpty())
        verify(selectionRepository, never()).findByValue(any())
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    /** One question of the schema under test, as the three records that define it. */
    private data class Question(
        val binding: SchemaFieldBinding,
        val contract: FieldContract,
        val definition: FieldDefinition,
    )

    private fun question(order: Int, key: String, type: FieldValueType = FieldValueType.SHORT_TEXT): Question
    {
        val definition = FieldDefinition().apply {
            id = UUID.randomUUID()
            namespace = "process"
            fieldKey = key
        }
        val contract = FieldContract().apply {
            id = UUID.randomUUID()
            fieldDefinitionId = definition.id
            valueType = type
            label = key
        }
        val binding = SchemaFieldBinding().apply {
            id = UUID.randomUUID()
            schemaVersionId = this@FieldsProjectionLoaderTest.schemaVersionId
            fieldContractId = contract.id
            displayOrder = order
            visibility = FieldDataClassification.INTERNAL
        }
        return Question(binding, contract, definition)
    }

    private fun loaderAsking(
        vararg questions: Question,
        withoutContract: Set<UUID> = emptySet(),
        withoutDefinition: Set<UUID> = emptySet(),
    ): FieldsProjectionLoader
    {
        whenever(bindingRepository.findByVersion(schemaVersionId)).thenReturn(questions.map { it.binding })
        val contracts = questions.map { it.contract }.filterNot { it.id in withoutContract }
        whenever(contractRepository.findByIds(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<UUID>>(0).toSet()
            contracts.filter { it.id in ids }
        }
        questions.map { it.definition }.filterNot { it.id in withoutDefinition }
            .forEach { whenever(definitionRepository.findById(it.id)).thenReturn(it) }
        return FieldsProjectionLoader(
            bindingRepository, contractRepository, definitionRepository, valueRepository, selectionRepository,
        )
    }

    private val storedValues: MutableList<FieldValue> = mutableListOf()

    private fun storeAnswer(question: Question, text: String?): FieldValue
    {
        val value = FieldValue().apply {
            id = UUID.randomUUID()
            fieldValueSetId = valueSetId
            schemaAssignmentId = valueSet.schemaAssignmentId
            fieldContractId = question.contract.id
            resourceType = "EXCHANGE"
            resourceId = UUID.randomUUID()
            valueType = question.contract.valueType
            textValue = text
        }
        storedValues += value
        whenever(valueRepository.findByValueSet(valueSetId)).thenReturn(storedValues.toList())
        return value
    }

    private fun storeSelections(value: FieldValue, vararg codes: String)
    {
        whenever(selectionRepository.findByValue(value.id)).thenReturn(
            codes.mapIndexed { index, code ->
                FieldValueSelection().apply {
                    id = UUID.randomUUID()
                    fieldValueId = value.id
                    optionCode = code
                    displayOrder = index
                }
            },
        )
    }
}
