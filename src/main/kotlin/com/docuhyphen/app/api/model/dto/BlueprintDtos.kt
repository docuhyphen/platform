package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.*

@Serializable
data class BlueprintDocumentConfig(
    val title: String,
    val restrictedType: String? = null,
    val restrictType: Boolean = false,
    val required: Boolean = false,
    @Serializable(with = UUIDSerializer::class)
    val libraryDocumentId: UUID? = null,
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
    val roleName: ExchangeShareRoleName,
)

@Serializable
data class BlueprintFieldDefaultConfig(
    @Serializable(with = UUIDSerializer::class)
    val fieldDefinitionId: UUID,
    val valueType: FieldValueType,
    val value: JsonElement? = null,
    val displayOrder: Int = 0,
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
    val recipientConfiguration: BlueprintRecipientConfiguration? = null,
)

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
    @Serializable(with = UUIDSerializer::class)
    val schemaDefinitionId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val informationRequestTemplateVersionId: UUID?,
    val informationRequestTemplateVersion: BlueprintInformationRequestTemplateVersionDto? = null,
    val exchangeDocuments: List<BlueprintDocumentConfig>,
    val participants: List<BlueprintParticipantConfig>,
    val fieldDefaults: List<BlueprintFieldDefaultConfig>,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val updatedAt: Timestamp,
)

@Serializable
data class BlueprintInformationRequestTemplateVersionDto(
    @Serializable(with = UUIDSerializer::class)
    val templateId: UUID,
    val templateDisplayName: String,
    val versionNumber: Int,
    val status: InformationRequestTemplateStatus,
    val latest: Boolean,
    val latestPublishedVersionNumber: Int? = null,
    @Serializable(with = TimestampSerializer::class)
    val retiredAt: Timestamp? = null,
)

@Serializable
data class CreateBlueprintRequest(
    val name: String,
    val summary: String? = null,
    val description: String? = null,
    val configJson: String,
    @Serializable(with = UUIDSerializer::class)
    val schemaDefinitionId: UUID? = null,
    @Serializable(with = UUIDSerializer::class)
    val informationRequestTemplateVersionId: UUID? = null,
    val exchangeDocuments: List<BlueprintDocumentConfig> = emptyList(),
    val participants: List<BlueprintParticipantConfig> = emptyList(),
    val fieldDefaults: List<BlueprintFieldDefaultConfig> = emptyList(),
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
    val exchangeDocuments: List<BlueprintDocumentConfig>? = null,
    val participants: List<BlueprintParticipantConfig>? = null,
    @Serializable(with = UUIDSerializer::class)
    val schemaDefinitionId: UUID? = null,
    val fieldDefaults: List<BlueprintFieldDefaultConfig>? = null,
    @Serializable(with = UUIDSerializer::class)
    val informationRequestTemplateVersionId: UUID? = null,
    val clearInformationRequestTemplateVersion: Boolean = false,
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
    val targetScope: String? = null,
)
