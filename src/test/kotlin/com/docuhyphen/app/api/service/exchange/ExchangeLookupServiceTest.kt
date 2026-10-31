package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class ExchangeLookupServiceTest {
    private val exchangeRepository = mock<ExchangeRepository>()
    private val service = ExchangeLookupService(exchangeRepository)
    private val exchange = Exchange().apply { id = UUID.randomUUID() }

    @Test
    fun `finding an Exchange reads it without taking a row lock`() {
        whenever(exchangeRepository.findById(exchange.id)).thenReturn(exchange)

        assertSame(exchange, service.findExchange(exchange.id))
        verify(exchangeRepository, never()).findByIdForUpdate(any())
    }

    @Test
    fun `locking an Exchange for update takes its pessimistic row lock`() {
        whenever(exchangeRepository.findByIdForUpdate(exchange.id)).thenReturn(exchange)

        assertSame(exchange, service.lockExchangeForUpdate(exchange.id))
        verify(exchangeRepository).findByIdForUpdate(exchange.id)
        verify(exchangeRepository, never()).findById(any())
    }

    @Test
    fun `an Exchange that does not exist is absent from both reads`() {
        val missing = UUID.randomUUID()

        assertNull(service.findExchange(missing))
        assertNull(service.lockExchangeForUpdate(missing))
    }
}
