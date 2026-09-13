package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import java.util.UUID

/**
 * Every command that mutates a runtime request takes the parent Exchange row lock first and the
 * request row lock second. Exchange termination locks the same rows in the same order, so the two
 * command families serialize instead of deadlocking on inverted lock acquisition.
 */
internal fun lockParentExchangeOf(
    requestId: UUID,
    requestRepository: InformationRequestRepository,
    exchangeRepository: ExchangeRepository,
): Exchange
{
    val exchangeId = requestRepository.findById(requestId)?.exchangeId
        ?: throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.NOT_FOUND,
            "Information Request not found",
        )
    return exchangeRepository.findByIdForUpdate(exchangeId)
        ?: throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            "Parent Exchange not found",
        )
}

