package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

/**
 * One occurrence's current draft response, projected back to the caller that just patched it.
 * Only the requirements a caller's patch named are ever projected; a caller never learns another
 * party's disposition or narrative through this shape.
 */
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
)
