package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.BlueprintDefinition
import com.docuhyphen.app.api.model.informationrequest.template.InformationRequestTemplateVersionDescription

object BlueprintDefinitionDtoMapper {
    @Suppress("LongParameterList")
    fun toDto(
        blueprint: BlueprintDefinition,
        generalTags: List<String>,
        exchangeDocuments: List<BlueprintDocumentConfig>,
        participants: List<BlueprintParticipantConfig>,
        fieldDefaults: List<BlueprintFieldDefaultConfig>,
        templateVersion: InformationRequestTemplateVersionDescription?,
    ): BlueprintDefinitionDto = BlueprintDefinitionDto(
        id = blueprint.id,
        name = blueprint.name,
        summary = blueprint.summary,
        description = blueprint.description,
        scope = blueprint.scope.name,
        organizationId = blueprint.organizationId,
        createdByAppUserId = blueprint.createdByAppUserId,
        isActive = blueprint.isActive,
        isPublished = blueprint.isPublished,
        isTemplate = blueprint.isTemplate,
        generalTags = generalTags,
        sourceTemplateId = blueprint.sourceTemplateId,
        configJson = blueprint.configJson,
        schemaDefinitionId = blueprint.schemaDefinitionId,
        informationRequestTemplateVersionId = blueprint.informationRequestTemplateVersionId,
        informationRequestTemplateVersion = templateVersion?.let(::toDto),
        exchangeDocuments = exchangeDocuments,
        participants = participants,
        fieldDefaults = fieldDefaults,
        createdAt = blueprint.createdAt,
        updatedAt = blueprint.updatedAt,
    )

    fun toDto(description: InformationRequestTemplateVersionDescription): BlueprintInformationRequestTemplateVersionDto =
        BlueprintInformationRequestTemplateVersionDto(
            templateId = description.templateDefinitionId,
            templateDisplayName = description.templateDisplayName,
            versionNumber = description.versionNumber,
            status = description.status,
            latest = description.latest,
            latestPublishedVersionNumber = description.latestPublishedVersionNumber,
            retiredAt = description.retiredAt,
        )
}
