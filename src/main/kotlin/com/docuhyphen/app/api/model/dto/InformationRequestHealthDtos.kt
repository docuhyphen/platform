package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthIndicatorKey
import com.docuhyphen.app.api.serializer.TimestampSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp

@Serializable
data class InformationRequestHealthIndicatorDto(
    val key: InformationRequestHealthIndicatorKey,
    val count: Long,
    val threshold: Long,
    val breached: Boolean,
)

@Serializable
data class InformationRequestHealthReportDto(
    @Serializable(with = TimestampSerializer::class) val checkedAt: Timestamp,
    val healthy: Boolean,
    val indicators: List<InformationRequestHealthIndicatorDto>,
)
