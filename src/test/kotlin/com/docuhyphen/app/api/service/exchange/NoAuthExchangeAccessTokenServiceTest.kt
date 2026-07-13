package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.entity.Exchange
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class NoAuthExchangeAccessTokenServiceTest
{
    private val service = NoAuthExchangeAccessTokenService()

    @Test
    fun `issued token is stored only as a hash and validates with constant credentials`()
    {
        val exchange = Exchange()

        val token = service.issue(exchange)

        assertNotNull(exchange.noAuthAccessTokenHash)
        assertNotEquals(token, exchange.noAuthAccessTokenHash)
        assertDoesNotThrow { service.requireValid(exchange, token) }
    }

    @Test
    fun `missing or incorrect token does not reveal the Exchange`()
    {
        val exchange = Exchange()
        service.issue(exchange)

        assertThrows(ExchangeNotFoundException::class.java) { service.requireValid(exchange, null) }
        assertThrows(ExchangeNotFoundException::class.java) { service.requireValid(exchange, "incorrect") }
    }

    @Test
    fun `issuing a new token invalidates the previous token and verification window`()
    {
        val exchange = Exchange()
        val first = service.issue(exchange)
        exchange.noAuthAccessVerifiedAt = java.sql.Timestamp(System.currentTimeMillis())

        val second = service.issue(exchange)

        assertNotEquals(first, second)
        assertThrows(ExchangeNotFoundException::class.java) { service.requireValid(exchange, first) }
        assertDoesNotThrow { service.requireValid(exchange, second) }
        assertNull(exchange.noAuthAccessVerifiedAt)
    }
}
