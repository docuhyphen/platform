package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.ExchangeRecipientInvitationDto
import com.docuhyphen.app.api.model.entity.ExchangeRecipient

object ExchangeRecipientInvitationDtoTransformer
{
    fun toDto(recipient: ExchangeRecipient): ExchangeRecipientInvitationDto =
        ExchangeRecipientInvitationDto(
            id = recipient.id,
            exchangeId = recipient.exchangeId,
            selectionType = recipient.selectionType,
            createdAt = recipient.createdAt,
        )
}
