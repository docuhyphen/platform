package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ShareStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class ExchangeAcceptancePermutationTest
{
    @Test
    fun `EX-ACC-01 primary user accepts without workflow`()
    {
        val fixture = ExchangeRecipientDecisionFixture()
        assertEquals(
            ExchangeRecipientAcceptanceStatus.ACCEPTED,
            fixture.recordPrimary(accepted = true).acceptanceStatus,
        )
    }

    @Test
    fun `EX-ACC-02 primary user rejects without workflow`()
    {
        val fixture = ExchangeRecipientDecisionFixture()
        assertEquals(
            ExchangeRecipientAcceptanceStatus.REJECTED,
            fixture.recordPrimary(accepted = false).acceptanceStatus,
        )
    }

    @Test
    fun `EX-ACC-03 primary acceptance follows active workflow`()
    {
        val fixture = ExchangeRecipientDecisionFixture()
        assertEquals(
            ExchangeRecipientAcceptanceStatus.ACCEPTED,
            fixture.recordPrimary(accepted = true).acceptanceStatus,
        )
    }

    @Test
    fun `EX-ACC-04 primary rejection follows active workflow`()
    {
        val fixture = ExchangeRecipientDecisionFixture()
        assertEquals(
            ExchangeRecipientAcceptanceStatus.REJECTED,
            fixture.recordPrimary(accepted = false).acceptanceStatus,
        )
    }

    @Test
    fun `EX-ACC-05 acceptance bypass activates Exchange immediately`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            initialAcceptanceStatus = ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
            initialShareStatus = ShareStatus.ACTIVE,
        )
        assertEquals(ExchangeRecipientAcceptanceStatus.NOT_REQUIRED, fixture.recipient.acceptanceStatus)
        assertEquals(ShareStatus.ACTIVE, fixture.share.status)
    }

    @Test
    fun `EX-ACC-06 additional participant has no decision controls`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            purpose = ExchangeRecipientPurpose.PARTICIPANT,
            initialAcceptanceStatus = ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
            initialShareStatus = ShareStatus.ACTIVE,
        )
        assertEquals(ExchangeRecipientPurpose.PARTICIPANT, fixture.recipient.purpose)
        assertEquals(ExchangeRecipientAcceptanceStatus.NOT_REQUIRED, fixture.recipient.acceptanceStatus)
    }

    @Test
    fun `EX-ACC-07 additional participant cannot call decision endpoint`()
    {
        val fixture = ExchangeRecipientDecisionFixture()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = true, actorId = UUID.randomUUID())
        }
    }

    @Test
    fun `EX-ACC-08 unrelated user cannot call decision endpoint`()
    {
        val fixture = ExchangeRecipientDecisionFixture()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = false, actorId = UUID.randomUUID())
        }
    }

    @Test
    fun `EX-ACC-09 repeated acceptance is idempotent`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            initialAcceptanceStatus = ExchangeRecipientAcceptanceStatus.ACCEPTED,
            initialShareStatus = ShareStatus.ACTIVE,
        )
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, fixture.recipient.acceptanceStatus)
    }

    @Test
    fun `EX-ACC-10 rejected Exchange cannot later be accepted`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            initialAcceptanceStatus = ExchangeRecipientAcceptanceStatus.REJECTED,
            initialShareStatus = ShareStatus.REVOKED,
        )
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
    }

    @Test
    fun `EX-ACC-11 trusted group requires acceptance when global policy bypasses it`()
    {
        val fixture = trustedGroupFixture(decisionMaker = true)
        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, fixture.recipient.acceptanceStatus)
        assertEquals(ShareStatus.PENDING_APPROVAL, fixture.share.status)
    }

    @Test
    fun `EX-ACC-12 trusted group Owner can accept`()
    {
        val fixture = trustedGroupFixture(decisionMaker = true)
        assertEquals(
            ExchangeRecipientAcceptanceStatus.ACCEPTED,
            fixture.recordPrimary(accepted = true).acceptanceStatus,
        )
    }

    @Test
    fun `EX-ACC-13 trusted group Manager can accept`()
    {
        val fixture = trustedGroupFixture(decisionMaker = true)
        assertEquals(
            ExchangeRecipientAcceptanceStatus.ACCEPTED,
            fixture.recordPrimary(accepted = true).acceptanceStatus,
        )
    }

    @Test
    fun `EX-ACC-14 trusted group Member cannot accept`()
    {
        val fixture = trustedGroupFixture(decisionMaker = false)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
    }

    @Test
    fun `EX-ACC-15 trusted group Observer cannot accept`()
    {
        val fixture = trustedGroupFixture(decisionMaker = false)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
    }

    @Test
    fun `EX-ACC-16 invalidated trust blocks pending group decision`()
    {
        val fixture = trustedGroupFixture(decisionMaker = true, trustedEligible = false)
        assertThrows(OrganizationTrustNotFoundException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, fixture.recipient.acceptanceStatus)
    }

    private fun trustedGroupFixture(
        decisionMaker: Boolean,
        trustedEligible: Boolean = true,
    ): ExchangeRecipientDecisionFixture = ExchangeRecipientDecisionFixture(
        selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP,
        principalKind = PrincipalKind.PRINCIPAL_GROUP,
        decisionMaker = decisionMaker,
        trustedEligible = trustedEligible,
    )
}
