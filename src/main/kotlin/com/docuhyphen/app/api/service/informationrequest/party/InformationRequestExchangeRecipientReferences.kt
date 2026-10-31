package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientReferenceHolder
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestExchangeRecipientReferences @Inject constructor(
    private val partyRepository: InformationRequestPartyRepository,
) : ExchangeRecipientReferenceHolder {
    override fun references(exchangeRecipientId: UUID): Boolean =
        partyRepository.existsForExchangeRecipient(exchangeRecipientId)
}
