package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.auth.authz.ResourceContextResolution
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

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
        roleName = role.name
        source = ShareSource.DIRECT
        status = ShareStatus.ACTIVE
        constraintsJson = null
    }

    private fun buildService(
        groupMembers: List<PrincipalGroupMember> = emptyList(),
        groupShareOverride: Pair<UUID, List<Share>>? = null,
        memberShares: List<Share> = emptyList(),
        expansion: GroupShareExpansionPolicy = GroupShareExpansionPolicy.UNRESTRICTED,
    ): DefaultAuthorizationService
    {
        val shareRepo = mock<ShareRepository>()
        whenever(shareRepo.findActiveForPrincipalOnResource(eq(PrincipalKind.USER), any(), any(), any()))
            .thenReturn(memberShares)
        whenever(shareRepo.findActiveForPrincipalOnResource(eq(PrincipalKind.PRINCIPAL_GROUP), any(), any(), any()))
            .thenReturn(emptyList())
        if (groupShareOverride != null)
        {
            whenever(
                shareRepo.findActiveForPrincipalOnResource(
                    eq(PrincipalKind.PRINCIPAL_GROUP), eq(groupShareOverride.first), any(), any()
                )
            ).thenReturn(groupShareOverride.second)
            groupShareOverride.second.forEach { whenever(shareRepo.findById(it.id)).thenReturn(it) }
        }

        val groupMemberRepo = mock<PrincipalGroupMemberRepository>()
        whenever(groupMemberRepo.findGroupsForPrincipal(any(), any())).thenReturn(groupMembers)
        whenever(groupMemberRepo.findMembership(any(), any(), any())).thenAnswer { invocation ->
            groupMembers.firstOrNull {
                it.principalGroupId == invocation.getArgument(0) &&
                        it.principalKind == invocation.getArgument(1) &&
                        it.principalId == invocation.getArgument(2)
            }
        }

        val registry = mock<ResourceAuthorizationContextRegistry>()
        whenever(registry.resolution(any<ResourceRef>())).thenReturn(
            ResourceContextResolution.Resolved(
                ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(UUID.randomUUID())),
            ),
        )

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
            groupShareExpansionPolicy = expansion,
        )
    }

    private fun inheritedShare(groupShare: Share, userId: UUID): Share = Share().apply {
        id = UUID.randomUUID()
        principalKind = PrincipalKind.USER
        principalId = userId
        resourceType = groupShare.resourceType
        resourceId = groupShare.resourceId
        roleName = groupShare.roleName
        source = ShareSource.INHERITED_FROM_GROUP
        sourceShareId = groupShare.id
        status = ShareStatus.ACTIVE
        constraintsJson = null
    }

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

    @Test
    fun `a group Share that no longer expands grants nothing to a member it never materialized`() {
        val userId = UUID.randomUUID()
        val groupShare = makeGroupShare(legalGroupId, exchangeId)
        val svc = buildService(
            groupMembers = listOf(makeGroupMembership(legalGroupId, userId)),
            groupShareOverride = legalGroupId to listOf(groupShare),
            expansion = GroupShareExpansionPolicy { false },
        )

        val decision = svc.authorize(
            PrincipalRef.user(userId), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), AuthorizationContext()
        )

        assertFalse(decision.isAllowed) { "A member the group Share holds back must not reach the Exchange through it" }
    }

    @Test
    fun `a member materialized from a group Share keeps its grant after the Share stops expanding`() {
        val userId = UUID.randomUUID()
        val groupShare = makeGroupShare(legalGroupId, exchangeId)
        val consulted = mutableListOf<UUID>()
        val svc = buildService(
            groupMembers = listOf(makeGroupMembership(legalGroupId, userId)),
            groupShareOverride = legalGroupId to listOf(groupShare),
            memberShares = listOf(inheritedShare(groupShare, userId)),
            expansion = GroupShareExpansionPolicy { share ->
                consulted += share.id
                false
            },
        )

        val decision = svc.authorize(
            PrincipalRef.user(userId), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), AuthorizationContext()
        )

        assertTrue(decision.isAllowed) { "A member admitted while the group Share expanded keeps that access" }
        assertTrue(consulted.isEmpty()) { "A materialized member needs no expansion decision" }
    }

    @Test
    fun `a group Share that still expands grants a member it has not materialized yet`() {
        val userId = UUID.randomUUID()
        val groupShare = makeGroupShare(legalGroupId, exchangeId)
        val svc = buildService(
            groupMembers = listOf(makeGroupMembership(legalGroupId, userId)),
            groupShareOverride = legalGroupId to listOf(groupShare),
            expansion = GroupShareExpansionPolicy { share -> share.id == groupShare.id },
        )

        val decision = svc.authorize(
            PrincipalRef.user(userId), Action.EXCHANGE_VIEW, ResourceRef.exchange(exchangeId), AuthorizationContext()
        )

        assertTrue(decision.isAllowed)
    }
}
