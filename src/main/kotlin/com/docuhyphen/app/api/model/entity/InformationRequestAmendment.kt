package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "information_request_amendment")
class InformationRequestAmendment
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "amendment_number", nullable = false)
    var amendmentNumber: Int = 1

    @Column(name = "from_template_version_id", nullable = false)
    lateinit var fromTemplateVersionId: UUID

    @Column(name = "to_template_version_id", nullable = false)
    lateinit var toTemplateVersionId: UUID

    @Column(name = "reason_code", length = 128)
    var reasonCode: String? = null

    @Column(name = "amended_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var amendedByPrincipalKind: PrincipalKind

    @Column(name = "amended_by_principal_id", nullable = false)
    lateinit var amendedByPrincipalId: UUID

    @Column(name = "amended_at", nullable = false)
    var amendedAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_amendment_change")
class InformationRequestAmendmentChange
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "amendment_id", nullable = false)
    lateinit var amendmentId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "template_requirement_id", nullable = false)
    lateinit var templateRequirementId: UUID

    @Column(name = "requirement_key", nullable = false, length = 128)
    lateinit var requirementKey: String

    @Column(name = "change_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var changeKind: InformationRequestAmendmentChangeKind

    @Column(name = "from_template_binding_id")
    var fromTemplateBindingId: UUID? = null

    @Column(name = "to_template_binding_id")
    var toTemplateBindingId: UUID? = null

    @Column(name = "reconfirmation_required", nullable = false)
    var reconfirmationRequired: Boolean = false

    constructor()
}

@Entity
@Table(name = "information_request_notice_intent")
class InformationRequestNoticeIntent
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "amendment_id", nullable = false)
    lateinit var amendmentId: UUID

    @Column(name = "party_id", nullable = false)
    lateinit var partyId: UUID

    @Column(name = "notice_kind", nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    var noticeKind: InformationRequestNoticeKind = InformationRequestNoticeKind.REQUIREMENTS_AMENDED

    @Column(name = "delivery_state", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var deliveryState: InformationRequestNoticeDeliveryState = InformationRequestNoticeDeliveryState.PENDING

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

enum class InformationRequestAmendmentChangeKind
{
    ADDED,
    REMOVED,
    PRESENTATION_CHANGED,
    MEANING_CHANGED,
}

enum class InformationRequestNoticeKind
{
    REQUIREMENTS_AMENDED,
}

enum class InformationRequestNoticeDeliveryState
{
    PENDING,
}
