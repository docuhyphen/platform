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
import java.util.UUID

enum class ExchangeRecipientAttestationSubjectType
{
    PERSON,
    GROUP,
}

@Entity
@Serializable
@Table(name = "exchange_recipient_attestation")
class ExchangeRecipientAttestation
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "exchange_recipient_id", nullable = false, unique = true)
    @Serializable(with = UUIDSerializer::class)
    lateinit var exchangeRecipientId: UUID

    @Column(name = "relationship_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var relationshipId: UUID

    @Column(name = "caller_organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var callerOrganizationId: UUID

    @Column(name = "target_organization_id", nullable = false)
    @Serializable(with = UUIDSerializer::class)
    lateinit var targetOrganizationId: UUID

    @Column(name = "sender_policy_revision", nullable = false)
    var senderPolicyRevision: Long = 0

    @Column(name = "target_policy_revision", nullable = false)
    var targetPolicyRevision: Long = 0

    @Column(name = "subject_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var subjectType: ExchangeRecipientAttestationSubjectType = ExchangeRecipientAttestationSubjectType.GROUP

    @Column(name = "subject_app_user_id")
    @Serializable(with = UUIDSerializer::class)
    var subjectAppUserId: UUID? = null

    @Column(name = "subject_membership_id")
    @Serializable(with = UUIDSerializer::class)
    var subjectMembershipId: UUID? = null

    @Column(name = "subject_group_id")
    @Serializable(with = UUIDSerializer::class)
    var subjectGroupId: UUID? = null

    @Column(name = "invited_email_snapshot")
    var invitedEmailSnapshot: String? = null

    @Column(name = "display_name_snapshot")
    var displayNameSnapshot: String? = null

    @Column(name = "organization_name_snapshot", nullable = false)
    lateinit var organizationNameSnapshot: String

    @Column(name = "verified_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    lateinit var verifiedAt: Timestamp

    @Column(name = "verification_expires_at", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    lateinit var verificationExpiresAt: Timestamp

    @Column(name = "acceptance_verified_at")
    @Serializable(with = TimestampSerializer::class)
    var acceptanceVerifiedAt: Timestamp? = null
}
