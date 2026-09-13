package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutation
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Append-only lifecycle history for one runtime Information Request.
 */
@Entity
@Table(name = "information_request_transition")
class InformationRequestTransition
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "sequence_number", nullable = false)
    var sequenceNumber: Int = 1

    @Column(name = "from_state", length = 32)
    @Enumerated(EnumType.STRING)
    var fromState: InformationRequestState? = null

    @Column(name = "to_state", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var toState: InformationRequestState

    @Column(name = "mutation", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    lateinit var mutation: InformationRequestMutation

    @Column(name = "actor_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var actorKind: InformationRequestTransitionActorKind

    @Column(name = "actor_id", nullable = false)
    lateinit var actorId: UUID

    @Column(name = "reason_code", length = 128)
    var reasonCode: String? = null

    @Column(name = "party_id")
    var partyId: UUID? = null

    @Column(name = "command_receipt_id")
    var commandReceiptId: UUID? = null

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
