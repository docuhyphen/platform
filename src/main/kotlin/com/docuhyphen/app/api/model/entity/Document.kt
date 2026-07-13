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

    @Column(name = "upload_date", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var uploadDate: Timestamp? = null

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

    @Column(name = "restrict_type", nullable = false)
    var restrictType: Boolean = false

    @Column(name = "required", nullable = false)
    var required: Boolean = false

    @Transient
    var documentContent: ByteArray? = null

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    @JoinColumn(name = "document_id")
    var comments: MutableList<ExchangeDocumentComment> = mutableListOf()

    //ToDo: add upload reminder frequency for a document to be uploaded if not uploaded

    constructor()
}
