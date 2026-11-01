package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

@ApplicationScoped
class NoAuthExchangeAccessWindowService @Inject constructor(
    private val exchangeRepository: ExchangeRepository,
    private val exchangeRecipientService: ExchangeRecipientService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(NoAuthExchangeAccessWindowService::class.java)

        const val VERIFICATION_REQUIRED_MESSAGE =
            "Your access verification has expired. Ask the person who requested documents to resend an access code in Manage Access."
    }

    @Transactional
    fun markVerified(exchange: Exchange, verifiedAt: Timestamp = Timestamp.from(Instant.now()))
    {
        exchange.noAuthAccessVerifiedAt = verifiedAt
        exchangeRepository.update(exchange)
        exchangeRepository.updateNoAuthAccessVerifiedAt(exchange.id, verifiedAt)
    }

    @Transactional
    fun clearVerification(exchange: Exchange)
    {
        exchange.noAuthAccessVerifiedAt = null
        exchangeRepository.update(exchange)
        exchangeRepository.updateNoAuthAccessVerifiedAt(exchange.id, null)
    }

    fun resolveVerifiedAt(exchange: Exchange): Timestamp?
    {
        exchange.noAuthAccessVerifiedAt?.let { return it }

        if (exchange.status != ExchangeStatus.ACCEPTED_STARTED) return null

        val primaryRecipient = exchangeRecipientService.findPrimary(exchange.id) ?: return null
        if (primaryRecipient.acceptanceStatus != ExchangeRecipientAcceptanceStatus.ACCEPTED) return null

        return primaryRecipient.acceptedOrRejectedAt
    }

    fun expiresAt(exchange: Exchange): Instant?
    {
        val verifiedAt = resolveVerifiedAt(exchange) ?: return null
        val validityDays = exchange.noAuthAccessValidityDays.coerceAtLeast(1).toLong()
        return verifiedAt.toInstant().plusSeconds(validityDays * 24 * 60 * 60)
    }

    fun isActive(exchange: Exchange): Boolean
    {
        val expiresAt = expiresAt(exchange) ?: return false
        return expiresAt.isAfter(Instant.now())
    }

    fun ensureActive(exchange: Exchange)
    {
        val verifiedAt = resolveVerifiedAt(exchange)
            ?: throw NoAuthExchangeAccessExpiredException(VERIFICATION_REQUIRED_MESSAGE)

        val validityDays = exchange.noAuthAccessValidityDays.coerceAtLeast(1).toLong()
        val validUntil = verifiedAt.toInstant().plusSeconds(validityDays * 24 * 60 * 60)
        if (validUntil.isBefore(Instant.now()))
        {
            throw NoAuthExchangeAccessExpiredException(VERIFICATION_REQUIRED_MESSAGE)
        }

        if (exchange.noAuthAccessVerifiedAt == null)
        {
            logger.info(
                "Restored missing no-auth verification timestamp for Exchange {} from the recipient acceptance decision",
                exchange.id,
            )
            markVerified(exchange, verifiedAt)
        }
    }
}

class NoAuthExchangeAccessExpiredException(message: String) : RuntimeException(message)

