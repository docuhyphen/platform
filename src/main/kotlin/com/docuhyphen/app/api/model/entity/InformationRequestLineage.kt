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
@Table(name = "information_request_lineage")
class InformationRequestLineage
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "successor_request_id", nullable = false)
    lateinit var successorRequestId: UUID

    @Column(name = "source_request_id", nullable = false)
    lateinit var sourceRequestId: UUID

    @Column(name = "source_package_id")
    var sourcePackageId: UUID? = null

    @Column(name = "lineage_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var lineageKind: InformationRequestLineageKind

    @Column(name = "recurrence_id")
    var recurrenceId: UUID? = null

    @Column(name = "recurrence_sequence")
    var recurrenceSequence: Int? = null

    @Column(name = "refresh_rule_id")
    var refreshRuleId: UUID? = null

    @Column(name = "reason_code", length = 128)
    var reasonCode: String? = null

    @Column(name = "created_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var createdByPrincipalKind: PrincipalKind

    @Column(name = "created_by_principal_id", nullable = false)
    lateinit var createdByPrincipalId: UUID

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_carry_forward")
class InformationRequestCarryForward
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "lineage_id", nullable = false)
    lateinit var lineageId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "information_request_requirement_id", nullable = false)
    lateinit var informationRequestRequirementId: UUID

    @Column(name = "source_package_id", nullable = false)
    lateinit var sourcePackageId: UUID

    @Column(name = "source_item_id", nullable = false)
    lateinit var sourceItemId: UUID

    @Column(name = "decision", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var decision: InformationRequestCarryForwardDecision

    @Column(name = "reason_code", length = 64)
    var reasonCode: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_recurrence")
class InformationRequestRecurrence
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "origin_request_id", nullable = false)
    lateinit var originRequestId: UUID

    @Column(name = "interval_unit", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    lateinit var intervalUnit: InformationRequestRecurrenceUnit

    @Column(name = "interval_count", nullable = false)
    var intervalCount: Int = 1

    @Column(name = "first_due_at", nullable = false)
    lateinit var firstDueAt: Timestamp

    @Column(name = "maximum_occurrences")
    var maximumOccurrences: Int? = null

    @Column(name = "created_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var createdByPrincipalKind: PrincipalKind

    @Column(name = "created_by_principal_id", nullable = false)
    lateinit var createdByPrincipalId: UUID

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

@Entity
@Table(name = "information_request_refresh_rule")
class InformationRequestRefreshRule
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "requirement_key", nullable = false, length = 128)
    lateinit var requirementKey: String

    @Column(name = "lead_days", nullable = false)
    var leadDays: Int = 0

    @Column(name = "created_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    lateinit var createdByPrincipalKind: PrincipalKind

    @Column(name = "created_by_principal_id", nullable = false)
    lateinit var createdByPrincipalId: UUID

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())

    constructor()
}

enum class InformationRequestLineageKind
{
    SUPPLEMENT,
    RECURRENCE,
    REFRESH,
    SUPERSEDING,
}

enum class InformationRequestCarryForwardDecision
{
    OFFERED,
    INVALIDATED,
}

enum class InformationRequestRecurrenceUnit
{
    DAY,
    WEEK,
    MONTH,
    YEAR,
}
