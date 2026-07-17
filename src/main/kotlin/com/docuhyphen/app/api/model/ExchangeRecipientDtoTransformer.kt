package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.ExchangeRecipientDto
import com.docuhyphen.app.api.model.entity.ExchangeRecipient

object ExchangeRecipientDtoTransformer
{
    fun toDto(recipient: ExchangeRecipient): ExchangeRecipientDto =
        ExchangeRecipientDto(
            id = recipient.id,
            exchangeId = recipient.exchangeId,
            directShareId = recipient.directShareId,
            purpose = recipient.purpose,
            selectionType = recipient.selectionType,
            targetOrganizationId = recipient.targetOrganizationId,
            acceptanceStatus = recipient.acceptanceStatus,
            acceptedOrRejectedByAppUserId = recipient.acceptedOrRejectedByAppUserId,
            acceptedOrRejectedAt = recipient.acceptedOrRejectedAt,
            createdAt = recipient.createdAt,
        )
}
