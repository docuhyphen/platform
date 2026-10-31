package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "participant_account_link")
class ParticipantAccountLink
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "participant_id", nullable = false, unique = true)
    lateinit var participantId: UUID

    @Column(name = "app_user_id", nullable = false)
    lateinit var appUserId: UUID

    @Column(name = "linked_via_information_request_id")
    var linkedViaInformationRequestId: UUID? = null

    @Column(name = "linked_via_share_link_id")
    var linkedViaShareLinkId: UUID? = null

    @Column(name = "linked_at", nullable = false)
    var linkedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
