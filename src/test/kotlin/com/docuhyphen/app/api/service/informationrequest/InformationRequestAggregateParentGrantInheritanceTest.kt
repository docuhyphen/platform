package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
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
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.DefaultAuthorizationService
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicyRegistry
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextProvider
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourcePolicyEvaluatorRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.enterprise.inject.Instance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * The Exchange owner's Share already carries INFORMATION_REQUEST_CREATE, READ, CANCEL, and ADMIN
 * (see InformationRequestAuthorizationVocabularyTest), but that Share is scoped to the Exchange
 * resource, not to any one request. This proves the aggregate resource kind must register that it
 * takes its parent Exchange's grants for that owner authoring capability to ever reach a decision
 * made directly against one request, such as cancellation.
 */
class InformationRequestAggregateParentGrantInheritanceTest
{
    private val userId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()
    private val exchangeId = UUID.randomUUID()
    private val requestId = UUID.randomUUID()

    private val principal = PrincipalRef.user(userId)
    private val exchangeRef = ResourceRef.exchange(exchangeId)
    private val requestRef = ResourceRef.informationRequest(requestId)
    private val context = AuthorizationContext()

    @Test
    fun `the exchange owner's grant authorizes cancelling a request through parent inheritance`()
    {
        val service = serviceWith()

        val decision = service.authorize(principal, Action.INFORMATION_REQUEST_CANCEL, requestRef, context)

        assertTrue(decision.isAllowed) {
            "the Exchange owner's authoring grant must reach the request through parent inheritance, got $decision"
        }
        val grants = service.grantsOn(principal, requestRef, context)
        assertEquals(listOf(exchangeRef), grants.mapNotNull { it.inheritedFrom })

        val capabilities = grants.flatMap { it.capabilities }.toSet()
        assertTrue(Capability.INFORMATION_REQUEST_CANCEL in capabilities)
        assertTrue(Capability.EXCHANGE_DELETE !in capabilities) {
            "an Exchange-only capability must not leak onto the request resource through inheritance"
        }
        assertTrue(Capability.DOCUMENT_WRITE !in capabilities) {
            "a Document-only capability must not leak onto the request resource through inheritance"
        }
    }

    @Test
    fun `an unrelated principal is still denied`()
    {
        val service = serviceWith(sharePrincipalId = UUID.randomUUID())

        val decision = service.authorize(principal, Action.INFORMATION_REQUEST_CANCEL, requestRef, context)

        assertEquals(Decision.REASON_NO_GRANT, (decision as Decision.Deny).reasonCode)
    }

    private fun serviceWith(sharePrincipalId: UUID = userId): DefaultAuthorizationService
    {
        val contextRegistry = registryWith()

        val policyRegistry = ParentGrantInheritancePolicyRegistry()
        val policies = mock<Instance<com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicy>>()
        whenever(policies.iterator()).thenReturn(
            mutableListOf<com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritancePolicy>(
                InformationRequestParentGrantInheritancePolicy(),
            ).iterator(),
        )
        ParentGrantInheritancePolicyRegistry::class.java
            .getDeclaredField("policies")
            .apply { isAccessible = true }
            .set(policyRegistry, policies)
        policyRegistry.init()

        val share = Share().apply {
            id = UUID.randomUUID()
            principalKind = PrincipalKind.USER
            principalId = sharePrincipalId
            resourceType = exchangeRef.type
            resourceId = exchangeRef.id
            roleName = ExchangeShareRoleName.OWNER.name
            source = ShareSource.DIRECT
            status = ShareStatus.ACTIVE
        }
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any()))
            .thenAnswer { invocation ->
                val kind = invocation.arguments[0] as PrincipalKind
                val id = invocation.arguments[1] as UUID
                val type = invocation.arguments[2] as com.docuhyphen.app.api.model.entity.ResourceType
                val resourceId = invocation.arguments[3] as UUID
                if (kind == PrincipalKind.USER && id == sharePrincipalId &&
                    type == exchangeRef.type && resourceId == exchangeRef.id
                )
                {
                    listOf(share)
                }
                else
                {
                    emptyList<Share>()
                }
            }
        whenever(shareRepository.findById(eq(share.id!!))).thenReturn(share)

        return DefaultAuthorizationService(
            shareRepository = shareRepository,
            shareLinkRepository = mock<ShareLinkRepository>(),
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>().also {
                whenever(it.findActiveForUser(any())).thenReturn(emptyList())
            },
            principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>().also {
                whenever(it.findGroupsForPrincipal(any(), any())).thenReturn(emptyList())
            },
            organizationMembershipRepository = mock<OrganizationMembershipRepository>().also {
                whenever(it.findActiveByUserAndOrg(any(), any())).thenReturn(null)
            },
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = contextRegistry,
            parentGrantInheritanceResolver = com.docuhyphen.app.api.service.auth.authz.ParentGrantInheritanceResolver(
                policyRegistry,
                contextRegistry,
            ),
            resourcePolicyEvaluatorRegistry = ResourcePolicyEvaluatorRegistry(),
        )
    }

    private fun registryWith(): ResourceAuthorizationContextRegistry
    {
        val requestProvider = mock<ResourceAuthorizationContextProvider>()
        whenever(requestProvider.supportedKind).thenReturn(ResourceKind.INFORMATION_REQUEST)
        whenever(requestProvider.resolve(eq(requestId))).thenReturn(
            ResourceAuthorizationContext(
                ownerContext = OwnerContext.Organization(orgId),
                parentRef = exchangeRef,
            ),
        )

        val exchangeProvider = mock<ResourceAuthorizationContextProvider>()
        whenever(exchangeProvider.supportedKind).thenReturn(ResourceKind.EXCHANGE)
        whenever(exchangeProvider.resolve(eq(exchangeId))).thenReturn(
            ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(orgId)),
        )

        val instance = mock<Instance<ResourceAuthorizationContextProvider>>()
        whenever(instance.iterator()).thenReturn(
            mutableListOf(requestProvider, exchangeProvider).iterator(),
        )

        val registry = ResourceAuthorizationContextRegistry()
        ResourceAuthorizationContextRegistry::class.java
            .getDeclaredField("providers")
            .apply { isAccessible = true }
            .set(registry, instance)
        registry.init()
        return registry
    }
}
