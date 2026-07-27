package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAcceptanceStatus
import com.docuhyphen.app.api.model.entity.ExchangeRecipientPurpose
import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.resource.model.RegisteredUserRecipientSelectionRequest
import com.docuhyphen.app.api.service.auth.authz.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeAdditionalParticipantPermutationTest
{
    @Test
    fun `EX-PAR-01 registered user added during initiation receives read access only`()
    {
        val fixture = ordinaryParticipant()
        val binding = createParticipantBinding(fixture, ExchangeRecipientSelectionType.REGISTERED_USER)
        assertEquals(ExchangeRecipientPurpose.PARTICIPANT, binding.purpose)
        assertEquals(ExchangeRecipientAcceptanceStatus.NOT_REQUIRED, binding.acceptanceStatus)
        val probe = ExchangeAuthorizationProbe()
        probe.assertAllowed(Action.EXCHANGE_VIEW, listOf(probe.share(ExchangeShareRoleName.PARTICIPANT)))
        probe.assertDenied(Action.DOCUMENT_UPDATE, listOf(probe.share(ExchangeShareRoleName.PARTICIPANT)))
    }

    @Test
    fun `EX-PAR-02 Editor added during initiation has no decision rights`()
    {
        val fixture = ordinaryParticipant()
        createParticipantBinding(fixture, ExchangeRecipientSelectionType.REGISTERED_USER)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.recordTrustedParticipantDecision(fixture.recipient.id, fixture.principalId, true)
        }
    }

    @Test
    fun `EX-PAR-03 primary recipient cannot also be participant`()
    {
        val fixture = ordinaryParticipant()
        whenever(fixture.repository.findByDirectShareId(fixture.share.id)).thenReturn(
            fixture.recipient.apply { purpose = ExchangeRecipientPurpose.PRIMARY },
        )
        assertThrows(IllegalArgumentException::class.java) {
            createParticipantBinding(fixture, ExchangeRecipientSelectionType.REGISTERED_USER)
        }
    }

    @Test
    fun `EX-PAR-04 duplicate participant is rejected`()
    {
        val fixture = ordinaryParticipant()
        whenever(fixture.repository.findByDirectShareId(fixture.share.id)).thenReturn(
            fixture.recipient.apply {
                purpose = ExchangeRecipientPurpose.PARTICIPANT
                selectionType = ExchangeRecipientSelectionType.INTERNAL_GROUP
            },
        )
        assertThrows(IllegalArgumentException::class.java) {
            createParticipantBinding(fixture, ExchangeRecipientSelectionType.REGISTERED_USER)
        }
    }

    @Test
    fun `EX-PAR-05 new external email participant is rejected during initiation`()
    {
        val fixture = ordinaryParticipant()
        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.createBinding(
                fixture.exchangeId,
                fixture.share,
                ExchangeRecipientPurpose.PARTICIPANT,
                ExchangeRecipientSelectionType.EXTERNAL_EMAIL,
                null,
                ExchangeRecipientAcceptanceStatus.PENDING,
            )
        }
    }

    @Test
    fun `EX-PAR-06 existing user added by email receives direct Share`()
    {
        val fixture = ordinaryParticipant()
        val binding = createParticipantBinding(fixture, ExchangeRecipientSelectionType.EXTERNAL_EMAIL)
        assertEquals(fixture.share.id, binding.directShareId)
    }

    @Test
    fun `EX-PAR-07 new email added through Manage access receives external participant Share`()
    {
        val fixture = ordinaryParticipant()
        val binding = createParticipantBinding(fixture, ExchangeRecipientSelectionType.EXTERNAL_EMAIL)
        assertEquals(ExchangeRecipientSelectionType.EXTERNAL_EMAIL, binding.selectionType)
        assertEquals(ShareStatus.ACTIVE, fixture.share.status)
    }

    @Test
    fun `EX-PAR-08 existing user added by identifier receives Reviewer Share`()
    {
        val probe = ExchangeAuthorizationProbe()
        val share = probe.share(ExchangeShareRoleName.REVIEWER)
        probe.assertAllowed(Action.DOCUMENT_COMMENT, listOf(share))
        probe.assertDenied(Action.DOCUMENT_UPDATE, listOf(share))
    }

    @Test
    fun `EX-PAR-09 personal group added through Manage access grants inherited access`()
    {
        assertGroupAccess()
    }

    @Test
    fun `EX-PAR-10 internal group added through Manage access grants inherited access`()
    {
        assertGroupAccess()
    }

    @Test
    fun `EX-PAR-11 eligible trusted group can be added through Manage access`()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP,
            purpose = ExchangeRecipientPurpose.PARTICIPANT,
            principalKind = PrincipalKind.PRINCIPAL_GROUP,
        )
        val binding = createParticipantBinding(fixture, ExchangeRecipientSelectionType.TRUSTED_GROUP)
        assertEquals(ExchangeRecipientAcceptanceStatus.PENDING, binding.acceptanceStatus)
        assertEquals(ShareStatus.PENDING_APPROVAL, fixture.share.status)
    }

    @Test
    fun `EX-PAR-12 Exchange owner cannot add self`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(RegisteredUserRecipientSelectionRequest(probe.initiator.id.toString()))
        }
    }

    @Test
    fun `EX-PAR-13 participant role change applies immediately`()
    {
        val probe = ExchangeAuthorizationProbe()
        val viewer = probe.share(ExchangeShareRoleName.VIEWER)
        probe.assertDenied(Action.DOCUMENT_UPDATE, listOf(viewer))
        viewer.roleName = ExchangeShareRoleName.EDITOR
        probe.assertAllowed(Action.DOCUMENT_UPDATE, listOf(viewer))
    }

    @Test
    fun `EX-PAR-14 participant revocation applies immediately`()
    {
        val probe = ExchangeAuthorizationProbe()
        val share = probe.share()
        probe.assertAllowed(Action.EXCHANGE_VIEW, listOf(share))
        share.status = ShareStatus.REVOKED
        probe.assertDenied(Action.EXCHANGE_VIEW, listOf(share))
    }

    @Test
    fun `EX-PAR-15 caller cannot change or revoke own access entry`()
    {
        val probe = ExchangeAuthorizationProbe()
        probe.assertDenied(
            Action.EXCHANGE_MANAGE_ACCESS,
            listOf(probe.share(ExchangeShareRoleName.PARTICIPANT)),
        )
    }

    @Test
    fun `EX-PAR-16 non-owner cannot manage access`()
    {
        val probe = ExchangeAuthorizationProbe()
        probe.assertDenied(
            Action.EXCHANGE_MANAGE_ACCESS,
            listOf(probe.share(ExchangeShareRoleName.EDITOR)),
        )
    }

    @Test
    fun `EX-PAR-17 unknown user cannot open Exchange directly`()
    {
        ExchangeAuthorizationProbe().assertDenied(Action.EXCHANGE_VIEW, emptyList())
    }

    private fun ordinaryParticipant(): ExchangeRecipientDecisionFixture =
        ExchangeRecipientDecisionFixture(
            purpose = ExchangeRecipientPurpose.PARTICIPANT,
            initialAcceptanceStatus = ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
            initialShareStatus = ShareStatus.ACTIVE,
        )

    private fun createParticipantBinding(
        fixture: ExchangeRecipientDecisionFixture,
        selectionType: ExchangeRecipientSelectionType,
    ) = fixture.service.createBinding(
        fixture.exchangeId,
        fixture.share,
        ExchangeRecipientPurpose.PARTICIPANT,
        selectionType,
        fixture.ownerOrganizationId.takeIf {
            selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP
        },
        if (selectionType == ExchangeRecipientSelectionType.TRUSTED_GROUP)
            ExchangeRecipientAcceptanceStatus.PENDING
        else
            ExchangeRecipientAcceptanceStatus.NOT_REQUIRED,
    )

    private fun assertGroupAccess()
    {
        val probe = ExchangeAuthorizationProbe()
        val groupId = UUID.randomUUID()
        val groupShare = probe.share(
            role = ExchangeShareRoleName.VIEWER,
            principalKind = PrincipalKind.PRINCIPAL_GROUP,
            principalId = groupId,
        )
        val membership = probe.groupMembership(groupId, PrincipalGroupRoleName.MEMBER)
        probe.assertAllowed(Action.EXCHANGE_VIEW, listOf(groupShare), memberships = listOf(membership))
    }
}
