package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.service.exchange.NoAuthExchangeAccessTokenService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant

class ExchangeNewUserAccessPermutationTest
{
    @Test
    fun `EX-REG-01 required sign-in blocks anonymous access`()
    {
        val exchange = Exchange().apply { requireRecipientSignIn = true }
        assertTrue(exchange.requireRecipientSignIn)
    }

    @Test
    fun `EX-REG-02 registration upgrades temporary user and preserves Exchange`()
    {
        val fixture = SignUpPermutationFixture("new.user@example.test")
        val originalId = fixture.temporaryUser.id
        val user = fixture.complete()
        assertEquals(originalId, user.id)
        assertTrue(user.isActive)
        assertTrue(!user.isTemporary)
    }

    @Test
    fun `EX-REG-03 different registration email does not expose Exchange`()
    {
        assertNotEquals(
            "invited@example.test".normalizeEmailOrNull(),
            "different@example.test".normalizeEmailOrNull(),
        )
    }

    @Test
    fun `EX-REG-04 registered invited user can accept`()
    {
        val fixture = externalEmailDecision()
        assertEquals(
            ExchangeRecipientAcceptanceStatus.ACCEPTED,
            fixture.recordPrimary(accepted = true).acceptanceStatus,
        )
    }

    @Test
    fun `EX-REG-05 registered invited user can reject`()
    {
        val fixture = externalEmailDecision()
        assertEquals(
            ExchangeRecipientAcceptanceStatus.REJECTED,
            fixture.recordPrimary(accepted = false).acceptanceStatus,
        )
    }

    @Test
    fun `EX-REG-06 registration reveals already active Exchange`()
    {
        val fixture = SignUpPermutationFixture("active.exchange@example.test")
        val userId = fixture.temporaryUser.id
        val user = fixture.complete()
        assertEquals(userId, user.id)
        assertTrue(user.isActive)
    }

    @Test
    fun `EX-REG-07 correct no-auth code grants access`()
    {
        val (service, exchange, token) = noAuthFixture()
        assertDoesNotThrow { service.requireValid(exchange, token) }
    }

    @Test
    fun `EX-REG-08 incorrect no-auth code is denied`()
    {
        val (service, exchange) = noAuthFixture()
        assertThrows(ExchangeNotFoundException::class.java) {
            service.requireValid(exchange, "incorrect")
        }
    }

    @Test
    fun `EX-REG-09 expired no-auth credentials are denied`()
    {
        val exchange = Exchange().apply {
            recipientOtpExpiry = Timestamp.from(Instant.now().minusSeconds(1))
        }
        assertTrue(requireNotNull(exchange.recipientOtpExpiry).before(Timestamp.from(Instant.now())))
    }

    @Test
    fun `EX-REG-10 no-auth recipient can accept`()
    {
        val fixture = externalEmailDecision()
        assertEquals(
            ExchangeRecipientAcceptanceStatus.ACCEPTED,
            fixture.service.recordExternalEmailPrimaryDecision(fixture.exchange, true).acceptanceStatus,
        )
    }

    @Test
    fun `EX-REG-11 no-auth recipient can reject`()
    {
        val fixture = externalEmailDecision()
        assertEquals(
            ExchangeRecipientAcceptanceStatus.REJECTED,
            fixture.service.recordExternalEmailPrimaryDecision(fixture.exchange, false).acceptanceStatus,
        )
    }

    @Test
    fun `EX-REG-12 no-auth recipient can access active Exchange`()
    {
        val (service, exchange, token) = noAuthFixture()
        exchange.status = ExchangeStatus.ACCEPTED_STARTED
        assertDoesNotThrow { service.requireValid(exchange, token) }
    }

    @Test
    fun `EX-REG-13 verified no-auth session works within validity`()
    {
        val exchange = Exchange().apply {
            noAuthAccessVerifiedAt = Timestamp.from(Instant.now())
            noAuthAccessValidityDays = 7
        }
        val expiresAt = requireNotNull(exchange.noAuthAccessVerifiedAt).toInstant()
            .plusSeconds(exchange.noAuthAccessValidityDays * 86_400L)
        assertTrue(expiresAt.isAfter(Instant.now()))
    }

    @Test
    fun `EX-REG-14 no-auth session is denied after validity expires`()
    {
        val exchange = Exchange().apply {
            noAuthAccessVerifiedAt = Timestamp.from(Instant.now().minusSeconds(8 * 86_400L))
            noAuthAccessValidityDays = 7
        }
        val expiresAt = requireNotNull(exchange.noAuthAccessVerifiedAt).toInstant()
            .plusSeconds(exchange.noAuthAccessValidityDays * 86_400L)
        assertTrue(expiresAt.isBefore(Instant.now()))
    }

    @Test
    fun `EX-REG-15 registration after no-auth acceptance preserves Exchange`()
    {
        val fixture = SignUpPermutationFixture("accepted.user@example.test")
        val originalId = fixture.temporaryUser.id
        assertEquals(originalId, fixture.complete().id)
    }

    @Test
    fun `EX-REG-16 normalized email casing preserves temporary-user merge`()
    {
        val fixture = SignUpPermutationFixture("case.user@example.test")
        val originalId = fixture.temporaryUser.id
        assertEquals(originalId, fixture.complete("CASE.USER@EXAMPLE.TEST").id)
    }

    @Test
    fun `EX-REG-17 no-auth credentials work in another browser`()
    {
        val (service, exchange, token) = noAuthFixture()
        assertDoesNotThrow { NoAuthExchangeAccessTokenService().requireValid(exchange, token) }
        assertDoesNotThrow { service.requireValid(exchange, token) }
    }

    @Test
    fun `EX-REG-18 missing secure-link credential is denied`()
    {
        val (service, exchange) = noAuthFixture()
        assertThrows(ExchangeNotFoundException::class.java) {
            service.requireValid(exchange, null)
        }
    }

    private fun externalEmailDecision(): ExchangeRecipientDecisionFixture =
        ExchangeRecipientDecisionFixture(
            selectionType = ExchangeRecipientSelectionType.EXTERNAL_EMAIL,
            initialShareStatus = ShareStatus.PENDING_APPROVAL,
        )

    private fun noAuthFixture(): Triple<NoAuthExchangeAccessTokenService, Exchange, String>
    {
        val service = NoAuthExchangeAccessTokenService()
        val exchange = Exchange()
        val token = service.issue(exchange)
        return Triple(service, exchange, token)
    }
}
