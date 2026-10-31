package com.docuhyphen.app.api.model.exchange

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.service.exchange.ResolvedExchangeRecipientSelection

internal data class ExchangeResolvedParticipant(
    val selection: ResolvedExchangeRecipientSelection,
    val role: ExchangeShareRoleName,
)
