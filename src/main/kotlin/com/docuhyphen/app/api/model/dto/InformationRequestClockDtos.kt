package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestClockDueEffect
import com.docuhyphen.app.api.model.entity.InformationRequestClockEventKind
import com.docuhyphen.app.api.model.entity.InformationRequestClockState
import com.docuhyphen.app.api.model.entity.InformationRequestClockType
import com.docuhyphen.app.api.model.entity.InformationRequestClockUrgency
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

@Serializable
data class InformationRequestWorkingPeriodDto(
    val dayOfWeek: String,
    val startMinute: Int,
    val endMinute: Int,
)

@Serializable
data class InformationRequestClockPolicyVersionDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val versionNumber: Int,
    val clockType: InformationRequestClockType,
    val businessTimezone: String,
    val standardDurationMinutes: Int,
    val urgentDurationMinutes: Int,
    val escalationAfterMinutes: Int? = null,
    val dueEffect: InformationRequestClockDueEffect,
    val workingPeriods: List<InformationRequestWorkingPeriodDto>,
    val holidays: List<String>,
    val reminderMinutesBeforeDue: List<Int>,
    @Serializable(with = UUIDSerializer::class) val reminderCommunicationId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val overdueCommunicationId: UUID? = null,
    @Serializable(with = TimestampSerializer::class) val publishedAt: Timestamp,
)

@Serializable
data class InformationRequestClockPolicyDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val policyKey: String,
    val displayName: String,
    val ownerType: InformationRequestOwnerType,
    val versions: List<InformationRequestClockPolicyVersionDto>,
)

@Serializable
data class InformationRequestClockEventDto(
    val eventNumber: Int,
    val eventKind: InformationRequestClockEventKind,
    val dueCycle: Int,
    val pointOrdinal: Int? = null,
    val reasonCode: String? = null,
    @Serializable(with = TimestampSerializer::class) val dueAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val occurredAt: Timestamp,
)

@Serializable
data class InformationRequestClockDto(
    @Serializable(with = UUIDSerializer::class) val id: UUID,
    val clockKey: String,
    @Serializable(with = UUIDSerializer::class) val policyVersionId: UUID,
    val policyVersionNumber: Int,
    val clockType: InformationRequestClockType,
    val urgency: InformationRequestClockUrgency,
    @Serializable(with = TimestampSerializer::class) val receivedAt: Timestamp,
    val state: InformationRequestClockState,
    @Serializable(with = TimestampSerializer::class) val dueAt: Timestamp,
    val dueCycle: Int,
    val remainingSeconds: Long? = null,
    @Serializable(with = TimestampSerializer::class) val overdueAt: Timestamp? = null,
    @Serializable(with = TimestampSerializer::class) val stoppedAt: Timestamp? = null,
    val clockETag: String,
    val events: List<InformationRequestClockEventDto>,
)
