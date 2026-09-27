package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "information_request_clock_policy")
class InformationRequestClockPolicy
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "owner_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var ownerType: InformationRequestOwnerType = InformationRequestOwnerType.ORGANIZATION

    @Column(name = "owner_organization_id")
    var ownerOrganizationId: UUID? = null

    @Column(name = "owner_user_id")
    var ownerUserId: UUID? = null

    @Column(name = "policy_key", nullable = false, length = 128)
    lateinit var policyKey: String

    @Column(name = "display_name", nullable = false, length = 255)
    lateinit var displayName: String

    @Column(name = "created_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var createdByPrincipalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "created_by_principal_id", nullable = false)
    lateinit var createdByPrincipalId: UUID

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_clock_policy_version")
class InformationRequestClockPolicyVersion
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "policy_id", nullable = false)
    lateinit var policyId: UUID

    @Column(name = "version_number", nullable = false)
    var versionNumber: Int = 1

    @Column(name = "clock_type", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var clockType: InformationRequestClockType = InformationRequestClockType.CALENDAR

    @Column(name = "business_timezone", nullable = false, length = 64)
    lateinit var businessTimezone: String

    @Column(name = "standard_duration_minutes", nullable = false)
    var standardDurationMinutes: Int = 0

    @Column(name = "urgent_duration_minutes", nullable = false)
    var urgentDurationMinutes: Int = 0

    @Column(name = "escalation_after_minutes")
    var escalationAfterMinutes: Int? = null

    @Column(name = "due_effect", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var dueEffect: InformationRequestClockDueEffect = InformationRequestClockDueEffect.MARK_OVERDUE

    @Column(name = "reminder_communication_id")
    var reminderCommunicationId: UUID? = null

    @Column(name = "overdue_communication_id")
    var overdueCommunicationId: UUID? = null

    @Column(name = "published_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var publishedByPrincipalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "published_by_principal_id", nullable = false)
    lateinit var publishedByPrincipalId: UUID

    @Column(name = "published_at", nullable = false)
    var publishedAt: Timestamp = Timestamp.from(Instant.now())
}

@Entity
@Table(name = "information_request_clock_policy_period")
class InformationRequestClockPolicyPeriod
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "policy_version_id", nullable = false)
    lateinit var policyVersionId: UUID

    @Column(name = "day_of_week", nullable = false)
    var dayOfWeek: Short = 1

    @Column(name = "start_minute", nullable = false)
    var startMinute: Int = 0

    @Column(name = "end_minute", nullable = false)
    var endMinute: Int = 0
}

@Entity
@Table(name = "information_request_clock_policy_holiday")
class InformationRequestClockPolicyHoliday
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "policy_version_id", nullable = false)
    lateinit var policyVersionId: UUID

    @Column(name = "holiday_date", nullable = false)
    lateinit var holidayDate: LocalDate
}

@Entity
@Table(name = "information_request_clock_policy_reminder")
class InformationRequestClockPolicyReminder
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "policy_version_id", nullable = false)
    lateinit var policyVersionId: UUID

    @Column(name = "reminder_ordinal", nullable = false)
    var reminderOrdinal: Int = 1

    @Column(name = "minutes_before_due", nullable = false)
    var minutesBeforeDue: Int = 0
}

@Entity
@Table(name = "information_request_clock")
class InformationRequestClock
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "clock_key", nullable = false, length = 64)
    lateinit var clockKey: String

    @Column(name = "policy_version_id", nullable = false)
    lateinit var policyVersionId: UUID

    @Column(name = "urgency", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var urgency: InformationRequestClockUrgency = InformationRequestClockUrgency.STANDARD

    @Column(name = "received_at", nullable = false)
    lateinit var receivedAt: Timestamp

    @Column(name = "state", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var state: InformationRequestClockState = InformationRequestClockState.RUNNING

    @Column(name = "due_at", nullable = false)
    lateinit var dueAt: Timestamp

    @Column(name = "due_cycle", nullable = false)
    var dueCycle: Int = 0

    @Column(name = "remaining_seconds")
    var remainingSeconds: Long? = null

    @Column(name = "next_point_at")
    var nextPointAt: Timestamp? = null

    @Column(name = "overdue_at")
    var overdueAt: Timestamp? = null

    @Column(name = "stopped_at")
    var stoppedAt: Timestamp? = null

    @Column(name = "clock_revision", nullable = false)
    var clockRevision: Long = 1

    @Column(name = "started_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var startedByPrincipalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "started_by_principal_id", nullable = false)
    lateinit var startedByPrincipalId: UUID

    @Column(name = "started_at", nullable = false)
    lateinit var startedAt: Timestamp
}

@Entity
@Table(name = "information_request_clock_event")
class InformationRequestClockEvent
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "clock_id", nullable = false)
    lateinit var clockId: UUID

    @Column(name = "information_request_id", nullable = false)
    lateinit var informationRequestId: UUID

    @Column(name = "event_number", nullable = false)
    var eventNumber: Int = 1

    @Column(name = "event_kind", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var eventKind: InformationRequestClockEventKind = InformationRequestClockEventKind.STARTED

    @Column(name = "due_cycle", nullable = false)
    var dueCycle: Int = 0

    @Column(name = "point_ordinal")
    var pointOrdinal: Int? = null

    @Column(name = "reason_code", length = 128)
    var reasonCode: String? = null

    @Column(name = "inputs_json", nullable = false, columnDefinition = "text")
    var inputsJson: String = "{}"

    @Column(name = "due_at")
    var dueAt: Timestamp? = null

    @Column(name = "occurred_at", nullable = false)
    lateinit var occurredAt: Timestamp

    @Column(name = "recorded_by_principal_kind", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    var recordedByPrincipalKind: PrincipalKind = PrincipalKind.SERVICE_ACCOUNT

    @Column(name = "recorded_by_principal_id", nullable = false)
    lateinit var recordedByPrincipalId: UUID
}
