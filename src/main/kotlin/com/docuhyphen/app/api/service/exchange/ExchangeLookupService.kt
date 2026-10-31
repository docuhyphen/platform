package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class ExchangeLookupService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
) {
    fun findExchange(exchangeId: UUID): Exchange? = exchangeRepository.findById(exchangeId)

    fun lockExchangeForUpdate(exchangeId: UUID): Exchange? = exchangeRepository.findByIdForUpdate(exchangeId)
}
