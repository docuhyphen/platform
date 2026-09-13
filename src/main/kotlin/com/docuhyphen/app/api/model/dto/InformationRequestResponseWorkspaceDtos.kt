package com.docuhyphen.app.api.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class InformationRequestResponseWorkspaceDto(
    val request: InformationRequestDto,
    val templateVersion: InformationRequestTemplateVersionDto,
    val responseETag: String,
    val occurrences: List<InformationRequestGroupOccurrenceDto>,
    val schemaAssignment: SchemaAssignmentDto? = null,
    val responses: List<InformationRequestResponseDto> = emptyList(),
)
