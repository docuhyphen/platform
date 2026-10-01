package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicy
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicyRegistry
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritanceResolver
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyEvaluatorRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.informationrequest.parent.InformationRequestParentGrantInheritancePolicy
import jakarta.enterprise.inject.Instance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestClosedRecordAuthorizationTest
{
    private val orgId = UUID.randomUUID()
    private val exchangeRef = ResourceRef.exchange(UUID.randomUUID())
    private val requestRef = ResourceRef.informationRequest(UUID.randomUUID())
    private val owner = PrincipalRef.user(UUID.randomUUID())
    private val reviewer = PrincipalRef.user(UUID.randomUUID())
    private val shares = listOf(
        share(owner, exchangeRef, ExchangeShareRoleName.OWNER.name),
        share(reviewer, requestRef, InformationRequestShareRoleKey.REVIEWER.name),
    )
    private val service = service()

    @Test
    fun `a closed request still takes the record actions its lifecycle allows after closure`()
    {
        listOf(
            Action.INFORMATION_REQUEST_VIEW,
            Action.INFORMATION_REQUEST_PROMOTE_FACT,
            Action.INFORMATION_REQUEST_RECORD_DECISION,
            Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES,
        ).forEach { action -> assertAllowed(owner, action) }
        assertAllowed(reviewer, Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES)
    }

    @Test
    fun `every other write to a closed request stays refused as archived`()
    {
        listOf(
            Action.INFORMATION_REQUEST_CANCEL,
            Action.INFORMATION_REQUEST_SUPERSEDE,
            Action.INFORMATION_REQUEST_MANAGE_PARTIES,
            Action.INFORMATION_REQUEST_MANAGE_CLOCKS,
            Action.INFORMATION_REQUEST_MANAGE_REVIEWS,
        ).forEach { action -> assertArchived(owner, action) }
        assertArchived(reviewer, Action.INFORMATION_REQUEST_REVIEW)
        assertArchived(reviewer, Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT)
    }

    @Test
    fun `the record actions still need the capability behind them`()
    {
        assertEquals(Decision.REASON_NO_GRANT, (decide(reviewer, Action.INFORMATION_REQUEST_MANAGE_EXTERNAL_SOURCES) as Decision.Deny).reasonCode)
        assertEquals(Decision.REASON_NO_GRANT, (decide(owner, Action.INFORMATION_REQUEST_DECIDE_EXTERNAL_VALUES) as Decision.Deny).reasonCode)
    }

    private fun assertAllowed(principal: PrincipalRef, action: Action)
    {
        val decision = decide(principal, action)
        assertTrue(decision.isAllowed) { "$action on a closed request was refused: $decision" }
    }

    private fun assertArchived(principal: PrincipalRef, action: Action)
    {
        val decision = decide(principal, action)
        assertEquals(Decision.REASON_EXCHANGE_ARCHIVED, (decision as? Decision.Deny)?.reasonCode) { "$action: $decision" }
    }

    private fun decide(principal: PrincipalRef, action: Action): Decision =
        service.authorize(principal, action, requestRef, AuthorizationContext())

    private fun share(principal: PrincipalRef, resource: ResourceRef, role: String) = Share().apply {
        id = UUID.randomUUID()
        principalKind = principal.kind
        principalId = principal.id
        resourceType = resource.type
        resourceId = resource.id
        roleName = role
        source = ShareSource.DIRECT
        status = ShareStatus.ACTIVE
    }

    private fun service(): DefaultAuthorizationService
    {
        val contexts = contextRegistry()
        val policyRegistry = ParentGrantInheritancePolicyRegistry()
        val policies = mock<Instance<ParentGrantInheritancePolicy>>()
        whenever(policies.iterator()).thenReturn(mutableListOf<ParentGrantInheritancePolicy>(InformationRequestParentGrantInheritancePolicy()).iterator())
        ParentGrantInheritancePolicyRegistry::class.java.getDeclaredField("policies").apply { isAccessible = true }.set(policyRegistry, policies)
        policyRegistry.init()

        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any())).thenAnswer { invocation ->
            val kind = invocation.arguments[0] as PrincipalKind
            val id = invocation.arguments[1] as UUID
            val type = invocation.arguments[2] as ResourceType
            val resourceId = invocation.arguments[3] as UUID
            shares.filter { it.principalKind == kind && it.principalId == id && it.resourceType == type && it.resourceId == resourceId }
        }
        shares.forEach { share -> whenever(shareRepository.findById(eq(share.id!!))).thenReturn(share) }

        return DefaultAuthorizationService(
            shareRepository = shareRepository,
            shareLinkRepository = mock<ShareLinkRepository>(),
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>().also { whenever(it.findActiveForUser(any())).thenReturn(emptyList()) },
            principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>().also {
                whenever(it.findGroupsForPrincipal(any(), any())).thenReturn(emptyList())
            },
            organizationMembershipRepository = mock<OrganizationMembershipRepository>().also {
                whenever(it.findActiveByUserAndOrg(any(), any())).thenReturn(null)
            },
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = contexts,
            parentGrantInheritanceResolver = ParentGrantInheritanceResolver(policyRegistry, contexts),
            resourcePolicyEvaluatorRegistry = ResourcePolicyEvaluatorRegistry(),
        )
    }

    private fun contextRegistry(): ResourceAuthorizationContextRegistry
    {
        val requestProvider = mock<ResourceAuthorizationContextProvider>()
        whenever(requestProvider.supportedKind).thenReturn(ResourceKind.INFORMATION_REQUEST)
        whenever(requestProvider.resolve(eq(requestRef.id))).thenReturn(
            ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(orgId), isArchived = true, parentRef = exchangeRef),
        )
        val exchangeProvider = mock<ResourceAuthorizationContextProvider>()
        whenever(exchangeProvider.supportedKind).thenReturn(ResourceKind.EXCHANGE)
        whenever(exchangeProvider.resolve(eq(exchangeRef.id))).thenReturn(ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(orgId)))
        val instance = mock<Instance<ResourceAuthorizationContextProvider>>()
        whenever(instance.iterator()).thenReturn(mutableListOf(requestProvider, exchangeProvider).iterator())
        val registry = ResourceAuthorizationContextRegistry()
        ResourceAuthorizationContextRegistry::class.java.getDeclaredField("providers").apply { isAccessible = true }.set(registry, instance)
        registry.init()
        return registry
    }
}
