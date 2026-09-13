package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.application.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.exchange.ShareLinkRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import jakarta.enterprise.inject.Instance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * A resource kind whose authorization facts must come from a registered provider is refused
 * while that provider is missing, instead of being decided from grants that were never scoped
 * to it.
 *
 * The runtime Information Request kinds have no provider until the runtime aggregate exists,
 * so every decision about one denies with a stable reason and contributes no grants. The
 * Exchange characterization in the same test pins the behavior of a kind whose provider is
 * installed, so the refusal is visibly limited to a resource whose facts are missing.
 */
class InformationRequestResourceContextFailClosedTest
{
    private val userId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()
    private val principal = PrincipalRef.user(userId)
    private val context = AuthorizationContext(activeOrgId = orgId)

    @Test
    fun `request kinds have no provider yet so their facts cannot be resolved`()
    {
        val registry = registryWith()

        assertInstanceOf(
            ResourceContextResolution.Unresolved::class.java,
            registry.resolution(ResourceRef.informationRequest(UUID.randomUUID())),
        )
        assertInstanceOf(
            ResourceContextResolution.Unresolved::class.java,
            registry.resolution(ResourceRef.informationRequestRequirement(UUID.randomUUID())),
        )
    }

    @Test
    fun `a type that carries no resource context is unaffected`()
    {
        val registry = registryWith()

        assertInstanceOf(
            ResourceContextResolution.NotGoverned::class.java,
            registry.resolution(ResourceRef(ResourceType.APPLICATION, UUID(0, 0))),
        )
    }

    @Test
    fun `registering the provider stops the refusal`()
    {
        val provider = mock<ResourceAuthorizationContextProvider>()
        whenever(provider.supportedKind).thenReturn(ResourceKind.INFORMATION_REQUEST)
        whenever(provider.resolve(any()))
            .thenReturn(ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(orgId)))
        val registry = registryWith(provider)

        assertInstanceOf(
            ResourceContextResolution.Resolved::class.java,
            registry.resolution(ResourceRef.informationRequest(UUID.randomUUID())),
        )
        assertInstanceOf(
            ResourceContextResolution.Unresolved::class.java,
            registry.resolution(ResourceRef.informationRequestRequirement(UUID.randomUUID())),
        )
    }

    @Test
    fun `authorizing a request action denies with the unresolved context reason`()
    {
        val service = serviceWith(registryWith())

        val decision = service.authorize(
            principal,
            Action.INFORMATION_REQUEST_VIEW,
            ResourceRef.informationRequest(UUID.randomUUID()),
            context,
        )

        val deny = decision as? Decision.Deny
        assertTrue(deny != null) { "an unresolvable request resource must deny" }
        assertEquals(Decision.REASON_RESOURCE_CONTEXT_UNRESOLVED, deny!!.reasonCode)
    }

    @Test
    fun `a request resource collects no grants from the callers active organization`()
    {
        val service = serviceWith(registryWith())

        val grants = service.grantsOn(principal, ResourceRef.informationRequest(UUID.randomUUID()), context)
        val capabilities = service.capabilities(principal, ResourceRef.informationRequest(UUID.randomUUID()), context)

        assertTrue(grants.isEmpty()) { "an active-organization role must not reach a request resource" }
        assertTrue(capabilities.isEmpty())
    }

    @Test
    fun `a resolvable Exchange keeps its organization membership grant`()
    {
        val exchangeProvider = mock<ResourceAuthorizationContextProvider>()
        whenever(exchangeProvider.supportedKind).thenReturn(ResourceKind.EXCHANGE)
        whenever(exchangeProvider.resolve(any()))
            .thenReturn(ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(orgId)))
        val service = serviceWith(registryWith(exchangeProvider))

        val grants = service.grantsOn(principal, ResourceRef.exchange(UUID.randomUUID()), context)

        assertTrue(grants.any { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }) {
            "existing Exchange behavior must be unchanged by the request vocabulary"
        }
    }

    // -------------------------------------------------------------------------
    // fixtures
    // -------------------------------------------------------------------------

    private fun registryWith(vararg providers: ResourceAuthorizationContextProvider): ResourceAuthorizationContextRegistry
    {
        val instance = mock<Instance<ResourceAuthorizationContextProvider>>()
        whenever(instance.iterator()).thenReturn(providers.toMutableList().iterator())

        val registry = ResourceAuthorizationContextRegistry()
        ResourceAuthorizationContextRegistry::class.java
            .getDeclaredField("providers")
            .apply { isAccessible = true }
            .set(registry, instance)
        registry.init()
        return registry
    }

    private fun serviceWith(registry: ResourceAuthorizationContextRegistry): DefaultAuthorizationService
    {
        val membership = OrganizationMembership().apply {
            appUserId = userId
            organizationId = orgId
            roles = mutableSetOf(OrganizationRoleName.ORG_ADMIN)
        }
        val membershipRepository = mock<OrganizationMembershipRepository>()
        whenever(membershipRepository.findActiveByUserAndOrg(eq(userId), eq(orgId))).thenReturn(membership)

        return DefaultAuthorizationService(
            shareRepository = mock<ShareRepository>(),
            shareLinkRepository = mock<ShareLinkRepository>(),
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>(),
            principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>(),
            organizationMembershipRepository = membershipRepository,
            principalGroupRepository = mock<PrincipalGroupRepository>(),
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = registry,
        )
    }
}






