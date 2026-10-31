package com.docuhyphen.app.api.service.informationrequest.parent

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import java.util.*

fun lockParentExchangeOf(
    requestId: UUID,
    requestRepository: InformationRequestRepository,
    exchangeLookup: ExchangeLookupService,
): Exchange = lockExchange(parentExchangeIdOf(requestId, requestRepository), exchangeLookup)

fun lockParentExchangesOf(
    requestIds: Collection<UUID>,
    requestRepository: InformationRequestRepository,
    exchangeLookup: ExchangeLookupService,
): Map<UUID, Exchange> =
    lockExchangesInOrder(requestIds.map { parentExchangeIdOf(it, requestRepository) }, exchangeLookup)

fun lockExchangesInOrder(exchangeIds: Collection<UUID>, exchangeLookup: ExchangeLookupService): Map<UUID, Exchange> =
    exchangeIds.distinct().sorted().associateWith { lockExchange(it, exchangeLookup) }

private fun parentExchangeIdOf(requestId: UUID, requestRepository: InformationRequestRepository): UUID =
    requestRepository.findById(requestId)?.exchangeId
        ?: throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.NOT_FOUND,
            "Information Request not found",
        )

private fun lockExchange(exchangeId: UUID, exchangeLookup: ExchangeLookupService): Exchange =
    exchangeLookup.lockExchangeForUpdate(exchangeId)
        ?: throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.PARENT_STATE_INVALID,
            "Parent Exchange not found",
        )

