package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldOption
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class SetFieldValuesRequest(
    val values: List<FieldValueEntry> = emptyList(),
)

@Serializable
data class AssignSchemaRequest(
    @Serializable(with = UUIDSerializer::class)
    val schemaDefinitionId: UUID,
)

@Serializable
data class FieldStatusRequest(val status: FieldLifecycleStatus)

@Serializable
data class FieldContractRequest(
    val valueType: FieldValueType,
    val label: String,
    val description: String? = null,
    val helpText: String? = null,
    val constraintsJson: String? = null,
    val options: List<FieldOption> = emptyList(),
    val dataClassification: FieldDataClassification = FieldDataClassification.INTERNAL,
    val isSearchable: Boolean = false,
    val isFilterable: Boolean = false,
    val isSortable: Boolean = false,
    val isReportable: Boolean = false,
)

@Serializable
data class CreateFieldDefinitionRequest(
    val namespace: String,
    val fieldKey: String,
    val scopeKind: FieldScopeKind? = null,
    val contract: FieldContractRequest,
)

@Serializable
data class BindingRequest(
    @Serializable(with = UUIDSerializer::class)
    val fieldContractId: UUID,
    val displayOrder: Int = -1,
    val section: String? = null,
    val isRequired: Boolean = false,
    val isReadOnly: Boolean = false,
    val defaultValueJson: String? = null,
    val visibility: FieldDataClassification? = null,
)

@Serializable
data class CreateSchemaRequest(
    val namespace: String,
    val schemaKey: String,
    val displayName: String,
    val description: String? = null,
    val scopeKind: FieldScopeKind? = null,
    val targetResourceType: String? = null,
    val bindings: List<BindingRequest> = emptyList(),
)

@Serializable
data class PublishSchemaRequest(
    val compatibility: SchemaCompatibility? = null,
)

@Serializable
data class UpdateBindingsRequest(
    val bindings: List<BindingRequest> = emptyList(),
)
