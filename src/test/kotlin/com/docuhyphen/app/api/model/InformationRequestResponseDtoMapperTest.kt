package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.FieldValueDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class InformationRequestResponseDtoMapperTest
{
    @Test
    fun `maps a response occurrence without leaking who recorded it`()
    {
        val response = InformationRequestResponse().apply {
            informationRequestId = UUID.randomUUID()
            informationRequestRequirementId = UUID.randomUUID()
            requirementRevisionId = UUID.randomUUID()
            occurrencePath = "root"
            disposition = InformationRequestResponseDisposition.PROVIDED
            narrative = "Complete."
            responseRevision = 2
            recordedByPrincipalKind = PrincipalKind.PARTICIPANT
            recordedByPrincipalId = UUID.randomUUID()
        }
        val requirement = requirementFor(response)

        val dto = InformationRequestResponseDtoMapper.toDto(response, requirement)

        assertEquals(response.informationRequestRequirementId, dto.informationRequestRequirementId)
        assertEquals(requirement.sourceTemplateRequirementId, dto.sourceTemplateRequirementId)
        assertEquals(requirement.sourceTemplateBindingId, dto.sourceTemplateBindingId)
        assertEquals("root", dto.occurrencePath)
        assertEquals(InformationRequestResponseDisposition.PROVIDED, dto.disposition)
        assertEquals("Complete.", dto.narrative)
        assertEquals(2, dto.responseRevision)
        assertEquals(response.updatedAt, dto.updatedAt)
    }

    @Test
    fun `maps structured Field values from the response value set projection`()
    {
        val valueSetId = UUID.randomUUID()
        val field = FieldValueDto(
            fieldContractId = UUID.randomUUID(),
            schemaFieldBindingId = UUID.randomUUID(),
            namespace = "process",
            fieldKey = "recorded-note",
            label = "Recorded note",
            valueType = FieldValueType.SHORT_TEXT,
            isEmpty = false,
            value = JsonPrimitive("Structured answer"),
        )
        val response = InformationRequestResponse().apply {
            informationRequestId = UUID.randomUUID()
            informationRequestRequirementId = UUID.randomUUID()
            requirementRevisionId = UUID.randomUUID()
            occurrencePath = "items[0]"
            disposition = InformationRequestResponseDisposition.PROVIDED
            fieldValueSetId = valueSetId
            responseRevision = 3
            recordedByPrincipalKind = PrincipalKind.PARTICIPANT
            recordedByPrincipalId = UUID.randomUUID()
        }
        val requirement = requirementFor(response)

        val dto = InformationRequestResponseDtoMapper.toDto(response, requirement, fieldProjection(valueSetId, listOf(field)))

        assertEquals(valueSetId, dto.fieldValueSetId)
        assertEquals(requirement.sourceTemplateRequirementId, dto.sourceTemplateRequirementId)
        assertEquals(requirement.sourceTemplateBindingId, dto.sourceTemplateBindingId)
        assertEquals("\"$valueSetId:4\"", dto.fieldValueSetETag)
        assertEquals(listOf(field), dto.fieldValues)
    }

    private fun requirementFor(response: InformationRequestResponse) = InformationRequestRequirement().apply {
        id = response.informationRequestRequirementId
        informationRequestId = response.informationRequestId
        sourceTemplateRequirementId = UUID.randomUUID()
        sourceTemplateBindingId = UUID.randomUUID()
        occurrencePath = response.occurrencePath
    }

    private fun fieldProjection(valueSetId: UUID, fields: List<FieldValueDto>) = SchemaAssignmentDto(
        id = UUID.randomUUID(),
        resourceType = "INFORMATION_REQUEST",
        resourceId = UUID.randomUUID(),
        schemaVersionId = UUID.randomUUID(),
        schemaDefinitionId = UUID.randomUUID(),
        schemaKey = "process-data",
        displayName = "Process data",
        versionNumber = 1,
        assignmentSource = SchemaAssignmentSource.API,
        assignedAt = Timestamp.from(Instant.now()),
        fields = fields,
        etag = "\"$valueSetId:4\"",
    )
}
