package com.docuhyphen.app.api.model.informationrequest

import java.util.UUID

data class SendInformationRequestRemindersCommand(
    val requestIds: List<UUID>,
    val idempotencyKey: String,
)

data class InformationRequestReminderResult(
    val requestId: UUID,
    val noticeCount: Int,
)

const val MAXIMUM_REMINDER_REQUESTS = 100
