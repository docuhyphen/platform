package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.RecordPreservationScope
import com.docuhyphen.app.api.serializer.TimestampSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp

@Serializable
data class PlaceRecordPreservationHoldRequest(
    val resourceType: String,
    val resourceId: String,
    val scope: RecordPreservationScope = RecordPreservationScope.RESOURCE,
    val reason: String,
    val caseReference: String? = null,
    @Serializable(with = TimestampSerializer::class) val effectiveFrom: Timestamp? = null,
)

@Serializable
data class ChangeRecordPreservationHoldScopeRequest(
    val scope: RecordPreservationScope,
    val reason: String,
)

@Serializable
data class ReleaseRecordPreservationHoldRequest(
    val reason: String,
)

@Serializable
data class PublishRecordRetentionScheduleRequest(
    val minimumRetentionDays: Int,
    val disposalAfterDays: Int? = null,
)
