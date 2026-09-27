package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestClockDto
import com.docuhyphen.app.api.model.dto.InformationRequestClockEventDto
import com.docuhyphen.app.api.model.dto.InformationRequestClockPolicyDto
import com.docuhyphen.app.api.model.dto.InformationRequestClockPolicyVersionDto
import com.docuhyphen.app.api.model.dto.InformationRequestWorkingPeriodDto
import com.docuhyphen.app.api.model.entity.InformationRequestClockEvent
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockPolicyVersionView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockPolicyView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestClockView

object InformationRequestClockDtoMapper
{
    fun toDto(view: InformationRequestClockPolicyView) = InformationRequestClockPolicyDto(
        id = view.policy.id,
        policyKey = view.policy.policyKey,
        displayName = view.policy.displayName,
        ownerType = view.policy.ownerType,
        versions = view.versions.map(::toDto),
    )

    fun toDto(view: InformationRequestClockPolicyVersionView) = InformationRequestClockPolicyVersionDto(
        id = view.version.id,
        versionNumber = view.version.versionNumber,
        clockType = view.version.clockType,
        businessTimezone = view.version.businessTimezone,
        standardDurationMinutes = view.version.standardDurationMinutes,
        urgentDurationMinutes = view.version.urgentDurationMinutes,
        escalationAfterMinutes = view.version.escalationAfterMinutes,
        dueEffect = view.version.dueEffect,
        workingPeriods = view.calendar.periods.map { InformationRequestWorkingPeriodDto(it.dayOfWeek.name, it.startMinute, it.endMinute) },
        holidays = view.holidays.map { it.toString() },
        reminderMinutesBeforeDue = view.reminderMinutesBeforeDue,
        reminderCommunicationId = view.version.reminderCommunicationId,
        overdueCommunicationId = view.version.overdueCommunicationId,
        publishedAt = view.version.publishedAt,
    )

    fun toDto(view: InformationRequestClockView) = InformationRequestClockDto(
        id = view.clock.id,
        clockKey = view.clock.clockKey,
        policyVersionId = view.clock.policyVersionId,
        policyVersionNumber = view.policyVersion.version.versionNumber,
        clockType = view.policyVersion.version.clockType,
        urgency = view.clock.urgency,
        receivedAt = view.clock.receivedAt,
        state = view.clock.state,
        dueAt = view.clock.dueAt,
        dueCycle = view.clock.dueCycle,
        remainingSeconds = view.clock.remainingSeconds,
        overdueAt = view.clock.overdueAt,
        stoppedAt = view.clock.stoppedAt,
        clockETag = view.clockETag,
        events = view.events.map(::toDto),
    )

    private fun toDto(event: InformationRequestClockEvent) = InformationRequestClockEventDto(
        eventNumber = event.eventNumber,
        eventKind = event.eventKind,
        dueCycle = event.dueCycle,
        pointOrdinal = event.pointOrdinal,
        reasonCode = event.reasonCode,
        dueAt = event.dueAt,
        occurredAt = event.occurredAt,
    )
}
