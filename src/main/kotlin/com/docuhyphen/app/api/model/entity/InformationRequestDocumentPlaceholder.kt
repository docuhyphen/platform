package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "information_request_document_placeholder")
class InformationRequestDocumentPlaceholder
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "source_blueprint_document_default_id", nullable = false)
    lateinit var sourceBlueprintDocumentDefaultId: UUID

    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Column(name = "restricted_type", nullable = true, length = 16)
    var restrictedType: String? = null

    @Column(name = "restrict_type", nullable = false)
    var restrictType: Boolean = false

    @Column(name = "required", nullable = false)
    var required: Boolean = false

    @Column(name = "library_document_id", nullable = true)
    var libraryDocumentId: UUID? = null

    @Column(name = "library_title", nullable = true)
    var libraryTitle: String? = null

    @Column(name = "library_description", nullable = true, length = 1024)
    var libraryDescription: String? = null

    @Column(name = "library_document_type", nullable = true, length = 64)
    var libraryDocumentType: String? = null

    @Column(name = "library_file_name", nullable = true, length = 512)
    var libraryFileName: String? = null

    @Column(name = "library_file_size_bytes", nullable = true)
    var libraryFileSizeBytes: Long? = null

    @Column(name = "library_content_hash", nullable = true, length = 128)
    var libraryContentHash: String? = null

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
