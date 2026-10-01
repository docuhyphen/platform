package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class InformationRequestNoticeChannel
{
    EMAIL,
}

enum class InformationRequestNoticeEndpointState
{
    RESOLVED,
    MISSING,
}

enum class InformationRequestNoticeSourceKind
{
    COMMUNICATION,
    PLATFORM_DEFAULT,
}

enum class InformationRequestNoticeAttemptOutcome
{
    DELIVERED,
    FAILED,
    SKIPPED,
}

@Entity
@Table(name = "information_request_notice_claim")
class InformationRequestNoticeClaim
{
    @Id
    @Column(name = "notice_intent_id")
    lateinit var noticeIntentId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "claimed_by", nullable = false, length = 128)
    lateinit var claimedBy: String

    @Column(name = "claimed_at", nullable = false)
    var claimedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_outbound_notice")
class InformationRequestOutboundNotice
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "notice_intent_id", nullable = false)
    lateinit var noticeIntentId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "party_id", nullable = false)
    lateinit var partyId: UUID

    @Column(name = "recipient_principal_kind", length = 32)
    @Enumerated(EnumType.STRING)
    var recipientPrincipalKind: PrincipalKind? = null

    @Column(name = "recipient_principal_id")
    var recipientPrincipalId: UUID? = null

    @Column(name = "channel", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var channel: InformationRequestNoticeChannel = InformationRequestNoticeChannel.EMAIL

    @Column(name = "recipient_endpoint", length = 320)
    var recipientEndpoint: String? = null

    @Column(name = "endpoint_state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var endpointState: InformationRequestNoticeEndpointState = InformationRequestNoticeEndpointState.MISSING

    @Column(name = "rendered_subject", nullable = false, columnDefinition = "text")
    lateinit var renderedSubject: String

    @Column(name = "rendered_body", nullable = false, columnDefinition = "text")
    lateinit var renderedBody: String

    @Column(name = "content_hash_algorithm", nullable = false, length = 16)
    var contentHashAlgorithm: String = "SHA_256"

    @Column(name = "rendered_content_hash", nullable = false, length = 64)
    lateinit var renderedContentHash: String

    @Column(name = "source_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var sourceKind: InformationRequestNoticeSourceKind = InformationRequestNoticeSourceKind.PLATFORM_DEFAULT

    @Column(name = "source_communication_id")
    var sourceCommunicationId: UUID? = null

    @Column(name = "source_content_hash", nullable = false, length = 64)
    lateinit var sourceContentHash: String

    @Column(name = "idempotency_key", nullable = false, length = 256)
    lateinit var idempotencyKey: String

    @Column(name = "rendered_at", nullable = false)
    var renderedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_notice_sequence_allocation")
class InformationRequestNoticeSequenceAllocation
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "outbound_notice_id", nullable = false)
    lateinit var outboundNoticeId: UUID

    @Column(name = "sequence_key", nullable = false, length = 64)
    lateinit var sequenceKey: String

    @Column(name = "allocated_value", nullable = false)
    var allocatedValue: Long = 0

    @Column(name = "rendered_value", nullable = false, columnDefinition = "text")
    lateinit var renderedValue: String
}

@Entity
@Table(name = "information_request_notice_delivery_attempt")
class InformationRequestNoticeDeliveryAttempt
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "outbound_notice_id", nullable = false)
    lateinit var outboundNoticeId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "attempt_number", nullable = false)
    var attemptNumber: Int = 1

    @Column(name = "channel", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var channel: InformationRequestNoticeChannel = InformationRequestNoticeChannel.EMAIL

    @Column(name = "outcome", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var outcome: InformationRequestNoticeAttemptOutcome = InformationRequestNoticeAttemptOutcome.FAILED

    @Column(name = "failure_code", length = 128)
    var failureCode: String? = null

    @Column(name = "attempted_at", nullable = false)
    var attemptedAt: Timestamp = Timestamp.from(Instant.now())
}
