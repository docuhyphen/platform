package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.util.UUID

// ── Blueprint config sub-types ────────────────────────────────────────────────

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
    // Descriptive business label for the recipient (e.g. "New Employee", "Counterparty"), not an
    // authorization role. It is never consumed for access control; keep it free text so blueprints
    // can carry human-readable recipient labels. The authorization role is chosen at Exchange
    // initiation and validated against [ExchangeShareRoleName] there.
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

/**
 * One default field value carried by a blueprint. Keyed by the stable [fieldDefinitionId] so it
 * survives schema re-publishing; [value] is the canonical JSON form, [valueType] the authoring-time
 * type. Applied through the creation-time schema/values seam when an Exchange is started.
 */
@Serializable
data class BlueprintFieldDefaultConfig(
    @Serializable(with = UUIDSerializer::class)
    val fieldDefinitionId: UUID,
    val valueType: FieldValueType,
    val value: JsonElement? = null,
    val displayOrder: Int = 0,
)

/**
 * The scalar form-prefill settings stored in `blueprint_definition.config_json`. Document and
 * participant defaults are NOT here; they are normalized into `blueprint_document_default` /
 * `blueprint_participant_default` and exposed as typed arrays on [BlueprintDefinitionDto].
 */
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
    @Serializable(with = UUIDSerializer::class)
    val schemaDefinitionId: UUID?,
    val exchangeDocuments: List<BlueprintDocumentConfig>,
    val participants: List<BlueprintParticipantConfig>,
    val fieldDefaults: List<BlueprintFieldDefaultConfig>,
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
    @Serializable(with = UUIDSerializer::class)
    val schemaDefinitionId: UUID? = null,
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
    // null = leave child collection unchanged; a list (incl. empty) replaces it.
    val exchangeDocuments: List<BlueprintDocumentConfig>? = null,
    val participants: List<BlueprintParticipantConfig>? = null,
    // When fieldDefaults is non-null the schema linkage is also (re)applied from schemaDefinitionId
    // (which may be null to clear the schema); when fieldDefaults is null both are left unchanged.
    @Serializable(with = UUIDSerializer::class)
    val schemaDefinitionId: UUID? = null,
    val fieldDefaults: List<BlueprintFieldDefaultConfig>? = null,
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
