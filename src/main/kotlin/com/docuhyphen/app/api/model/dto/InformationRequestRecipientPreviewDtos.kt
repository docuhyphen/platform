package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class InformationRequestRecipientPreviewDto(
    @Serializable(with = UUIDSerializer::class) val partyId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val templateVersion: InformationRequestTemplateVersionDto,
)
