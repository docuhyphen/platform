package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

// ── Response DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class CommunicationDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val summary: String?,
    val description: String?,
    val scope: String,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val createdByAppUserId: UUID?,
    val subject: String,
    val body: String,
    val generalTags: List<String>,
    val isActive: Boolean,
    val isPublished: Boolean,
    val isTemplate: Boolean,
    @Serializable(with = UUIDSerializer::class)
    val sourceTemplateId: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val updatedAt: Timestamp,
)

// ── Request types ─────────────────────────────────────────────────────────────

@Serializable
data class CreateCommunicationRequest(
    val name: String,
    val subject: String,
    val body: String,
    val summary: String? = null,
    val description: String? = null,
    val generalTags: List<String> = emptyList(),
    val isActive: Boolean = true,
    val scope: String? = null,
    val isTemplate: Boolean = false,
)

@Serializable
data class UpdateCommunicationRequest(
    val name: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val generalTags: List<String>? = null,
)

@Serializable
data class PatchCommunicationPublishedRequest(
    val isPublished: Boolean,
)

@Serializable
data class PatchCommunicationStatusRequest(
    val isActive: Boolean,
)

@Serializable
data class CloneCommunicationRequest(
    val newName: String? = null,
)

@Serializable
data class PreviewCommunicationRequest(
    val sampleVariables: Map<String, String> = emptyMap(),
)
