package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "document_comment")
@Serializable
class SharingSessionDocumentComment
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "comment_text", length = 500, nullable = false)
    lateinit var commentText: String

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = false)
    var document: Document? = null

    @ManyToOne
    @JoinColumn(name = "commented_by_user_id", nullable = false)
    lateinit var commentedBy: AppUser

    constructor()
}