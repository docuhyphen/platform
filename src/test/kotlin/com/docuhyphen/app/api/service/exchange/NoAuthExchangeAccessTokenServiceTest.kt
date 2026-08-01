package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.entity.Exchange
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" ", "incorrect"])
    fun `missing blank or incorrect token does not reveal the Exchange`(presentedToken: String?)
    {
        val exchange = Exchange()
        service.issue(exchange)

        assertThrows(ExchangeNotFoundException::class.java) {
            service.requireValid(exchange, presentedToken)
        }
    }

    @Test
    fun `token issued for another Exchange is rejected`()
    {
        val firstExchange = Exchange()
        val secondExchange = Exchange()
        val firstToken = service.issue(firstExchange)
        service.issue(secondExchange)

        assertThrows(ExchangeNotFoundException::class.java) {
            service.requireValid(secondExchange, firstToken)
        }
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
