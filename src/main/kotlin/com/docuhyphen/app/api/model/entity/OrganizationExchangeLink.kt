package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

enum class LinkStatus
{
    PENDING,
    ACCEPTED,
    REJECTED,
}

@Entity
@Serializable
@Table(name = "organization_exchange_link")
class OrganizationExchangeLink
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requesting_organization_id", nullable = false)
    var requestingOrganization: Organization? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_organization_id", nullable = false)
    var requestedOrganization: Organization? = null

    @Column(name = "requesting_message", nullable = true)
    var requestingMessage: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: LinkStatus = LinkStatus.PENDING

    @Column(name = "linked_date", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var linkedDate: Timestamp? = null

    @Column(name = "rejected_date", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var rejectedDate: Timestamp? = null

    @Column(name = "rejection_reason")
    var rejectionReason: String? = null

}