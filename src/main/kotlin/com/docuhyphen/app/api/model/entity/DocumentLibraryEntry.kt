package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "document_library")
class DocumentLibraryEntry
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Column(name = "description", nullable = true, length = 1024)
    var description: String? = null

    @Column(name = "scope", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var scope: BlueprintScope = BlueprintScope.PERSONAL

    @Column(name = "organization_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var organizationId: UUID? = null

    @Column(name = "created_by_app_user_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var createdByAppUserId: UUID? = null

    @Column(name = "document_type", nullable = true, length = 16)
    var documentType: String? = null

    @Column(name = "file_name", nullable = true, length = 512)
    var fileName: String? = null

    @Column(name = "file_size_bytes", nullable = true)
    var fileSizeBytes: Long? = null

    @Column(name = "storage_path", nullable = true, length = 1024)
    var storagePath: String? = null

    @Column(name = "content_hash", nullable = true, length = 128)
    var contentHash: String? = null

    @Column(name = "is_published", nullable = false)
    var isPublished: Boolean = false

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    @Column(name = "source_document_id", nullable = true)
    @Serializable(with = UUIDSerializer::class)
    var sourceDocumentId: UUID? = null

    @Column(name = "restrict_type", nullable = false)
    var restrictType: Boolean = false

    @Column(name = "restricted_type", nullable = true, length = 16)
    var restrictedType: String? = null

    @Column(name = "required", nullable = false)
    var required: Boolean = false

    @Column(name = "general_tags", nullable = false, columnDefinition = "text")
    var generalTags: String = "[]"

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
