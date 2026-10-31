package com.docuhyphen.app.api.model.exchange

import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import java.util.*

internal data class ExchangeRecipientBinding(
    val selectionType: ExchangeRecipientSelectionType,
    val targetOrganizationId: UUID?,
)
