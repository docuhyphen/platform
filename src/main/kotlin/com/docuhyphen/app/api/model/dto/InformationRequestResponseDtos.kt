package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestResponseEditLock
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class InformationRequestResponseDto(
    @Serializable(with = UUIDSerializer::class) val informationRequestRequirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourceTemplateRequirementId: UUID,
    @Serializable(with = UUIDSerializer::class) val sourceTemplateBindingId: UUID,
    val occurrencePath: String,
    val disposition: InformationRequestResponseDisposition,
    val narrative: String? = null,
    @Serializable(with = UUIDSerializer::class) val fieldValueSetId: UUID? = null,
    val fieldValueSetETag: String? = null,
    val fieldValues: List<FieldValueDto> = emptyList(),
    val responseRevision: Long,
    @Serializable(with = TimestampSerializer::class) val updatedAt: Timestamp,
    val reconfirmationRequired: Boolean = false,
    val editLock: InformationRequestResponseEditLock? = null,
    val evidenceFileCount: Int? = null,
)
