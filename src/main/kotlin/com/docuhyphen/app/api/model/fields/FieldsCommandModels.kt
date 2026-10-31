package com.docuhyphen.app.api.model.fields

import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.*

@Serializable
data class FieldValueEntry(
    @Serializable(with = UUIDSerializer::class)
    val fieldContractId: UUID,
    val value: JsonElement,
)

data class FieldValueWriteCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val entries: List<FieldValueEntry>,
    val valueSet: FieldValueSetRef = FieldValueSetRef.Root,
    val precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
) {
    val operation: FieldValueOperation get() = FieldValueOperation.WRITE
}

data class SchemaAssignmentCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val operation: SchemaAssignmentOperation,
    val schemaDefinitionId: UUID? = null,
    val source: SchemaAssignmentSource = SchemaAssignmentSource.MANUAL,
    val precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
)

data class PublishedSchemaAssignmentCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val schemaVersionId: UUID,
    val source: SchemaAssignmentSource = SchemaAssignmentSource.API,
    val precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
)

data class BlueprintFieldDefaultEntry(
    val fieldDefinitionId: UUID,
    val value: JsonElement,
)

data class BlueprintFieldDefaultsCommand(
    val resource: FieldsResourceRef,
    val access: FieldsAccessContext,
    val defaults: List<BlueprintFieldDefaultEntry>,
)

data class ValidatedSchemaDefault(
    val fieldDefinitionId: UUID,
    val valueType: FieldValueType,
    val value: JsonElement,
)
