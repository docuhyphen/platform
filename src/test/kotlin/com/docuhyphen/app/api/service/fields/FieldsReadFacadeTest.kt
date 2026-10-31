package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldValueSetRef
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.repository.fields.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class FieldsReadFacadeTest {
    private val assignments = mock<SchemaAssignmentRepository>()
    private val valueSets = mock<FieldValueSetRepository>()
    private val values = mock<FieldValueRepository>()
    private val selections = mock<FieldValueSelectionRepository>()
    private val contracts = mock<FieldContractRepository>()
    private val schemaBindings = mock<SchemaFieldBindingRepository>()
    private val facade = FieldsReadFacade(assignments, valueSets, values, selections, contracts, schemaBindings, mock())
    private val assignmentId = UUID.randomUUID()

    @Test
    fun `the assignment of a resource is read by its resource type and identity`() {
        val resourceId = UUID.randomUUID()
        val assignment = SchemaAssignment()
        whenever(assignments.findByResource("PROCESS_RECORD", resourceId)).thenReturn(assignment)

        assertSame(assignment, facade.assignmentFor(FieldsResourceRef("PROCESS_RECORD", resourceId)))
        assertNull(facade.assignmentFor(FieldsResourceRef("PROCESS_RECORD", UUID.randomUUID())))
    }

    @Test
    fun `the root set and an occurrence set are read from the assignment they belong to`() {
        val root = FieldValueSet()
        val occurrence = FieldValueSet()
        whenever(valueSets.findRoot(assignmentId)).thenReturn(root)
        whenever(valueSets.findOccurrence(assignmentId, "items[1]")).thenReturn(occurrence)

        assertSame(root, facade.valueSetOf(assignmentId, FieldValueSetRef.Root))
        assertSame(occurrence, facade.valueSetOf(assignmentId, FieldValueSetRef.Occurrence("items[1]")))
        verify(valueSets, never()).findRootForUpdate(any())
        verify(valueSets, never()).findOccurrenceForUpdate(any(), any())
    }

    @Test
    fun `the selected option codes of a value are read in stored order`() {
        val valueId = UUID.randomUUID()
        whenever(selections.findByValue(valueId)).thenReturn(
            listOf(
                FieldValueSelection().apply { fieldValueId = valueId; optionCode = "second" },
                FieldValueSelection().apply { fieldValueId = valueId; optionCode = "first" },
            ),
        )

        assertEquals(listOf("second", "first"), facade.selectionCodesOf(valueId))
    }

    @Test
    fun `values, contracts, and schema bindings are read by the identities that hold them`() {
        val valueSetId = UUID.randomUUID()
        val schemaVersionId = UUID.randomUUID()
        val value = FieldValue()
        val contract = FieldContract()
        val binding = SchemaFieldBinding()
        whenever(values.findByValueSet(valueSetId)).thenReturn(listOf(value))
        whenever(contracts.findById(contract.id)).thenReturn(contract)
        whenever(contracts.findByIds(listOf(contract.id))).thenReturn(listOf(contract))
        whenever(schemaBindings.findByVersion(schemaVersionId)).thenReturn(listOf(binding))

        assertEquals(listOf(value), facade.valuesIn(valueSetId))
        assertSame(contract, facade.contract(contract.id))
        assertEquals(listOf(contract), facade.contracts(listOf(contract.id)))
        assertEquals(listOf(binding), facade.schemaBindings(schemaVersionId))
    }
}
