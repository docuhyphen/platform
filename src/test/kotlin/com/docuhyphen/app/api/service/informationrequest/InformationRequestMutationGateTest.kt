package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestMutationGateTest {
    private val requestRepository = mock<InformationRequestRepository>()
    private val exchangeLookup = mock<ExchangeLookupService>()
    private val gate = InformationRequestMutationGate(requestRepository, exchangeLookup, mock(), mock(), mock())

    @Test
    fun `several requests are locked after every parent Exchange, each lock taken once in identifier order`() {
        val lowerExchange = exchange(UUID(0, 1))
        val higherExchange = exchange(UUID(0, 2))
        val first = request(UUID(1, 1), higherExchange)
        val second = request(UUID(1, 2), lowerExchange)
        val third = request(UUID(1, 3), higherExchange)

        val locked = gate.lockAll(listOf(third.id, first.id, second.id, third.id))

        val order = inOrder(exchangeLookup, requestRepository)
        order.verify(exchangeLookup).lockExchangeForUpdate(lowerExchange.id)
        order.verify(exchangeLookup).lockExchangeForUpdate(higherExchange.id)
        order.verify(requestRepository).findRequestByIdForUpdate(first.id)
        order.verify(requestRepository).findRequestByIdForUpdate(second.id)
        order.verify(requestRepository).findRequestByIdForUpdate(third.id)
        verify(exchangeLookup, times(2)).lockExchangeForUpdate(any())
        verify(requestRepository, times(3)).findRequestByIdForUpdate(any())
        assertEquals(
            listOf(
                LockedInformationRequest(higherExchange, first),
                LockedInformationRequest(lowerExchange, second),
                LockedInformationRequest(higherExchange, third),
            ),
            locked,
        )
    }

    @Test
    fun `an unknown request is refused before any Exchange or request is locked`() {
        val known = request(UUID(1, 1), exchange(UUID(0, 1)))

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            gate.lockAll(listOf(known.id, UUID(1, 2)))
        }

        assertEquals(InformationRequestErrorCatalog.NOT_FOUND, refusal.reasonCode)
        verify(exchangeLookup, never()).lockExchangeForUpdate(any())
        verify(requestRepository, never()).findRequestByIdForUpdate(any())
    }

    @Test
    fun `requests that still exist are locked after every parent Exchange in identifier order and one that no longer exists is skipped`() {
        val lowerExchange = exchange(UUID(0, 1))
        val higherExchange = exchange(UUID(0, 2))
        val first = request(UUID(1, 1), higherExchange)
        val gone = UUID(1, 2)
        val third = request(UUID(1, 3), lowerExchange)
        whenever(requestRepository.findParentExchangeIds(any()))
            .thenReturn(mapOf(first.id to higherExchange.id, third.id to lowerExchange.id))

        val locked = gate.lockExisting(listOf(third.id, gone, first.id, third.id))

        val order = inOrder(exchangeLookup, requestRepository)
        order.verify(exchangeLookup).lockExchangeForUpdate(lowerExchange.id)
        order.verify(exchangeLookup).lockExchangeForUpdate(higherExchange.id)
        order.verify(requestRepository).findRequestByIdForUpdate(first.id)
        order.verify(requestRepository).findRequestByIdForUpdate(third.id)
        verify(exchangeLookup, times(2)).lockExchangeForUpdate(any())
        verify(requestRepository, never()).findRequestByIdForUpdate(gone)
        assertEquals(
            listOf(LockedInformationRequest(higherExchange, first), LockedInformationRequest(lowerExchange, third)),
            locked,
        )
    }

    @Test
    fun `a request removed after its parent Exchange was locked is skipped`() {
        val parent = exchange(UUID(0, 1))
        val kept = request(UUID(1, 1), parent)
        val removed = UUID(1, 2)
        whenever(requestRepository.findParentExchangeIds(any())).thenReturn(
            mapOf(
                kept.id to parent.id,
                removed to parent.id
            )
        )

        val locked = gate.lockExisting(listOf(removed, kept.id))

        assertEquals(listOf(LockedInformationRequest(parent, kept)), locked)
        verify(exchangeLookup, times(1)).lockExchangeForUpdate(parent.id)
        verify(requestRepository).findRequestByIdForUpdate(removed)
    }

    private fun exchange(id: UUID): Exchange {
        val exchange = Exchange().apply { this.id = id }
        whenever(exchangeLookup.lockExchangeForUpdate(id)).thenReturn(exchange)
        return exchange
    }

    private fun request(id: UUID, parent: Exchange): InformationRequest {
        val request = InformationRequest().apply {
            this.id = id
            exchangeId = parent.id
        }
        whenever(requestRepository.findById(id)).thenReturn(request)
        whenever(requestRepository.findRequestByIdForUpdate(id)).thenReturn(request)
        return request
    }
}
