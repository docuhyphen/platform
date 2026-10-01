package com.docuhyphen.app.api.model.informationrequest.clock

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.service.command.CommandPrecondition
import java.time.Instant
import java.time.LocalDate
import java.util.*

data class InformationRequestClockPolicyDefinition(
    val clockType: InformationRequestClockType,
    val businessTimezone: String,
    val workingPeriods: List<InformationRequestWorkingPeriod>,
    val holidays: List<LocalDate>,
    val standardDurationMinutes: Int,
    val urgentDurationMinutes: Int,
    val reminderMinutesBeforeDue: List<Int>,
    val escalationAfterMinutes: Int?,
    val dueEffect: InformationRequestClockDueEffect,
    val reminderCommunicationId: UUID? = null,
    val overdueCommunicationId: UUID? = null,
)

data class DefineInformationRequestClockPolicyCommand(
    val policyKey: String,
    val displayName: String,
    val definition: InformationRequestClockPolicyDefinition,
)

data class PublishInformationRequestClockPolicyVersionCommand(
    val policyId: UUID,
    val definition: InformationRequestClockPolicyDefinition,
)

data class InformationRequestClockPolicyVersionView(
    val version: InformationRequestClockPolicyVersion,
    val calendar: InformationRequestClockCalendar,
    val holidays: List<LocalDate>,
    val reminderMinutesBeforeDue: List<Int>,
)

data class InformationRequestClockPolicyView(
    val policy: InformationRequestClockPolicy,
    val versions: List<InformationRequestClockPolicyVersionView>,
)

data class StartInformationRequestClockCommand(
    val requestId: UUID,
    val clockKey: String,
    val policyVersionId: UUID,
    val urgency: InformationRequestClockUrgency,
    val receivedAt: Instant?,
    val access: RequestAccessContext,
    val idempotencyKey: String,
)

enum class InformationRequestClockChange
{
    PAUSE,
    RESUME,
    EXTEND,
}

data class ChangeInformationRequestClockCommand(
    val requestId: UUID,
    val clockId: UUID,
    val change: InformationRequestClockChange,
    val extensionMinutes: Int?,
    val reasonCode: String?,
    val access: RequestAccessContext,
    val precondition: CommandPrecondition,
    val idempotencyKey: String,
)

data class InformationRequestClockView(
    val clock: InformationRequestClock,
    val policyVersion: InformationRequestClockPolicyVersionView,
    val events: List<InformationRequestClockEvent>,
    val clockETag: String,
)

data class InformationRequestClockPoint(
    val kind: InformationRequestClockEventKind,
    val at: Instant,
    val ordinal: Int?,
)
