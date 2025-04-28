package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class SharingSessionParticipantType
{
    GROUP,
    APP_USER
}

@Entity
@Table(name = "sharing_session_participant")
@Serializable
class SharingSessionParticipant {

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "participant_type", nullable = false)
    @Enumerated(EnumType.STRING)
    lateinit var participantType: SharingSessionParticipantType

    @ManyToOne
    @JoinColumn(name = "app_user_id", nullable = true)
    var appUser: AppUser? = null

    @ManyToOne
    @JoinColumn(name = "organization_group_id", nullable = true)
    var organizationGroup: OrganizationGroup? = null

    @Column(name = "added_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var addedDate: Timestamp = Timestamp.from(Instant.now())

    @ManyToOne
    @JoinColumn(name = "sharing_session_id", nullable = false)
    lateinit var sharingSession: SharingSession

    constructor()
}