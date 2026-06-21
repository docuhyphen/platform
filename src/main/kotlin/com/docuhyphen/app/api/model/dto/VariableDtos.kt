package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.SequenceDefinition
import com.docuhyphen.app.api.model.entity.VariableDefinition
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

// ── Sequence DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class SequenceDefinitionDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID,
    val name: String,
    val key: String,
    val currentValue: Long,
    val padWidth: Int,
    val prefix: String?,
    val suffix: String?,
    val resetPeriod: String,
    @Serializable(with = TimestampSerializer::class)
    val lastResetAt: Timestamp?,
    val isActive: Boolean,
    @Serializable(with = UUIDSerializer::class)
    val createdByAppUserId: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    val previewValue: String,
)

fun SequenceDefinition.toDto(): SequenceDefinitionDto = SequenceDefinitionDto(
    id = id,
    organizationId = organizationId,
    name = name,
    key = key,
    currentValue = currentValue,
    padWidth = padWidth,
    prefix = prefix,
    suffix = suffix,
    resetPeriod = resetPeriod.name,
    lastResetAt = lastResetAt,
    isActive = isActive,
    createdByAppUserId = createdByAppUserId,
    createdAt = createdAt,
    previewValue = formatSequenceValue(currentValue + 1, padWidth, prefix, suffix),
)

fun formatSequenceValue(value: Long, padWidth: Int, prefix: String?, suffix: String?): String {
    val padded = if (padWidth > 0) value.toString().padStart(padWidth, '0') else value.toString()
    return "${prefix ?: ""}$padded${suffix ?: ""}"
}

@Serializable
data class CreateSequenceRequest(
    val name: String,
    val key: String,
    val padWidth: Int = 0,
    val prefix: String? = null,
    val suffix: String? = null,
    val resetPeriod: String = "NEVER",
)

@Serializable
data class UpdateSequenceRequest(
    val name: String? = null,
    val padWidth: Int? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val resetPeriod: String? = null,
    val isActive: Boolean? = null,
)

// ── Variable DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class VariableDefinitionDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val key: String,
    val defaultValue: String?,
    val scope: String,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val createdByAppUserId: UUID,
    val isActive: Boolean,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
)

fun VariableDefinition.toDto(): VariableDefinitionDto = VariableDefinitionDto(
    id = id,
    key = key,
    defaultValue = defaultValue,
    scope = scope.name,
    organizationId = organizationId,
    createdByAppUserId = createdByAppUserId,
    isActive = isActive,
    createdAt = createdAt,
)

@Serializable
data class CreateVariableRequest(
    val key: String,
    val defaultValue: String? = null,
    val scope: String,
)

@Serializable
data class UpdateVariableRequest(
    val defaultValue: String? = null,
    val isActive: Boolean? = null,
)

// ── Available Variables DTO ───────────────────────────────────────────────────

@Serializable
data class SystemVariableDto(
    val token: String,
    val description: String,
    val example: String,
)

@Serializable
data class AvailableVariablesDto(
    val system: List<SystemVariableDto>,
    val sequences: List<SequenceDefinitionDto>,
    val org: List<VariableDefinitionDto>,
    val personal: List<VariableDefinitionDto>,
)
