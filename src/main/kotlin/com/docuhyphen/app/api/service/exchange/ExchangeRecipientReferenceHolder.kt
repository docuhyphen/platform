package com.docuhyphen.app.api.service.exchange

import java.util.*

interface ExchangeRecipientReferenceHolder {
    fun references(exchangeRecipientId: UUID): Boolean
}
