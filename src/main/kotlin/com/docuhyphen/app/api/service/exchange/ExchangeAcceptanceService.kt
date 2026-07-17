package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeAcceptanceDecision
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class ExchangeAcceptanceService @Inject constructor(
    private val exchangeUpdateService: ExchangeUpdateService,
)
{
    fun decide(exchangeId: String, decision: ExchangeAcceptanceDecision, reason: String?)
    {
        exchangeUpdateService.decideAcceptance(
            exchangeId = exchangeId,
            accepted = decision == ExchangeAcceptanceDecision.ACCEPT,
            reason = reason,
        )
    }
}
