package com.docuhyphen.app.api.model.exchange

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName

internal data class ExchangeResolvedParticipant(
    val selection: ResolvedExchangeRecipientSelection,
    val role: ExchangeShareRoleName,
)
