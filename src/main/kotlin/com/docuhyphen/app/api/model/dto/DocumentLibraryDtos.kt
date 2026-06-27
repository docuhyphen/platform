package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class DocumentLibraryEntryDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val title: String,
    val description: String?,
    val scope: String,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val createdByAppUserId: UUID?,
    val documentType: String?,
    val fileName: String?,
    val fileSizeBytes: Long?,
    val contentHash: String?,
    val isPublished: Boolean,
    val isActive: Boolean,
    val hasFile: Boolean,
    val restrictType: Boolean = false,
    val restrictedType: String? = null,
    val required: Boolean = false,
    val generalTags: List<String>,
    @Serializable(with = UUIDSerializer::class)
    val sourceDocumentId: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val updatedAt: Timestamp,
)

@Serializable
data class DocumentLibraryEntrySummaryDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val title: String,
    val description: String?,
    val scope: String,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val createdByAppUserId: UUID?,
    val documentType: String?,
    val fileName: String?,
    val fileSizeBytes: Long?,
    val isPublished: Boolean,
    val isActive: Boolean,
    val hasFile: Boolean,
    val restrictType: Boolean = false,
    val restrictedType: String? = null,
    val required: Boolean = false,
    val generalTags: List<String>,
    @Serializable(with = UUIDSerializer::class)
    val sourceDocumentId: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val updatedAt: Timestamp,
)

@Serializable
data class CreateDocumentLibraryEntryRequest(
    val title: String,
    val description: String? = null,
    val generalTags: List<String> = emptyList(),
    val scope: String? = null,
    val restrictType: Boolean = false,
    val restrictedType: String? = null,
    val required: Boolean = false,
)

@Serializable
data class UpdateDocumentLibraryEntryRequest(
    val title: String? = null,
    val description: String? = null,
    val generalTags: List<String>? = null,
    val restrictType: Boolean? = null,
    val restrictedType: String? = null,
    val required: Boolean? = null,
)

@Serializable
data class CloneDocumentLibraryEntryRequest(
    val newName: String? = null,
    val targetScope: String? = null,
)

@Serializable
data class PatchDocumentLibraryStatusRequest(
    val isActive: Boolean,
)

@Serializable
data class PatchDocumentLibraryPublishedRequest(
    val isPublished: Boolean,
)
