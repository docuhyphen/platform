package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.service.fields.FieldValueSetETag
import com.docuhyphen.app.api.service.fields.ResolvedFieldValue

/**
 * Shapes a resource's Schema Assignment and the answers it holds into the projection a consumer
 * reads: which Schema governs the resource, which version of it is in force, the questions it asks,
 * the answers themselves, and the validator naming the exact state those answers stand in. Nothing
 * here reaches a repository.
 */
object SchemaAssignmentDtoMapper
{
    /**
     * The projection for one assignment.
     *
     * [questions] are the questions this caller may be shown together with the answers held for
     * them, already narrowed and ordered by whoever gathered them. Both the descriptions an editor is
     * built from and the answers it shows are derived from that one list, so the projection cannot
     * describe a question it withholds the answer to, or answer one it does not describe.
     *
     * [valueSet] is the set those answers were read from, and is null only where the resource holds
     * no set of answers yet; a projection with no set carries no validator rather than an invented
     * one, because there is no state for a later write to be judged against.
     */
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
}
