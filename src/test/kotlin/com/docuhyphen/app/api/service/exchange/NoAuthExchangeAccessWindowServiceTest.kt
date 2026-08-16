package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipient
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class NoAuthExchangeAccessWindowServiceTest
{
    private val exchangeRepository = mock<ExchangeRepository>()
    private val exchangeRecipientService = mock<ExchangeRecipientService>()
    private val service = NoAuthExchangeAccessWindowService(exchangeRepository, exchangeRecipientService)

    private fun exchange(
        status: ExchangeStatus = ExchangeStatus.ACCEPTED_STARTED,
        verifiedAt: Instant? = null,
        validityDays: Int = 7,
    ) = Exchange().apply {
        this.status = status
        this.noAuthAccessVerifiedAt = verifiedAt?.let(Timestamp::from)
        this.noAuthAccessValidityDays = validityDays
    }

    private fun acceptedPrimary(exchangeId: UUID, at: Instant?) = ExchangeRecipient().apply {
        this.exchangeId = exchangeId
        this.directShareId = UUID.randomUUID()
        this.acceptanceStatus = ExchangeRecipientAcceptanceStatus.ACCEPTED
        this.acceptedOrRejectedAt = at?.let(Timestamp::from)
    }

    @Test
    fun `markVerified writes the timestamp onto the entity as well as the row`()
    {
        val exchange = exchange()
        val verifiedAt = Timestamp.from(Instant.now())

        service.markVerified(exchange, verifiedAt)

        // The entity must carry the new value so a later dirty-check flush cannot revert the
        // row back to the stale null it was loaded with.
        assertEquals(verifiedAt, exchange.noAuthAccessVerifiedAt)
        verify(exchangeRepository).update(exchange)
        verify(exchangeRepository).updateNoAuthAccessVerifiedAt(exchange.id, verifiedAt)
    }

    @Test
    fun `clearVerification resets the entity and the row`()
    {
        val exchange = exchange(verifiedAt = Instant.now())

        service.clearVerification(exchange)

        assertNull(exchange.noAuthAccessVerifiedAt)
        verify(exchangeRepository).updateNoAuthAccessVerifiedAt(exchange.id, null)
    }

    @Test
    fun `a window verified within the validity period stays active`()
    {
        val exchange = exchange(verifiedAt = Instant.now().minusSeconds(24 * 60 * 60))

        assertTrue(service.isActive(exchange))
        assertDoesNotThrow { service.ensureActive(exchange) }
    }

    @Test
    fun `a window verified beyond the validity period lapses`()
    {
        val exchange = exchange(verifiedAt = Instant.now().minusSeconds(8 * 24 * 60 * 60), validityDays = 7)

        assertFalse(service.isActive(exchange))
        assertThrows<NoAuthExchangeAccessExpiredException> { service.ensureActive(exchange) }
    }

    @Test
    fun `an accepted exchange with no recorded verification falls back to the acceptance decision`()
    {
        val exchange = exchange(verifiedAt = null)
        val acceptedAt = Instant.now().minusSeconds(24 * 60 * 60)
        whenever(exchangeRecipientService.findPrimary(exchange.id))
            .thenReturn(acceptedPrimary(exchange.id, acceptedAt))

        assertTrue(service.isActive(exchange))
        assertDoesNotThrow { service.ensureActive(exchange) }
    }

    @Test
    fun `the recovered acceptance timestamp is back-filled so later checks resolve it directly`()
    {
        val exchange = exchange(verifiedAt = null)
        val acceptedAt = Instant.now().minusSeconds(24 * 60 * 60)
        whenever(exchangeRecipientService.findPrimary(exchange.id))
            .thenReturn(acceptedPrimary(exchange.id, acceptedAt))

        service.ensureActive(exchange)

        assertNotNull(exchange.noAuthAccessVerifiedAt)
        verify(exchangeRepository).updateNoAuthAccessVerifiedAt(exchange.id, exchange.noAuthAccessVerifiedAt)
    }

    @Test
    fun `an acceptance older than the validity period still lapses`()
    {
        val exchange = exchange(verifiedAt = null, validityDays = 7)
        whenever(exchangeRecipientService.findPrimary(exchange.id))
            .thenReturn(acceptedPrimary(exchange.id, Instant.now().minusSeconds(30 * 24 * 60 * 60)))

        assertFalse(service.isActive(exchange))
        assertThrows<NoAuthExchangeAccessExpiredException> { service.ensureActive(exchange) }
    }

    @Test
    fun `a draft exchange never falls back to an acceptance decision`()
    {
        val exchange = exchange(status = ExchangeStatus.INITIATED, verifiedAt = null)

        assertFalse(service.isActive(exchange))
        assertThrows<NoAuthExchangeAccessExpiredException> { service.ensureActive(exchange) }
    }

    @Test
    fun `a recipient that never accepted has no fallback anchor`()
    {
        val exchange = exchange(verifiedAt = null)
        whenever(exchangeRecipientService.findPrimary(exchange.id)).thenReturn(
            ExchangeRecipient().apply {
                this.exchangeId = exchange.id
                this.directShareId = UUID.randomUUID()
                this.acceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING
            }
        )

        assertFalse(service.isActive(exchange))
        assertThrows<NoAuthExchangeAccessExpiredException> { service.ensureActive(exchange) }
    }
}

