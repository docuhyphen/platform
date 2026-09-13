package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import com.docuhyphen.app.api.service.auth.authz.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.*

internal class ExchangeAuthorizationProbe
{
    val principalId: UUID = UUID.randomUUID()
    val resourceId: UUID = UUID.randomUUID()

    fun share(
        role: ExchangeShareRoleName = ExchangeShareRoleName.VIEWER,
        status: ShareStatus = ShareStatus.ACTIVE,
        constraintsJson: String? = null,
        principalKind: PrincipalKind = PrincipalKind.USER,
        principalId: UUID = this.principalId,
        source: ShareSource = ShareSource.DIRECT,
        sourceShareId: UUID? = null,
        expiresAt: Instant? = null,
    ): Share = Share().apply {
        id = UUID.randomUUID()
        resourceType = ResourceType.EXCHANGE
        resourceId = this@ExchangeAuthorizationProbe.resourceId
        this.principalKind = principalKind
        this.principalId = principalId
        roleName = role.name
        this.source = source
        this.sourceShareId = sourceShareId
        this.status = status
        this.constraintsJson = constraintsJson
        this.expiresAt = expiresAt?.let(Timestamp::from)
    }

    fun groupMembership(
        groupId: UUID,
        role: PrincipalGroupRoleName,
        active: Boolean = true,
    ): PrincipalGroupMember = PrincipalGroupMember().apply {
        principalGroupId = groupId
        principalKind = PrincipalKind.USER
        principalId = this@ExchangeAuthorizationProbe.principalId
        groupRole = role
        isActive = active
    }

    fun authorize(
        action: Action,
        shares: List<Share>,
        context: AuthorizationContext = AuthorizationContext(),
        memberships: List<PrincipalGroupMember> = emptyList(),
        resourceContext: ResourceAuthorizationContext? = null,
        principal: PrincipalRef = PrincipalRef.user(principalId),
    ): Decision = service(shares, memberships, resourceContext)
        .authorize(principal, action, ResourceRef.exchange(resourceId), context)

    fun assertAllowed(
        action: Action,
        shares: List<Share>,
        context: AuthorizationContext = AuthorizationContext(),
        memberships: List<PrincipalGroupMember> = emptyList(),
        resourceContext: ResourceAuthorizationContext? = null,
    )
    {
        assertTrue(
            authorize(action, shares, context, memberships, resourceContext).isAllowed,
            "Expected $action to be allowed",
        )
    }

    fun assertDenied(
        action: Action,
        shares: List<Share>,
        context: AuthorizationContext = AuthorizationContext(),
        memberships: List<PrincipalGroupMember> = emptyList(),
        resourceContext: ResourceAuthorizationContext? = null,
    )
    {
        assertFalse(
            authorize(action, shares, context, memberships, resourceContext).isAllowed,
            "Expected $action to be denied",
        )
    }

    fun personalResourceContext(
        archived: Boolean = false,
        suspended: Boolean = false,
    ): ResourceAuthorizationContext = ResourceAuthorizationContext(
        ownerContext = OwnerContext.Personal(principalId),
        isArchived = archived,
        isSuspended = suspended,
    )

    private fun service(
        shares: List<Share>,
        memberships: List<PrincipalGroupMember>,
        resourceContext: ResourceAuthorizationContext?,
    ): DefaultAuthorizationService
    {
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any()))
            .thenAnswer { invocation ->
                val kind = invocation.getArgument<PrincipalKind>(0)
                val id = invocation.getArgument<UUID>(1)
                shares.filter { it.principalKind == kind && it.principalId == id }
            }
        whenever(shareRepository.findDirectForPrincipalOnResource(any(), any(), any(), any()))
            .thenAnswer { invocation ->
                val kind = invocation.getArgument<PrincipalKind>(0)
                val id = invocation.getArgument<UUID>(1)
                shares.filter {
                    it.principalKind == kind &&
                        it.principalId == id &&
                        it.source == ShareSource.DIRECT &&
                        it.sourceShareId == null
                }
            }
        whenever(shareRepository.findById(any())).thenAnswer { invocation ->
            val id = invocation.getArgument<UUID>(0)
            shares.firstOrNull { it.id == id }
        }

        val groupMemberRepository = mock<PrincipalGroupMemberRepository>()
        whenever(groupMemberRepository.findGroupsForPrincipal(any(), any()))
            .thenAnswer { invocation ->
                val kind = invocation.getArgument<PrincipalKind>(0)
                val id = invocation.getArgument<UUID>(1)
                memberships.filter { it.isActive && it.principalKind == kind && it.principalId == id }
            }
        whenever(groupMemberRepository.findMembership(any(), any(), any()))
            .thenAnswer { invocation ->
                val groupId = invocation.getArgument<UUID>(0)
                val kind = invocation.getArgument<PrincipalKind>(1)
                val id = invocation.getArgument<UUID>(2)
                memberships.firstOrNull {
                    it.principalGroupId == groupId &&
                        it.principalKind == kind &&
                        it.principalId == id
                }
            }

        val registry = mock<ResourceAuthorizationContextRegistry>()
        // An Exchange under test always exists, so its facts always resolve. A probe that says
        // nothing about resource state gets an organization-owned, non-archived Exchange whose
        // owner the caller is not a member of, so shares alone decide the outcome.
        whenever(registry.resolution(any<ResourceRef>())).thenReturn(
            ResourceContextResolution.Resolved(
                resourceContext
                    ?: ResourceAuthorizationContext(
                        ownerContext = OwnerContext.Organization(UUID.randomUUID()),
                    ),
            ),
        )

        return DefaultAuthorizationService(
            shareRepository = shareRepository,
            shareLinkRepository = mock<ShareLinkRepository>(),
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>().also {
                whenever(it.findActiveForUser(any())).thenReturn(emptyList())
            },
            principalGroupMemberRepository = groupMemberRepository,
            organizationMembershipRepository = mock<OrganizationMembershipRepository>().also {
                whenever(it.findActiveByUserAndOrg(any(), any())).thenReturn(null)
            },
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = registry,
        )
    }
}
