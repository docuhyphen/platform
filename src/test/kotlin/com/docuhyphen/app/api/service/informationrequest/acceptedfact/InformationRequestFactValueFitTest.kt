package com.docuhyphen.app.api.service.informationrequest.acceptedfact

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.FieldTypeRegistry
import com.docuhyphen.app.api.service.fields.FieldValueValidator
import com.docuhyphen.app.api.service.fields.FieldsReadFacade
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestFactValueFitTest {
    private val request = InformationRequest()
    private val fieldDefinitionId = UUID.randomUUID()
    private val fieldsReader = mock<FieldsReadFacade>()
    private val fit = InformationRequestFactValueFit(fieldsReader, FieldValueValidator(FieldTypeRegistry()))

    @Test
    fun `a fact whose value the later Field contract no longer accepts is not offered for reuse`() {
        laterContract("""{"maxLength": 5}""")

        assertFalse(fit.fits(request, fact("\"a recorded value longer than five\"")))
    }

    @Test
    fun `a fact whose value the later Field contract still accepts is offered`() {
        laterContract("""{"maxLength": 100}""")

        assertTrue(fit.fits(request, fact("\"a recorded value\"")))
    }

    private fun laterContract(constraints: String) {
        val assignment = SchemaAssignment().apply { schemaVersionId = UUID.randomUUID() }
        val contract = FieldContract().apply {
            fieldDefinitionId = this@InformationRequestFactValueFitTest.fieldDefinitionId
            valueType = FieldValueType.SHORT_TEXT
            label = "Recorded value"
            constraintsJson = constraints
        }
        val binding = SchemaFieldBinding().apply {
            fieldDefinitionId = this@InformationRequestFactValueFitTest.fieldDefinitionId
            fieldContractId = contract.id
        }
        whenever(
            fieldsReader.assignmentFor(
                FieldsResourceRef(
                    ResourceType.INFORMATION_REQUEST.name,
                    request.id
                )
            )
        ).thenReturn(assignment)
        whenever(fieldsReader.schemaBindings(assignment.schemaVersionId)).thenReturn(listOf(binding))
        whenever(fieldsReader.contract(contract.id)).thenReturn(contract)
    }

    private fun fact(value: String) = InformationRequestAcceptedFact().apply {
        fieldDefinitionId = this@InformationRequestFactValueFitTest.fieldDefinitionId
        valueType = FieldValueType.SHORT_TEXT
        canonicalValue = value
    }
}
