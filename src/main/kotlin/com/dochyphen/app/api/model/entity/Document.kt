package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class DocumentEncryptionMode
{
    INTERNAL,
    END_TO_END,
}

enum class DocumentType
{
    WORD,
    PDF,
    DOCX,
    DOC,
    XLSX,
    PPTX,
    PNG,
    JPG;

    companion object
    {
        fun fromFileExtension(extension: String): DocumentType?
        {
            return when (extension)
            {
                ".pdf" -> PDF
                ".docx" -> DOCX
                ".doc" -> DOC
                ".xlsx" -> XLSX
                ".pptx" -> PPTX
                ".png" -> PNG
                ".jpg" -> JPG
                else -> null
            }
        }

        fun toFileExtension(documentType: DocumentType): String
        {
            return when (documentType)
            {
                PDF -> ".pdf"
                DOCX -> ".docx"
                DOC -> ".doc"
                XLSX -> ".xlsx"
                PPTX -> ".pptx"
                PNG -> ".png"
                JPG -> ".jpg"
                else -> ""
            }
        }
    }
}

enum class RequiredDocumentType
{
    PDF,
    WORD,
    IMAGE,
}

@Entity
@Serializable
@Table(name = "document")
class Document
{

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = false

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "update_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var updateDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "upload_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var uploadDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "title", nullable = false)
    var title: String = ""

    @Column(name = "type", nullable = true)
    @Enumerated(EnumType.STRING)
    var type: DocumentType? = null

    @Column(name = "hash", nullable = true)
    var hash: String = "" // For file integrity verification (e.g., SHA256)

    @OneToOne
    @JoinColumn(name = "last_updated_by_id")
    var lastUpdatedBy: AppUser? = null

    @Column(name = "encryption_mode", nullable = false)
    var encryptionMode: DocumentEncryptionMode = DocumentEncryptionMode.INTERNAL

    @Column(name = "restricted_type", nullable = true)
    var restrictedType: DocumentType? = null

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    @JoinColumn(name = "document_id")
    var auditLogs: MutableList<DocumentAuditLog> = mutableListOf()

    @Transient
    var documentContent: ByteArray? = null

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    @JoinColumn(name = "document_id")
    var comments: MutableList<DocumentComment> = mutableListOf()

    constructor()
}