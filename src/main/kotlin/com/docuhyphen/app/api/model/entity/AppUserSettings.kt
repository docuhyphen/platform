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
    var notifyLogin: Boolean = false

    @Column(name = "auto_preview_documents", nullable = false)
    var autoPreviewDocuments: Boolean = true

    @Column(name = "notify_share_start", nullable = false)
    var notifyShareStart: Boolean = true

    @Column(name = "notify_share_accept", nullable = false)
    var notifyShareAccept: Boolean = true

    @Column(name = "notify_share_decline", nullable = false)
    var notifyShareDecline: Boolean = true

    @Column(name = "notify_share_end", nullable = false)
    var notifyShareEnd: Boolean = false

    @Column(name = "notify_doc_comment", nullable = false)
    var notifyDocComment: Boolean = true

    @Column(name = "notify_doc_delete", nullable = false)
    var notifyDocDelete: Boolean = true

    @Column(name = "notify_doc_add", nullable = false)
    var notifyDocAdd: Boolean = true

    @Column(name = "notify_doc_upload", nullable = false)
    var notifyDocUpload: Boolean = true


    @Column(name = "notify_share_start_channels", nullable = false, length = 128)
    var notifyShareStartChannels: String = "EMAIL,IN_APP"

    @Column(name = "notify_share_accept_channels", nullable = false, length = 128)
    var notifyShareAcceptChannels: String = "EMAIL,IN_APP"

    @Column(name = "notify_share_decline_channels", nullable = false, length = 128)
    var notifyShareDeclineChannels: String = "EMAIL,IN_APP"

    @Column(name = "notify_share_end_channels", nullable = false, length = 128)
    var notifyShareEndChannels: String = ""

    @Column(name = "notify_doc_comment_channels", nullable = false, length = 128)
    var notifyDocCommentChannels: String = "EMAIL,IN_APP"

    @Column(name = "notify_doc_delete_channels", nullable = false, length = 128)
    var notifyDocDeleteChannels: String = "EMAIL,IN_APP"

    @Column(name = "notify_doc_add_channels", nullable = false, length = 128)
    var notifyDocAddChannels: String = "EMAIL,IN_APP"

    @Column(name = "notify_doc_upload_channels", nullable = false, length = 128)
    var notifyDocUploadChannels: String = "EMAIL,IN_APP"

    @Column(name = "theme", nullable = false, length = 16)
    var theme: String = "light"

    @Column(name = "tour_completed", nullable = false)
    var tourCompleted: Boolean = false

    @Column(name = "document_library_view", nullable = false, length = 10)
    var documentLibraryView: String = "cards"

    @Column(name = "blueprints_view", nullable = false, length = 10)
    var blueprintsView: String = "cards"

    @Column(name = "workflows_view", nullable = false, length = 10)
    var workflowsView: String = "cards"

    @Column(name = "sequences_view", nullable = false, length = 10)
    var sequencesView: String = "cards"

    @Column(name = "variables_view", nullable = false, length = 10)
    var variablesView: String = "cards"

    @Column(name = "communications_view", nullable = false, length = 10)
    var communicationsView: String = "cards"

    @OneToOne(mappedBy = "settings")
    @JsonIgnore
    var appUser: AppUser? = null

    constructor()
}
