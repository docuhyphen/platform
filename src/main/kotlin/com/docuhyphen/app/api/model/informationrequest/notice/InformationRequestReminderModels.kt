package com.docuhyphen.app.api.model.informationrequest.notice

import java.time.Instant
import java.util.*

data class SendInformationRequestRemindersCommand(
    val requestIds: List<UUID>,
    val idempotencyKey: String,
)

data class InformationRequestReminderResult(
    val requestId: UUID,
    val noticeCount: Int,
    val cooldownUntil: Instant? = null,
)

const val MAXIMUM_REMINDER_REQUESTS = 100
