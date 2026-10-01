package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class InformationRequestReminderResultDto(
    @Serializable(with = UUIDSerializer::class) val requestId: UUID,
    val noticeCount: Int,
    val cooldownUntil: String? = null,
)
