package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "app_user_settings")
class AppUserSettings
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var updatedDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "notify_login", nullable = false)
    var notifyLogin: Boolean = true

    @Column(name = "auto_preview_documents", nullable = false)
    var autoPreviewDocuments: Boolean = true

    @Column(name = "notify_share_start", nullable = false)
    var notifyShareStart: Boolean = true

    @Column(name = "notify_share_accept", nullable = false)
    var notifyShareAccept: Boolean = true

    @Column(name = "notify_share_decline", nullable = false)
    var notifyShareDecline: Boolean = true

    @Column(name = "notify_share_end", nullable = false)
    var notifyShareEnd: Boolean = true

    @Column(name = "notify_doc_comment", nullable = false)
    var notifyDocComment: Boolean = true

    @Column(name = "notify_doc_delete", nullable = false)
    var notifyDocDelete: Boolean = true

    @Column(name = "notify_doc_add", nullable = false)
    var notifyDocAdd: Boolean = true

    @Column(name = "notify_doc_upload", nullable = false)
    var notifyDocUpload: Boolean = true

    @Column(name = "theme", nullable = false, length = 16)
    var theme: String = "light"

    @Column(name = "tour_completed", nullable = false)
    var tourCompleted: Boolean = false

    @OneToOne(mappedBy = "settings")
    @JsonIgnore
    var appUser: AppUser? = null

    constructor()
}
