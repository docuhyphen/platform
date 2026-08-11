package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.FieldContractRepository
import com.docuhyphen.app.api.repository.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.SchemaVersionRepository
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit tests for [SchemaDefinitionService.validateDefaultsForSchema] - the authoring-time guard that
 * blueprint default values are checked against before persistence.
 */
class SchemaDefinitionServiceDefaultsTest
{
    private val schemaDefinitionRepository: SchemaDefinitionRepository = mock()
    private val schemaVersionRepository: SchemaVersionRepository = mock()
    private val bindingRepository: SchemaFieldBindingRepository = mock()
    private val fieldContractRepository: FieldContractRepository = mock()
    private val fieldValueValidator: FieldValueValidator = mock()

    private val service = SchemaDefinitionService(
        schemaDefinitionRepository,
        schemaVersionRepository,
        bindingRepository,
        fieldContractRepository,
        mock(),
        fieldValueValidator,
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
    )

    private val schemaId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val fieldDefinitionId = UUID.randomUUID()
    private val contractId = UUID.randomUUID()

    private fun schema(target: String = ResourceType.EXCHANGE.name) = SchemaDefinition().apply {
        id = schemaId
        targetResourceType = target
    }

    private fun version() = SchemaVersion().apply { id = versionId }

    private fun binding() = SchemaFieldBinding().apply { fieldContractId = contractId }

    private fun contract(type: FieldValueType = FieldValueType.SHORT_TEXT) = FieldContract().apply {
        id = contractId
        this.fieldDefinitionId = this@SchemaDefinitionServiceDefaultsTest.fieldDefinitionId
        valueType = type
        label = "Category"
    }

    @Test
    fun `empty values returns empty list without touching repositories`()
    {
        val result = service.validateDefaultsForSchema(schemaId, emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown schema id throws`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(null)
        assertThrows(IllegalArgumentException::class.java) {
            service.validateDefaultsForSchema(schemaId, listOf(fieldDefinitionId to JsonPrimitive("x")))
        }
    }

    @Test
    fun `non-Exchange target schema is rejected`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(schema(target = "ORGANIZATION"))
        assertThrows(FieldValidationException::class.java) {
            service.validateDefaultsForSchema(schemaId, listOf(fieldDefinitionId to JsonPrimitive("x")))
        }
    }

    @Test
    fun `schema without published version is rejected`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(schema())
        whenever(schemaVersionRepository.findLatestPublished(schemaId)).thenReturn(null)
        assertThrows(FieldValidationException::class.java) {
            service.validateDefaultsForSchema(schemaId, listOf(fieldDefinitionId to JsonPrimitive("x")))
        }
    }

    @Test
    fun `field not part of schema is rejected`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(schema())
        whenever(schemaVersionRepository.findLatestPublished(schemaId)).thenReturn(version())
        whenever(bindingRepository.findByVersion(versionId)).thenReturn(listOf(binding()))
        whenever(fieldContractRepository.findByIds(listOf(contractId))).thenReturn(listOf(contract()))
        assertThrows(FieldValidationException::class.java) {
            service.validateDefaultsForSchema(schemaId, listOf(UUID.randomUUID() to JsonPrimitive("x")))
        }
    }

    @Test
    fun `valid default canonicalizes and returns validated default with value type`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(schema())
        whenever(schemaVersionRepository.findLatestPublished(schemaId)).thenReturn(version())
        whenever(bindingRepository.findByVersion(versionId)).thenReturn(listOf(binding()))
        whenever(fieldContractRepository.findByIds(listOf(contractId)))
            .thenReturn(listOf(contract(FieldValueType.SHORT_TEXT)))
        whenever(fieldValueValidator.canonicalize(any(), any()))
            .thenReturn(CanonicalFieldValue(type = FieldValueType.SHORT_TEXT, isEmpty = false, textValue = "Onboarding"))

        val value = JsonPrimitive("Onboarding")
        val result = service.validateDefaultsForSchema(schemaId, listOf(fieldDefinitionId to value))

        assertEquals(1, result.size)
        assertEquals(fieldDefinitionId, result[0].fieldDefinitionId)
        assertEquals(FieldValueType.SHORT_TEXT, result[0].valueType)
        assertEquals(value, result[0].value)
    }

    @Test
    fun `invalid value propagates validator exception`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(schema())
        whenever(schemaVersionRepository.findLatestPublished(schemaId)).thenReturn(version())
        whenever(bindingRepository.findByVersion(versionId)).thenReturn(listOf(binding()))
        whenever(fieldContractRepository.findByIds(listOf(contractId))).thenReturn(listOf(contract()))
        whenever(fieldValueValidator.canonicalize(any(), any()))
            .thenThrow(FieldValidationException("bad value"))

        assertThrows(FieldValidationException::class.java) {
            service.validateDefaultsForSchema(schemaId, listOf(fieldDefinitionId to JsonPrimitive("x")))
        }
    }
}
