package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentStateDto
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.model.fields.ResolvedFieldValue
import com.docuhyphen.app.api.model.fields.SchemaAssignmentCapabilities
import com.docuhyphen.app.api.service.fields.FieldValueSetETag

object SchemaAssignmentDtoMapper
{
    fun toDto(
        assignment: SchemaAssignment,
        definition: SchemaDefinition,
        version: SchemaVersion,
        questions: List<ResolvedFieldValue>,
        valueSet: FieldValueSet?,
    ): SchemaAssignmentDto = SchemaAssignmentDto(
        id = assignment.id,
        resourceType = assignment.resourceType,
        resourceId = assignment.resourceId,
        schemaVersionId = assignment.schemaVersionId,
        schemaDefinitionId = definition.id,
        schemaKey = definition.schemaKey,
        displayName = definition.displayName,
        versionNumber = version.versionNumber,
        assignmentSource = assignment.assignmentSource,
        assignedAt = assignment.assignedAt,
        bindings = questions.map { SchemaFieldBindingDtoMapper.toDto(it.question) },
        fields = questions.map(FieldValueDtoMapper::toDto),
        etag = valueSet?.let(FieldValueSetETag::of),
    )

    fun toStateDto(
        assignment: SchemaAssignmentDto?,
        capabilities: SchemaAssignmentCapabilities,
    ): SchemaAssignmentStateDto = SchemaAssignmentStateDto(
        assignment = assignment,
        canManageSchema = capabilities.canManageSchema,
        canEditValues = capabilities.canEditValues,
    )
}
