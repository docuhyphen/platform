package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Entity
@Table(name = "document_version")
class DocumentVersion
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    lateinit var document: Document

    @Column(name = "file_name", nullable = false)
    lateinit var fileName: String

    @Column(nullable = false, name = "storage_path")
    lateinit var storagePath: String

    @Column(nullable = false, name = "version")
    lateinit var version: String

    @Column(nullable = false, name = "created_date")
    @Serializable(with = TimestampSerializer::class)
    lateinit var createdDate: Timestamp

    @Column(nullable = true)
    var createdByEmail: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    var createdBy: AppUser? = null
}