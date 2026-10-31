package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestRecipientPreviewDto
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestRecipientPreview

object InformationRequestRecipientPreviewDtoMapper {
    fun toDto(preview: InformationRequestRecipientPreview): InformationRequestRecipientPreviewDto =
        InformationRequestRecipientPreviewDto(
            partyId = preview.partyId,
            roleKey = preview.roleKey,
            templateVersion = preview.templateVersion,
        )
}
