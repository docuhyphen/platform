package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Durable proof that an [ExternalParticipant] has completed a verified registration as the named
 * [AppUser]. Recorded once per participant; the participant's earlier Shares, request parties, and
 * ShareLinks are never rewritten or reattributed once this link exists, so history stays intact
 * regardless of which Information Request first triggered the upgrade.
 */
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

    /** The Information Request whose party proved contact and triggered this upgrade. */
    @Column(name = "linked_via_information_request_id", nullable = false)
    lateinit var linkedViaInformationRequestId: UUID

    /** The bootstrap ShareLink whose session proved contact and triggered this upgrade. */
    @Column(name = "linked_via_share_link_id", nullable = false)
    lateinit var linkedViaShareLinkId: UUID

    @Column(name = "linked_at", nullable = false)
    var linkedAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
