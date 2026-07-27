package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.service.workflow.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
        val fixture = ExchangeAcceptanceWorkflowFixture()

        fixture.decide(accepted = true)

        fixture.verifyWorkflowDecision(Decision.APPROVE, reason = null)
        verify(fixture.exchangeRepository)
            .updateStatus(fixture.exchangeId, ExchangeStatus.ACCEPTED_STARTED)
    }

    @Test
    fun `EX-ACC-04 primary rejection follows active workflow`()
    {
        val fixture = ExchangeAcceptanceWorkflowFixture()

        fixture.decide(accepted = false, reason = "Unable to participate")

        fixture.verifyWorkflowDecision(Decision.REJECT, reason = "Unable to participate")
        verify(fixture.exchangeRepository)
            .updateStatus(fixture.exchangeId, ExchangeStatus.REJECTED)
    }

    @Test
    fun `EX-ACC-05 acceptance bypass activates Exchange immediately`()
    {
        val fixture = ExchangeInitiationAutoAcceptFixture()

        val exchange = fixture.initiate()

        assertEquals(ExchangeStatus.ACCEPTED_STARTED, exchange.status)
        verify(fixture.recipientService).createBinding(
            exchangeId = eq(exchange.id),
            directShare = any(),
            purpose = eq(ExchangeRecipientPurpose.PRIMARY),
            selectionType = eq(ExchangeRecipientSelectionType.REGISTERED_USER),
            targetOrganizationId = eq(null),
            acceptanceStatus = eq(ExchangeRecipientAcceptanceStatus.NOT_REQUIRED),
        )
    }

    @Test
    fun `EX-ACC-06 additional participant has no decision controls`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            purpose = ExchangeRecipientPurpose.PARTICIPANT,
            initialAcceptanceStatus = ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
            initialShareStatus = ShareStatus.ACTIVE,
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordParticipant(accepted = true)
        }

        assertEquals(ExchangeRecipientPurpose.PARTICIPANT, fixture.recipient.purpose)
        assertEquals(ExchangeRecipientAcceptanceStatus.NOT_REQUIRED, fixture.recipient.acceptanceStatus)
        verify(fixture.repository, never()).update(any())
        fixture.verifyParticipantNotActivated()
        fixture.verifyParticipantNotRevoked()
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
        val fixture = trustedGroupFixture(PrincipalGroupRoleName.OWNER)
        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, fixture.recipient.acceptanceStatus)
        assertEquals(ShareStatus.PENDING_APPROVAL, fixture.share.status)
    }

    @Test
    fun `EX-ACC-12 trusted group Owner can accept`()
    {
        val fixture = trustedGroupFixture(PrincipalGroupRoleName.OWNER)
        assertEquals(PrincipalGroupRoleName.OWNER, fixture.groupMember?.groupRole)
        assertEquals(
            ExchangeRecipientAcceptanceStatus.ACCEPTED,
            fixture.recordPrimary(accepted = true).acceptanceStatus,
        )
    }

    @Test
    fun `EX-ACC-13 trusted group Manager can accept`()
    {
        val fixture = trustedGroupFixture(PrincipalGroupRoleName.MANAGER)
        assertEquals(PrincipalGroupRoleName.MANAGER, fixture.groupMember?.groupRole)
        assertEquals(
            ExchangeRecipientAcceptanceStatus.ACCEPTED,
            fixture.recordPrimary(accepted = true).acceptanceStatus,
        )
    }

    @Test
    fun `EX-ACC-14 trusted group Member cannot accept`()
    {
        val fixture = trustedGroupFixture(PrincipalGroupRoleName.MEMBER)
        assertEquals(PrincipalGroupRoleName.MEMBER, fixture.groupMember?.groupRole)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
    }

    @Test
    fun `EX-ACC-15 trusted group Observer cannot accept`()
    {
        val fixture = trustedGroupFixture(PrincipalGroupRoleName.OBSERVER)
        assertEquals(PrincipalGroupRoleName.OBSERVER, fixture.groupMember?.groupRole)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
    }

    @Test
    fun `EX-ACC-16 suspended trust blocks pending group decision`()
    {
        val fixture = trustedGroupFixture(
            PrincipalGroupRoleName.OWNER,
            AcceptanceTrustLifecycle.SUSPENDED,
        )
        assertThrows(OrganizationTrustNotFoundException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, fixture.recipient.acceptanceStatus)
    }

    @Test
    fun `EX-ACC-17 ended trust blocks pending group decision`()
    {
        val fixture = trustedGroupFixture(
            PrincipalGroupRoleName.OWNER,
            AcceptanceTrustLifecycle.ENDED,
        )
        assertThrows(OrganizationTrustNotFoundException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, fixture.recipient.acceptanceStatus)
    }

    @Test
    fun `EX-ACC-18 competing primary decisions serialize on the locked recipient`()
    {
        val fixture = ExchangeRecipientDecisionFixture()

        fixture.recordPrimary(accepted = true)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = false)
        }

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, fixture.recipient.acceptanceStatus)
        fixture.verifyPrimaryLockCount(2)
        fixture.verifyDecisionUpdateCount(1)
    }

    @Test
    fun `EX-ACC-19 competing participant decisions serialize on the locked recipient`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
            purpose = ExchangeRecipientPurpose.PARTICIPANT,
            initialAcceptanceStatus = ExchangeRecipientAcceptanceStatus.PENDING,
            initialShareStatus = ShareStatus.PENDING_APPROVAL,
        )

        fixture.recordParticipant(accepted = true)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordParticipant(accepted = false)
        }

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, fixture.recipient.acceptanceStatus)
        fixture.verifyParticipantLockCount(2)
        fixture.verifyDecisionUpdateCount(1)
        fixture.verifyParticipantActivated()
    }

    @Test
    fun `EX-ACC-20 stale participant decision cannot change recipient or Share state`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            selectionType = ExchangeRecipientSelectionType.TRUSTED_PERSON,
            purpose = ExchangeRecipientPurpose.PARTICIPANT,
            initialAcceptanceStatus = ExchangeRecipientAcceptanceStatus.ACCEPTED,
            initialShareStatus = ShareStatus.ACTIVE,
        )

        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordParticipant(accepted = false)
        }

        assertEquals(ExchangeRecipientAcceptanceStatus.ACCEPTED, fixture.recipient.acceptanceStatus)
        assertEquals(ShareStatus.ACTIVE, fixture.share.status)
        fixture.verifyParticipantLockCount(1)
        fixture.verifyDecisionUpdateCount(0)
        fixture.verifyParticipantNotActivated()
        fixture.verifyParticipantNotRevoked()
    }

    private fun trustedGroupFixture(
        groupRole: PrincipalGroupRoleName,
        trustLifecycle: AcceptanceTrustLifecycle = AcceptanceTrustLifecycle.ACTIVE,
    ): ExchangeRecipientDecisionFixture = ExchangeRecipientDecisionFixture(
        selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP,
        principalKind = PrincipalKind.PRINCIPAL_GROUP,
        groupRole = groupRole,
        trustLifecycle = trustLifecycle,
    )
}
