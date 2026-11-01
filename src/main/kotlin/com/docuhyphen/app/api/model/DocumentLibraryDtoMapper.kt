package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.DocumentLibraryEntryDto
import com.docuhyphen.app.api.model.dto.DocumentLibraryEntrySummaryDto
import com.docuhyphen.app.api.model.entity.DocumentLibraryEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object DocumentLibraryDtoMapper
{
    private val json = Json { ignoreUnknownKeys = true }

    fun toDto(entry: DocumentLibraryEntry): DocumentLibraryEntryDto = DocumentLibraryEntryDto(
        id = entry.id,
        title = entry.title,
        description = entry.description,
        scope = entry.scope.name,
        organizationId = entry.organizationId,
        createdByAppUserId = entry.createdByAppUserId,
        documentType = entry.documentType,
        fileName = entry.fileName,
        fileSizeBytes = entry.fileSizeBytes,
        contentHash = entry.contentHash,
        isPublished = entry.isPublished,
        isActive = entry.isActive,
        hasFile = entry.storagePath != null,
        restrictType = entry.restrictType,
        restrictedType = entry.restrictedType,
        required = entry.required,
        generalTags = decodeTags(entry.generalTags),
        sourceDocumentId = entry.sourceDocumentId,
        createdAt = entry.createdAt,
        updatedAt = entry.updatedAt,
    )

    fun toSummaryDto(entry: DocumentLibraryEntry): DocumentLibraryEntrySummaryDto = DocumentLibraryEntrySummaryDto(
        id = entry.id,
        title = entry.title,
        description = entry.description,
        scope = entry.scope.name,
        organizationId = entry.organizationId,
        createdByAppUserId = entry.createdByAppUserId,
        documentType = entry.documentType,
        fileName = entry.fileName,
        fileSizeBytes = entry.fileSizeBytes,
        isPublished = entry.isPublished,
        isActive = entry.isActive,
        hasFile = entry.storagePath != null,
        restrictType = entry.restrictType,
        restrictedType = entry.restrictedType,
        required = entry.required,
        generalTags = decodeTags(entry.generalTags),
        sourceDocumentId = entry.sourceDocumentId,
        createdAt = entry.createdAt,
        updatedAt = entry.updatedAt,
    )

    private fun decodeTags(tagsJson: String): List<String> =
        runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), tagsJson)
        }.getOrDefault(emptyList())
}
