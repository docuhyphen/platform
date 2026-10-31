package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.Share
import java.util.*

interface TrustedGroupShareAttestationResolver {
    fun attestationFor(share: Share): ExchangeRecipientAttestation?

    fun activeGroupShareIdsFor(exchangeRecipientId: UUID): List<UUID>
}
