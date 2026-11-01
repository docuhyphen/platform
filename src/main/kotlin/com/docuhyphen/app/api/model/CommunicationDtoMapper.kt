package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.CommunicationDto
import com.docuhyphen.app.api.model.entity.Communication
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object CommunicationDtoMapper
{
    private val json = Json { ignoreUnknownKeys = true }

    fun toDto(communication: Communication): CommunicationDto = CommunicationDto(
        id = communication.id,
        name = communication.name,
        summary = communication.summary,
        description = communication.description,
        scope = communication.scope.name,
        organizationId = communication.organizationId,
        createdByAppUserId = communication.createdByAppUserId,
        subject = communication.subject,
        body = communication.body,
        generalTags = decodeTags(communication.generalTags),
        isActive = communication.isActive,
        isPublished = communication.isPublished,
        isTemplate = communication.isTemplate,
        sourceTemplateId = communication.sourceTemplateId,
        createdAt = communication.createdAt,
        updatedAt = communication.updatedAt,
    )

    private fun decodeTags(tagsJson: String): List<String> =
        runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), tagsJson)
        }.getOrDefault(emptyList())
}
