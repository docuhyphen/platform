package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.fields.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

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
    private val fieldDefinitionRepository: FieldDefinitionRepository = mock()
    private val fieldValueValidator: FieldValueValidator = mock()

    private val service = SchemaDefinitionService(
        schemaDefinitionRepository,
        schemaVersionRepository,
        bindingRepository,
        fieldContractRepository,
        fieldDefinitionRepository,
        fieldValueValidator,
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        FieldsProjectionLoader(
            bindingRepository, fieldContractRepository, fieldDefinitionRepository, mock(), mock(),
        ),
        SchemaTargetRegistry(),
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
        val result = service.validateDefaultsForSchema(schemaId, emptyList(), ResourceType.EXCHANGE.name)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `unknown schema id throws`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(null)
        assertThrows(IllegalArgumentException::class.java) {
            service.validateDefaultsForSchema(
                schemaId,
                listOf(fieldDefinitionId to JsonPrimitive("x")),
                ResourceType.EXCHANGE.name,
            )
        }
    }

    @Test
    fun `non-Exchange target schema is rejected`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(schema(target = "ORGANIZATION"))
        assertThrows(FieldValidationException::class.java) {
            service.validateDefaultsForSchema(
                schemaId,
                listOf(fieldDefinitionId to JsonPrimitive("x")),
                ResourceType.EXCHANGE.name,
            )
        }
    }

    @Test
    fun `a schema written for another resource is rejected for the resource being configured`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId))
            .thenReturn(schema(target = "INFORMATION_REQUEST"))

        val refusal = assertThrows(FieldValidationException::class.java) {
            service.validateDefaultsForSchema(
                schemaId,
                listOf(fieldDefinitionId to JsonPrimitive("x")),
                ResourceType.EXCHANGE.name,
            )
        }

        assertTrue(
            refusal.message.orEmpty().contains("INFORMATION_REQUEST"),
            "The refusal should name the resource the schema was written for: ${refusal.message}",
        )
    }

    @Test
    fun `a caller configuring a target nobody declared is rejected before the schema is read`()
    {
        assertThrows(FieldValidationException::class.java) {
            service.validateDefaultsForSchema(
                schemaId,
                listOf(fieldDefinitionId to JsonPrimitive("x")),
                "UNDECLARED_RESOURCE",
            )
        }

        verify(schemaDefinitionRepository, never()).findById(any())
    }

    @Test
    fun `schema without published version is rejected`()
    {
        whenever(schemaDefinitionRepository.findById(schemaId)).thenReturn(schema())
        whenever(schemaVersionRepository.findLatestPublished(schemaId)).thenReturn(null)
        assertThrows(FieldValidationException::class.java) {
            service.validateDefaultsForSchema(
                schemaId,
                listOf(fieldDefinitionId to JsonPrimitive("x")),
                ResourceType.EXCHANGE.name,
            )
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
            service.validateDefaultsForSchema(
                schemaId,
                listOf(UUID.randomUUID() to JsonPrimitive("x")),
                ResourceType.EXCHANGE.name,
            )
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
        val result = service.validateDefaultsForSchema(
            schemaId,
            listOf(fieldDefinitionId to value),
            ResourceType.EXCHANGE.name,
        )

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
            service.validateDefaultsForSchema(
                schemaId,
                listOf(fieldDefinitionId to JsonPrimitive("x")),
                ResourceType.EXCHANGE.name,
            )
        }
    }
}
