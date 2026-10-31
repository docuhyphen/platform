package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import java.time.Instant
import java.util.*

data class FieldBindingAccess(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val valueSet: FieldValueSetRef,
    val binding: SchemaFieldBinding,
    val operation: FieldValueOperation,
)

data class ResolvedFieldBinding(
    val binding: SchemaFieldBinding,
    val contract: FieldContract,
    val definition: FieldDefinition,
)

data class ResolvedFieldValue(
    val question: ResolvedFieldBinding,
    val stored: FieldValue?,
    val selectionCodes: List<String> = emptyList(),
)

data class PublishedSchemaVersionRef(
    val schemaVersionId: UUID,
    val schemaDefinitionId: UUID,
    val versionNumber: Int,
    val targetResourceType: String,
)

data class CanonicalDateTimeReading(val instant: Instant, val offsetMinutes: Int?)

data class ExchangeFieldSnapshot(
    val hasAssignedSchema: Boolean,
    val valuesByFieldDefinitionId: Map<UUID, CanonicalFieldValue>,
)
