package com.docuhyphen.app.api.model.informationrequest.response

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import java.util.*

data class InformationRequestRecipientPreview(
    val partyId: UUID,
    val roleKey: InformationRequestShareRoleKey,
    val templateVersion: InformationRequestTemplateVersionDto,
)
