package com.docuhyphen.app.api.model.entity

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

enum class ExchangeRecipientPurpose
{
    PRIMARY,
    PARTICIPANT,
}

enum class ExchangeRecipientSelectionType
{
    REGISTERED_USER,
    EXTERNAL_EMAIL,
    INTERNAL_GROUP,
    PERSONAL_GROUP,
    TRUSTED_PERSON,
    TRUSTED_GROUP,
}

enum class ExchangeRecipientAcceptanceStatus
{
    NOT_REQUIRED,
    PENDING,
    ACCEPTED,
    REJECTED,
}

enum class ExchangeAcceptanceDecision
{
    ACCEPT,
    REJECT,
}

@Entity
@Serializable
@Table(name = "exchange_recipient")
class ExchangeRecipient
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "exchange_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var exchangeId: UUID

    @Column(name = "direct_share_id", nullable = false, unique = true)
    @Serializable(with = UUIDSerializer::class)
    lateinit var directShareId: UUID

    @Column(name = "purpose", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var purpose: ExchangeRecipientPurpose = ExchangeRecipientPurpose.PARTICIPANT

    @Column(name = "selection_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var selectionType: ExchangeRecipientSelectionType = ExchangeRecipientSelectionType.REGISTERED_USER

    @Column(name = "target_organization_id")
    @Serializable(with = UUIDSerializer::class)
    var targetOrganizationId: UUID? = null

    @Column(name = "acceptance_status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var acceptanceStatus: ExchangeRecipientAcceptanceStatus = ExchangeRecipientAcceptanceStatus.NOT_REQUIRED

    @Column(name = "accepted_or_rejected_by_app_user_id")
    @Serializable(with = UUIDSerializer::class)
    var acceptedOrRejectedByAppUserId: UUID? = null

    @Column(name = "accepted_or_rejected_at")
    @Serializable(with = TimestampSerializer::class)
    var acceptedOrRejectedAt: Timestamp? = null

    @Column(name = "created_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}
