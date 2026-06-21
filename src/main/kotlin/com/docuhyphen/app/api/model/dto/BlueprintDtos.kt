package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

// ── Blueprint config sub-types ────────────────────────────────────────────────

@Serializable
data class BlueprintDocumentConfig(
    val title: String,
    val restrictedType: String? = null,
    val restrictType: Boolean = false,
    val required: Boolean = false,
)

@Serializable
data class BlueprintRecipientConfiguration(
    val recipientRoleName: String? = null,
    val recipientConstraintsJson: String? = null,
    val defaultRecipientOrgGroupId: String? = null,
)

@Serializable
data class BlueprintParticipantConfig(
    val principalId: String,
    val principalKind: String,
    val roleName: String,
)

@Serializable
data class BlueprintConfigJson(
    val name: String? = null,
    val description: String? = null,
    val initialShareMessage: String? = null,
    val requestRecipientSignIn: Boolean = false,
    val allowDocumentAddition: Boolean = false,
    val allowDocumentDeletion: Boolean = false,
    val allowDocumentDownload: Boolean = false,
    val allowDocumentUpdate: Boolean = false,
    val allowDocumentUpload: Boolean = false,
    val allowedDownloadFormats: List<String>? = null,
    val exchangeDocuments: List<BlueprintDocumentConfig> = emptyList(),
    val recipientConfiguration: BlueprintRecipientConfiguration? = null,
    val participants: List<BlueprintParticipantConfig> = emptyList(),
)

// ── Response DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class BlueprintDefinitionDto(
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
    val isActive: Boolean,
    val isPublished: Boolean,
    val isTemplate: Boolean,
    val generalTags: List<String>,
    @Serializable(with = UUIDSerializer::class)
    val sourceTemplateId: UUID?,
    val configJson: String,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val updatedAt: Timestamp,
)

// ── Request types ─────────────────────────────────────────────────────────────

@Serializable
data class CreateBlueprintRequest(
    val name: String,
    val summary: String? = null,
    val description: String? = null,
    val configJson: String,
    val generalTags: List<String> = emptyList(),
    val isActive: Boolean = true,
    val scope: String? = null,
    val isTemplate: Boolean = false,
)

@Serializable
data class UpdateBlueprintRequest(
    val name: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val configJson: String? = null,
    val generalTags: List<String>? = null,
)

@Serializable
data class PatchBlueprintPublishedRequest(
    val isPublished: Boolean,
)

@Serializable
data class PatchBlueprintStatusRequest(
    val isActive: Boolean,
)

@Serializable
data class CloneBlueprintRequest(
    val newName: String? = null,
)
