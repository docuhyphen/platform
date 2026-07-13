package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.model.entity.Exchange
import jakarta.enterprise.context.ApplicationScoped
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@ApplicationScoped
class NoAuthExchangeAccessTokenService
{
    private val secureRandom = SecureRandom()

    fun issue(exchange: Exchange): String
    {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        exchange.noAuthAccessTokenHash = hash(token)
        exchange.noAuthAccessVerifiedAt = null
        return token
    }

    fun requireValid(exchange: Exchange, token: String?)
    {
        val expected = exchange.noAuthAccessTokenHash
            ?: throw ExchangeNotFoundException("Exchange not found")
        val presented = token?.takeIf { it.isNotBlank() }?.let(::hash)
            ?: throw ExchangeNotFoundException("Exchange not found")
        if (!MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), presented.toByteArray(Charsets.US_ASCII)))
        {
            throw ExchangeNotFoundException("Exchange not found")
        }
    }

    private fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
