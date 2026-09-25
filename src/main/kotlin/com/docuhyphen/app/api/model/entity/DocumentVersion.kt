package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.model.document.DocumentVersionContentHashAlgorithm
import com.docuhyphen.app.api.model.document.DocumentVersionContentVerification
import com.docuhyphen.app.api.model.document.DocumentVersionLocatorKind
import com.docuhyphen.app.api.model.document.DocumentVersionStorageProvider
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

    @Column(name = "storage_provider", nullable = false)
    @Enumerated(EnumType.STRING)
    lateinit var storageProvider: DocumentVersionStorageProvider

    @Column(name = "storage_locator_kind", nullable = false)
    @Enumerated(EnumType.STRING)
    lateinit var storageLocatorKind: DocumentVersionLocatorKind

    @Column(name = "storage_locator", nullable = false)
    lateinit var storageLocator: String

    @Column(nullable = false, name = "version")
    lateinit var version: String

    @Column(nullable = false, name = "created_date")
    @Serializable(with = TimestampSerializer::class)
    lateinit var createdDate: Timestamp

    @Column(name = "created_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var createdByPrincipalKind: PrincipalKind

    @Column(name = "created_by_principal_id", nullable = false)
    lateinit var createdByPrincipalId: UUID

    @Column(name = "content_length", nullable = false)
    var contentLength: Long = 0

    @Column(name = "content_hash_algorithm", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var contentHashAlgorithm: DocumentVersionContentHashAlgorithm

    @Column(name = "content_hash", nullable = false, length = 128)
    lateinit var contentHash: String

    @Column(name = "content_verification", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var contentVerification: DocumentVersionContentVerification
}