package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "document_comment")
@Serializable
class DocumentComment {

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "comment_text", nullable = false)
    lateinit var commentText: String

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = false)
    lateinit var document: Document

    @ManyToOne
    @JoinColumn(name = "commented_by", nullable = false)
    lateinit var commentedBy: AppUser

    constructor()
}