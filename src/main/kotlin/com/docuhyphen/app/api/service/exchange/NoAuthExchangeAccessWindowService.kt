package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.ExchangeRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant

/**
 * Owns the lifetime of a no-auth recipient's verified access window.
 *
 * A recipient proves possession of the emailed access code once, and that proof is valid for
 * `noAuthAccessValidityDays`. Every no-auth action (view, upload, download, thumbnail) is
 * measured against the same window through this service so the recipient can never be shown an
 * Exchange they are then blocked from acting on.
 */
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

    /**
     * Records a successful access-code verification.
     *
     * The timestamp is written through the managed entity as well as the row, because writing it
     * only with a bulk JPQL update leaves the in-memory entity holding the previous value. When
     * that entity is dirty for any other reason in the same transaction, the flush at commit
     * rewrites every column and silently reverts the verification back to null, which made
     * recipients look verified while every later action reported an expired window.
     */
    @Transactional
    fun markVerified(exchange: Exchange, verifiedAt: Timestamp = Timestamp.from(Instant.now()))
    {
        exchange.noAuthAccessVerifiedAt = verifiedAt
        exchangeRepository.update(exchange)
        exchangeRepository.updateNoAuthAccessVerifiedAt(exchange.id, verifiedAt)
    }

    /** Clears the window so the next no-auth action requires a fresh access code. */
    @Transactional
    fun clearVerification(exchange: Exchange)
    {
        exchange.noAuthAccessVerifiedAt = null
        exchangeRepository.update(exchange)
        exchangeRepository.updateNoAuthAccessVerifiedAt(exchange.id, null)
    }

    /**
     * The instant the recipient last proved possession of an access code.
     *
     * Falls back to the primary recipient's acceptance decision, because accepting an Exchange
     * over the no-auth flow already requires a valid one-time code. Without that fallback an
     * Exchange whose verification timestamp was never persisted looks unverified forever, even
     * though the recipient demonstrably passed the code check when they accepted.
     */
    fun resolveVerifiedAt(exchange: Exchange): Timestamp?
    {
        exchange.noAuthAccessVerifiedAt?.let { return it }

        if (exchange.status != ExchangeStatus.ACCEPTED_STARTED) return null

        val primaryRecipient = exchangeRecipientService.findPrimary(exchange.id) ?: return null
        if (primaryRecipient.acceptanceStatus != ExchangeRecipientAcceptanceStatus.ACCEPTED) return null

        return primaryRecipient.acceptedOrRejectedAt
    }

    /** The instant the current window lapses, or null when no verification has happened. */
    fun expiresAt(exchange: Exchange): Instant?
    {
        val verifiedAt = resolveVerifiedAt(exchange) ?: return null
        val validityDays = exchange.noAuthAccessValidityDays.coerceAtLeast(1).toLong()
        return verifiedAt.toInstant().plusSeconds(validityDays * 24 * 60 * 60)
    }

    /** Non-throwing check used by read paths that report window state instead of failing. */
    fun isActive(exchange: Exchange): Boolean
    {
        val expiresAt = expiresAt(exchange) ?: return false
        return expiresAt.isAfter(Instant.now())
    }

    /**
     * Gate for every no-auth action that changes or reveals document content. Back-fills the
     * verification timestamp when it was recovered from the acceptance decision so subsequent
     * calls resolve it directly.
     */
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
                "Restored missing no-auth verification timestamp for exchange {} from the recipient acceptance decision",
                exchange.id,
            )
            markVerified(exchange, verifiedAt)
        }
    }
}

/** Raised when a no-auth recipient has no live verified access window. */
class NoAuthExchangeAccessExpiredException(message: String) : RuntimeException(message)

