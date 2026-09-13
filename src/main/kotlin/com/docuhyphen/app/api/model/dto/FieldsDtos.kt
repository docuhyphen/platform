package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.docuhyphen.app.api.service.fields.FieldConstraints
import com.docuhyphen.app.api.service.fields.FieldOperator
import com.docuhyphen.app.api.service.fields.FieldOption
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

/** Response DTOs for the configurable Fields and Business Schema engine. */

@Serializable
data class FieldDefinitionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val scopeKind: FieldScopeKind,
    @Serializable(with = UUIDSerializer::class) val scopeOrgId: UUID? = null,
    val namespace: String,
    val fieldKey: String,
    val status: FieldLifecycleStatus,
    val contractCount: Int,
    val latestContract: FieldContractDto? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

@Serializable
data class FieldContractDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val fieldDefinitionId: UUID,
    val contractVersion: Int,
    val valueType: FieldValueType,
    val typeContractVersion: Int,
    val label: String,
    val description: String? = null,
    val helpText: String? = null,
    val constraints: FieldConstraints,
    val options: List<FieldOption> = emptyList(),
    val dataClassification: FieldDataClassification,
    val isSearchable: Boolean,
    val isFilterable: Boolean,
    val isSortable: Boolean,
    val isReportable: Boolean,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

@Serializable
data class SchemaDefinitionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val scopeKind: FieldScopeKind,
    @Serializable(with = UUIDSerializer::class) val scopeOrgId: UUID? = null,
    val namespace: String,
    val schemaKey: String,
    val displayName: String,
    val description: String? = null,
    val targetResourceType: String,
    val status: FieldLifecycleStatus,
    val draftVersion: SchemaVersionDto? = null,
    val latestPublishedVersion: SchemaVersionDto? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

@Serializable
data class SchemaVersionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val schemaDefinitionId: UUID,
    val versionNumber: Int,
    val status: FieldLifecycleStatus,
    val compatibility: SchemaCompatibility? = null,
    val bindings: List<SchemaFieldBindingDto> = emptyList(),
    @Serializable(with = TimestampSerializer::class) val publishedAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val createdAt: Timestamp,
)

@Serializable
data class SchemaFieldBindingDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    @Serializable(with = UUIDSerializer::class) val fieldContractId: UUID,
    @Serializable(with = UUIDSerializer::class) val fieldDefinitionId: UUID,
    val namespace: String,
    val fieldKey: String,
    val label: String,
    val valueType: FieldValueType,
    val displayOrder: Int,
    val section: String? = null,
    val isRequired: Boolean,
    val isReadOnly: Boolean,
    val defaultValueJson: String? = null,
    val visibility: FieldDataClassification,
    val description: String? = null,
    val helpText: String? = null,
    val constraints: FieldConstraints,
    val options: List<FieldOption> = emptyList(),
)

/** The engine-resolved, ordered, flattened view a consumer uses instead of loading raw config. */
@Serializable
data class ResolvedSchemaViewDto(
    @Serializable(with = UUIDSerializer::class) val schemaDefinitionId: UUID,
    val schemaKey: String,
    val namespace: String,
    val displayName: String,
    @Serializable(with = UUIDSerializer::class) val schemaVersionId: UUID,
    val versionNumber: Int,
    val targetResourceType: String,
    val scopeKind: FieldScopeKind,
    val fields: List<SchemaFieldBindingDto> = emptyList(),
)

/** Describes a registered field value type for UI construction. */
@Serializable
data class FieldTypeInfoDto(
    val type: FieldValueType,
    val supportsOptions: Boolean,
    val supportedOperators: List<FieldOperator>,
)

/** A resource's current schema assignment plus its resolved field values. */
@Serializable
data class SchemaAssignmentDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val resourceType: String,
    @Serializable(with = UUIDSerializer::class) val resourceId: UUID,
    @Serializable(with = UUIDSerializer::class) val schemaVersionId: UUID,
    @Serializable(with = UUIDSerializer::class) val schemaDefinitionId: UUID,
    val schemaKey: String,
    val displayName: String,
    val versionNumber: Int,
    val assignmentSource: SchemaAssignmentSource,
    @Serializable(with = TimestampSerializer::class) val assignedAt: Timestamp,
    /**
     * The questions the assigned Schema Version asks of this caller, described as an editor is built
     * from them. Filtered by the same rule as [fields] and listed in the same order, so a consumer
     * pairs a description with its answer without a second request, and a question this caller may
     * not be shown appears in neither list.
     */
    val bindings: List<SchemaFieldBindingDto> = emptyList(),
    val fields: List<FieldValueDto> = emptyList(),
    /**
     * Strong entity tag for the state of the answers carried in [fields]. A client sends it back on
     * its next write so a save can be refused when the answers moved on underneath it. Null only
     * where the resource has no set of answers to validate.
     */
    val etag: String? = null,
)

/**
 * One resolved field value for a resource. [value] is the canonical JSON representation
 * (string, number, boolean, ISO date/date-time, option code, or array of option codes),
 * suitable for typed editors and read-only renderers.
 */
@Serializable
data class FieldValueDto(
    @Serializable(with = UUIDSerializer::class) val fieldContractId: UUID,
    @Serializable(with = UUIDSerializer::class) val schemaFieldBindingId: UUID? = null,
    val namespace: String,
    val fieldKey: String,
    val label: String,
    val valueType: FieldValueType,
    val isEmpty: Boolean,
    val value: kotlinx.serialization.json.JsonElement,
)
