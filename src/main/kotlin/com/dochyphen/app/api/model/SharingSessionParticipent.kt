package com.dochyphen.app.api.model

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class SharingSessionParticipantRole {
    VIEWER,
    FULL_ACCESS,
    EDITOR,
    COMMENTER,
    OWNER,
    UPLOADER,
    DOWNLOADER
}

@Entity
@Table(name = "sharing_session_participant")
@Serializable
class SharingSessionParticipant {

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @ManyToOne
    @JoinColumn(name = "app_user_id", nullable = false)
    var appUser: AppUser? = null

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    var role: SharingSessionParticipantRole = SharingSessionParticipantRole.VIEWER

    @Column(name = "added_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var addedDate: Timestamp = Timestamp.from(Instant.now())

    constructor()
}