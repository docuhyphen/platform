package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeRecipientSelectionType
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.resource.model.InternalGroupRecipientSelectionRequest
import com.docuhyphen.app.api.service.auth.authz.Action
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeGroupAccessPermutationTest
{
    @Test
    fun `EX-GRP-01 internal primary group materializes inherited Shares`()
    {
        val fixture = TrustedGroupShareFixture(ShareStatus.PENDING_APPROVAL, eligible = true, withAttestation = false)
        fixture.service.activate(fixture.parentShare.id)
        verify(fixture.repository).save(any())
    }

    @Test
    fun `EX-GRP-02 internal group Owner can accept`()
    {
        assertGroupDecisionAllowed()
    }

    @Test
    fun `EX-GRP-03 internal group Member cannot accept`()
    {
        assertGroupDecisionDenied()
    }

    @Test
    fun `EX-GRP-04 new internal group member receives inherited access`()
    {
        val fixture = TrustedGroupShareFixture(ShareStatus.ACTIVE, eligible = true, withAttestation = false)
        fixture.service.synchronizeGroupMemberAccess(
            fixture.groupId,
            PrincipalKind.USER,
            fixture.userId,
            active = true,
        )
        verify(fixture.repository).save(any())
    }

    @Test
    fun `EX-GRP-05 removed internal group member loses inherited access`()
    {
        val fixture = TrustedGroupShareFixture(ShareStatus.ACTIVE, eligible = true)
        val inherited = fixture.inheritedShare()
        whenever(fixture.repository.findBySourceShareId(fixture.parentShare.id)).thenReturn(listOf(inherited))
        fixture.service.synchronizeGroupMemberAccess(
            fixture.groupId,
            PrincipalKind.USER,
            fixture.userId,
            active = false,
        )
        verify(fixture.repository).update(inherited)
    }

    @Test
    fun `EX-GRP-06 removed group member retains separate direct access`()
    {
        val probe = ExchangeAuthorizationProbe()
        probe.assertAllowed(Action.EXCHANGE_VIEW, listOf(probe.share(ExchangeShareRoleName.VIEWER)))
    }

    @Test
    fun `EX-GRP-07 revoked parent group Share revokes inherited access`()
    {
        val probe = ExchangeAuthorizationProbe()
        val inherited = probe.share(status = ShareStatus.REVOKED)
        probe.assertDenied(Action.EXCHANGE_VIEW, listOf(inherited))
    }

    @Test
    fun `EX-GRP-08 inactive group cannot grant new access`()
    {
        val probe = ExchangeRecipientSelectionProbe()
        val group = PrincipalGroup().apply {
            name = "Inactive group"
            scope = PrincipalGroupScope.ORG
            ownerOrganizationId = probe.callerOrganizationId
            isActive = false
        }
        probe.registerGroup(group)
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(InternalGroupRecipientSelectionRequest(group.id.toString()))
        }
    }

    @Test
    fun `EX-GRP-09 trusted participant group materializes inherited access`()
    {
        val fixture = TrustedGroupShareFixture(ShareStatus.PENDING_APPROVAL, eligible = true)
        fixture.service.activate(fixture.parentShare.id)
        verify(fixture.repository).save(any())
    }

    @Test
    fun `EX-GRP-10 trust suspension preserves existing materialized access`()
    {
        val probe = ExchangeAuthorizationProbe()
        probe.assertAllowed(Action.EXCHANGE_VIEW, listOf(probe.share()))
    }

    @Test
    fun `EX-GRP-11 member added during trust suspension receives no access`()
    {
        val fixture = TrustedGroupShareFixture(ShareStatus.ACTIVE, eligible = false)
        fixture.service.synchronizeGroupMemberAccess(
            fixture.groupId,
            PrincipalKind.USER,
            fixture.userId,
            active = true,
        )
        verify(fixture.repository, never()).save(any())
    }

    @Test
    fun `EX-GRP-12 member removed during trust suspension loses access`()
    {
        val fixture = TrustedGroupShareFixture(ShareStatus.ACTIVE, eligible = false)
        val inherited = fixture.inheritedShare()
        whenever(fixture.repository.findBySourceShareId(fixture.parentShare.id)).thenReturn(listOf(inherited))
        fixture.service.synchronizeGroupMemberAccess(
            fixture.groupId,
            PrincipalKind.USER,
            fixture.userId,
            active = false,
        )
        verify(fixture.repository).update(inherited)
    }

    @Test
    fun `EX-GRP-13 trust resumption reconciles eligible group members`()
    {
        val fixture = TrustedGroupShareFixture(ShareStatus.ACTIVE, eligible = true)
        fixture.service.reconcileGroupShare(fixture.parentShare.id)
        verify(fixture.repository).save(any())
    }

    @Test
    fun `EX-GRP-14 ended trust preserves existing access and blocks future materialization`()
    {
        val probe = ExchangeAuthorizationProbe()
        probe.assertAllowed(Action.EXCHANGE_VIEW, listOf(probe.share()))
        val fixture = TrustedGroupShareFixture(ShareStatus.ACTIVE, eligible = false)
        fixture.service.synchronizeGroupMemberAccess(
            fixture.groupId,
            PrincipalKind.USER,
            fixture.userId,
            active = true,
        )
        verify(fixture.repository, never()).save(any())
    }

    @Test
    fun `EX-GRP-15 unpublishing trusted group blocks pending acceptance`()
    {
        val fixture = TrustedGroupShareFixture(ShareStatus.PENDING_APPROVAL, eligible = false)
        assertThrows(IllegalArgumentException::class.java) {
            fixture.service.activate(fixture.parentShare.id)
        }
        verify(fixture.repository, never()).update(any())
    }

    @Test
    fun `EX-GRP-16 decision maker demotion blocks trusted group acceptance`()
    {
        assertGroupDecisionDenied()
    }

    @Test
    fun `EX-GRP-17 group without Owner or Manager cannot accept`()
    {
        assertGroupDecisionDenied()
    }

    private fun assertGroupDecisionAllowed()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP,
            principalKind = PrincipalKind.PRINCIPAL_GROUP,
            decisionMaker = true,
        )
        fixture.recordPrimary(accepted = true)
    }

    private fun assertGroupDecisionDenied()
    {
        val fixture = ExchangeRecipientDecisionFixture(
            selectionType = ExchangeRecipientSelectionType.TRUSTED_GROUP,
            principalKind = PrincipalKind.PRINCIPAL_GROUP,
            decisionMaker = false,
        )
        assertThrows(IllegalArgumentException::class.java) {
            fixture.recordPrimary(accepted = true)
        }
    }
}
