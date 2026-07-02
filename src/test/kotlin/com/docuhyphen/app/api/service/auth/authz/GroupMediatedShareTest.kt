package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupMember
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.ShareLinkRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Group-mediated Share path in DefaultAuthorizationService.
 *
 * Rules verified (Phase 10 required matrix):
 *  - A user who is an active member of a group that has a Share on an Exchange is permitted
 *    (the "Legal group permitted" requirement).
 *  - A user who is an active member of a group that has NO Share on the same Exchange is denied
 *    (the "Finance group denied" requirement).
 *  - An inactive group membership yields no grant; findGroupsForPrincipal filters to isActive=true
 *    so the repository simply returns no groups for an inactive member.
 *  - A group with an EDITOR Share grants both EXCHANGE_VIEW and EXCHANGE_EDIT to the member.
 *  - Two independent users with different groups on the same exchange produce the expected
 *    Allow/Deny split.
 */
class GroupMediatedShareTest
{
    private val exchangeId: UUID = UUID.randomUUID()
    private val legalGroupId: UUID = UUID.randomUUID()
    private val financeGroupId: UUID = UUID.randomUUID()

    private fun makeGroupMembership(groupId: UUID, userId: UUID): PrincipalGroupMember =
        PrincipalGroupMember().apply {
            id = UUID.randomUUID()
            principalGroupId = groupId
            principalKind = PrincipalKind.USER
            principalId = userId
            groupRole = PrincipalGroupRoleName.MEMBER
            isActive = true
        }

    private fun makeGroupShare(
        groupId: UUID,
        resourceId: UUID,
        role: ExchangeShareRoleName = ExchangeShareRoleName.VIEWER,
    ): Share = Share().apply {
        id = UUID.randomUUID()
        principalKind = PrincipalKind.PRINCIPAL_GROUP
        principalId = groupId
        resourceType = ResourceType.EXCHANGE
        this.resourceId = resourceId
        roleName = role
        source = ShareSource.DIRECT
        status = ShareStatus.ACTIVE
        constraintsJson = null
    }

    private fun buildService(
        groupMembers: List<PrincipalGroupMember> = emptyList(),
        groupShareOverride: Pair<UUID, List<Share>>? = null,
    ): DefaultAuthorizationService
    {
        val shareRepo = mock<ShareRepository>()
        whenever(shareRepo.findActiveForPrincipalOnResource(eq(PrincipalKind.USER), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(shareRepo.findActiveForPrincipalOnResource(eq(PrincipalKind.PRINCIPAL_GROUP), any(), any(), any()))
            .thenReturn(emptyList())
        if (groupShareOverride != null)
        {
            whenever(
                shareRepo.findActiveForPrincipalOnResource(
                    eq(PrincipalKind.PRINCIPAL_GROUP), eq(groupShareOverride.first), any(), any()
                )
            ).thenReturn(groupShareOverride.second)
        }

        val groupMemberRepo = mock<PrincipalGroupMemberRepository>()
        whenever(groupMemberRepo.findGroupsForPrincipal(any(), any())).thenReturn(groupMembers)
        whenever(groupMemberRepo.findMembership(any(), any(), any())).thenReturn(null)

        val registry = mock<ResourceAuthorizationContextRegistry>()
        whenever(registry.resolve(any<ResourceRef>())).thenReturn(null)

        return DefaultAuthorizationService(
            shareRepository = shareRepo,
            shareLinkRepository = mock<ShareLinkRepository>(),
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>().also {
                whenever(it.findActiveForUser(any())).thenReturn(emptyList())
            },
            principalGroupMemberRepository = groupMemberRepo,
            organizationMembershipRepository = mock<OrganizationMembershipRepository>().also {
                whenever(it.findActiveByUserAndOrg(any(), any())).thenReturn(null)
            },
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = registry,
        )
    }

    // -------------------------------------------------------------------------
    // Legal group permitted, Finance group denied
    // -------------------------------------------------------------------------

    @Test
    fun `member of Legal group with VIEWER Share on Exchange is permitted`()
    {
        val userId = UUID.randomUUID()
        val membership = makeGroupMembership(legalGroupId, userId)
        val groupShare = makeGroupShare(legalGroupId, exchangeId)

        val svc = buildService(
            groupMembers = listOf(membership),
            groupShareOverride = legalGroupId to listOf(groupShare),
        )

        val decision = svc.authorize(
            PrincipalRef.user(userId), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), AuthorizationContext()
        )

        assertTrue(decision.isAllowed) { "Legal group member must be allowed via group Share" }
    }

    @Test
    fun `member of Finance group with NO Share on Exchange is denied`()
    {
        val userId = UUID.randomUUID()
        val membership = makeGroupMembership(financeGroupId, userId)

        val svc = buildService(
            groupMembers = listOf(membership),
        )

        val decision = svc.authorize(
            PrincipalRef.user(userId), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), AuthorizationContext()
        )

        assertFalse(decision.isAllowed) { "Finance group member with no Share must be denied" }
    }

    @Test
    fun `inactive group membership yields no grant`()
    {
        val userId = UUID.randomUUID()
        val groupShare = makeGroupShare(legalGroupId, exchangeId)

        val svc = buildService(
            groupMembers = emptyList(),
            groupShareOverride = legalGroupId to listOf(groupShare),
        )

        val decision = svc.authorize(
            PrincipalRef.user(userId), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), AuthorizationContext()
        )

        assertFalse(decision.isAllowed) {
            "Inactive group membership must not grant access: findGroupsForPrincipal returns only isActive=true rows"
        }
    }

    @Test
    fun `group EDITOR Share grants both EXCHANGE_VIEW and EXCHANGE_EDIT to member`()
    {
        val userId = UUID.randomUUID()
        val membership = makeGroupMembership(legalGroupId, userId)
        val editorShare = makeGroupShare(legalGroupId, exchangeId, role = ExchangeShareRoleName.EDITOR)

        val svc = buildService(
            groupMembers = listOf(membership),
            groupShareOverride = legalGroupId to listOf(editorShare),
        )

        val resource = ResourceRef.exchange(exchangeId)
        val principal = PrincipalRef.user(userId)

        assertTrue(svc.authorize(principal, Action.EXCHANGE_VIEW, resource, AuthorizationContext()).isAllowed)
        assertTrue(svc.authorize(principal, Action.EXCHANGE_EDIT, resource, AuthorizationContext()).isAllowed)
    }

    @Test
    fun `Legal user permitted while Finance user denied on same Exchange`()
    {
        val legalUser = UUID.randomUUID()
        val financeUser = UUID.randomUUID()
        val legalShare = makeGroupShare(legalGroupId, exchangeId)

        val legalSvc = buildService(
            groupMembers = listOf(makeGroupMembership(legalGroupId, legalUser)),
            groupShareOverride = legalGroupId to listOf(legalShare),
        )
        val financeSvc = buildService(
            groupMembers = listOf(makeGroupMembership(financeGroupId, financeUser)),
        )

        val resource = ResourceRef.exchange(exchangeId)

        assertTrue(
            legalSvc.authorize(PrincipalRef.user(legalUser), Action.EXCHANGE_VIEW, resource, AuthorizationContext()).isAllowed
        )
        assertFalse(
            financeSvc.authorize(PrincipalRef.user(financeUser), Action.EXCHANGE_VIEW, resource, AuthorizationContext()).isAllowed
        )
    }
}
