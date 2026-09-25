package com.docuhyphen.app.api.model.entity

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
 * Runtime aggregate for one request attached to an Exchange and one exact published Template
 * Version. The aggregate revision is used for lifecycle commands, while the party revision is used
 * for party and access-shaping commands.
 */
@Entity
@Table(name = "information_request")
class InformationRequest
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "exchange_id", nullable = false)
    lateinit var exchangeId: UUID

    @Column(name = "template_version_id", nullable = false)
    lateinit var templateVersionId: UUID

    @Column(name = "owner_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var ownerType: InformationRequestOwnerType = InformationRequestOwnerType.ORGANIZATION

    @Column(name = "owner_organization_id")
    var ownerOrganizationId: UUID? = null

    @Column(name = "owner_user_id")
    var ownerUserId: UUID? = null

    @Column(name = "state", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var state: InformationRequestState = InformationRequestState.DRAFT

    @Column(name = "gates_exchange_closure", nullable = false)
    var gatesExchangeClosure: Boolean = true

    @Column(name = "aggregate_revision", nullable = false)
    var aggregateRevision: Long = 1

    @Column(name = "party_revision", nullable = false)
    var partyRevision: Long = 1

    @Column(name = "response_revision", nullable = false)
    var responseRevision: Long = 1

    @Column(name = "created_by_app_user_id")
    var createdByAppUserId: UUID? = null

    @Column(name = "issued_at")
    var issuedAt: Timestamp? = null

    @Column(name = "closed_at")
    var closedAt: Timestamp? = null

    @Column(name = "satisfied_at")
    var satisfiedAt: Timestamp? = null

    @Column(name = "satisfied_by_package_id")
    var satisfiedByPackageId: UUID? = null

    @Column(name = "cancelled_at")
    var cancelledAt: Timestamp? = null

    @Column(name = "superseded_at")
    var supersededAt: Timestamp? = null

    @Column(name = "superseded_by_request_id")
    var supersededByRequestId: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}
