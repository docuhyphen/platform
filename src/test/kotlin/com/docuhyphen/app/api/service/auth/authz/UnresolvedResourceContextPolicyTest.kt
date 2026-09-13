package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupScope
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
 * A resource whose type declares that it carries its own authorization facts is refused when
 * those facts cannot be resolved, and its organization-role grants come from the resolved owner
 * rather than from whichever organization the caller happens to have selected.
 *
 * Types that deliberately carry no resource context, being the platform control-plane types,
 * keep their previous behavior including the active-organization fallback.
 */
class UnresolvedResourceContextPolicyTest
{
    private val userId = UUID.randomUUID()
    private val activeOrgId = UUID.randomUUID()
    private val ownerOrgId = UUID.randomUUID()
    private val principal = PrincipalRef.user(userId)
    private val context = AuthorizationContext(activeOrgId = activeOrgId)

    // -------------------------------------------------------------------------
    // resolution outcomes
    // -------------------------------------------------------------------------

    @Test
    fun `resolution separates a governed resource from one that carries no context`()
    {
        val registry = registryWith(providerFor(ResourceKind.EXCHANGE, contextOwnedBy(OwnerContext.Organization(ownerOrgId))))

        assertInstanceOf(
            ResourceContextResolution.Resolved::class.java,
            registry.resolution(ResourceRef.exchange(UUID.randomUUID())),
        )
        assertInstanceOf(
            ResourceContextResolution.Unresolved::class.java,
            registry.resolution(ResourceRef.document(UUID.randomUUID())),
        )
        assertInstanceOf(
            ResourceContextResolution.NotGoverned::class.java,
            registry.resolution(ResourceRef(ResourceType.APPLICATION, UUID(0, 0))),
        )
        assertInstanceOf(
            ResourceContextResolution.NotGoverned::class.java,
            registry.resolution(ResourceRef(ResourceType.WORKFLOW_WEBHOOK_ENDPOINT, UUID.randomUUID())),
        )
    }

    @Test
    fun `a governed resource whose row is gone is unresolved rather than resolved`()
    {
        val registry = registryWith(providerFor(ResourceKind.EXCHANGE, null))

        assertInstanceOf(
            ResourceContextResolution.Unresolved::class.java,
            registry.resolution(ResourceRef.exchange(UUID.randomUUID())),
        )
    }

    // -------------------------------------------------------------------------
    // authorize
    // -------------------------------------------------------------------------

    @Test
    fun `a governed kind with no registered provider denies with the unresolved reason`()
    {
        val service = serviceWith(registryWith())

        val decision = service.authorize(
            principal,
            Action.DOCUMENT_VIEW,
            ResourceRef.document(UUID.randomUUID()),
            context,
        )

        val deny = decision as? Decision.Deny
        assertTrue(deny != null) { "a governed resource with no resolvable facts must deny" }
        assertEquals(Decision.REASON_RESOURCE_CONTEXT_UNRESOLVED, deny!!.reasonCode)
    }

    @Test
    fun `a governed resource whose row is gone denies instead of skipping the state denies`()
    {
        val service = serviceWith(registryWith(providerFor(ResourceKind.EXCHANGE, null)))

        val decision = service.authorize(
            principal,
            Action.EXCHANGE_VIEW,
            ResourceRef.exchange(UUID.randomUUID()),
            context,
        )

        assertEquals(
            Decision.REASON_RESOURCE_CONTEXT_UNRESOLVED,
            (decision as Decision.Deny).reasonCode,
        )
    }

    // -------------------------------------------------------------------------
    // grants
    // -------------------------------------------------------------------------

    @Test
    fun `a governed kind with no registered provider contributes no grants`()
    {
        val service = serviceWith(registryWith())
        val document = ResourceRef.document(UUID.randomUUID())

        assertTrue(service.grantsOn(principal, document, context).isEmpty())
        assertTrue(service.capabilities(principal, document, context).isEmpty())
    }

    @Test
    fun `organization grants come from the resolved owner and not from the active organization`()
    {
        val registry = registryWith(
            providerFor(ResourceKind.EXCHANGE, contextOwnedBy(OwnerContext.Organization(ownerOrgId))),
        )
        val service = serviceWith(registry)

        val grants = service.grantsOn(principal, ResourceRef.exchange(UUID.randomUUID()), context)

        assertTrue(grants.any { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }) {
            "the owner organization's membership must still grant"
        }
        assertEquals(
            listOf(OrganizationRoleName.ORG_ADMIN.name),
            grants.filter { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }.map { it.roleName },
        )
    }

    @Test
    fun `a personally owned resource yields no organization grants`()
    {
        val registry = registryWith(
            providerFor(ResourceKind.EXCHANGE, contextOwnedBy(OwnerContext.Personal(userId))),
        )
        val service = serviceWith(registry)

        val grants = service.grantsOn(principal, ResourceRef.exchange(UUID.randomUUID()), context)

        assertTrue(grants.none { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP })
    }

    @Test
    fun `a type that carries no resource context keeps its active organization fallback`()
    {
        val service = serviceWith(registryWith())

        val applicationGrants = service.grantsOn(
            principal,
            ResourceRef(ResourceType.APPLICATION, UUID(0, 0)),
            context,
        )
        val webhookGrants = service.grantsOn(
            principal,
            ResourceRef(ResourceType.WORKFLOW_WEBHOOK_ENDPOINT, UUID.randomUUID()),
            context,
        )

        assertTrue(applicationGrants.any { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }) {
            "platform control-plane authorization must keep its current behavior"
        }
        assertTrue(webhookGrants.any { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP })
    }

    @Test
    fun `a group resolves through its own provider and keeps its owner organization grants`()
    {
        val groupId = UUID.randomUUID()
        val group = PrincipalGroup().apply {
            id = groupId
            name = "process group"
            scope = PrincipalGroupScope.ORG
            ownerOrganizationId = ownerOrgId
        }
        val groupRepository = mock<PrincipalGroupRepository>()
        whenever(groupRepository.findById(groupId)).thenReturn(group)

        val registry = registryWith(
            providerFor(
                ResourceKind.PRINCIPAL_GROUP,
                contextOwnedBy(OwnerContext.Organization(ownerOrgId)),
            ),
        )
        val service = serviceWith(registry, groupRepository)

        val grants = service.grantsOn(principal, ResourceRef.group(groupId), context)

        assertTrue(grants.any { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }) {
            "group management must keep resolving the group's owning organization"
        }
    }

    // -------------------------------------------------------------------------
    // fixtures
    // -------------------------------------------------------------------------

    private fun contextOwnedBy(owner: OwnerContext) = ResourceAuthorizationContext(ownerContext = owner)

    private fun providerFor(
        kind: ResourceKind,
        resolved: ResourceAuthorizationContext?,
    ): ResourceAuthorizationContextProvider
    {
        val provider = mock<ResourceAuthorizationContextProvider>()
        whenever(provider.supportedKind).thenReturn(kind)
        whenever(provider.resolve(any())).thenReturn(resolved)
        return provider
    }

    private fun registryWith(
        vararg providers: ResourceAuthorizationContextProvider,
    ): ResourceAuthorizationContextRegistry
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

    private fun serviceWith(
        registry: ResourceAuthorizationContextRegistry,
        groupRepository: PrincipalGroupRepository = mock<PrincipalGroupRepository>(),
    ): DefaultAuthorizationService
    {
        val membershipRepository = mock<OrganizationMembershipRepository>()
        whenever(membershipRepository.findActiveByUserAndOrg(eq(userId), eq(ownerOrgId)))
            .thenReturn(membershipIn(ownerOrgId))
        whenever(membershipRepository.findActiveByUserAndOrg(eq(userId), eq(activeOrgId)))
            .thenReturn(membershipIn(activeOrgId))

        return DefaultAuthorizationService(
            shareRepository = mock<ShareRepository>(),
            shareLinkRepository = mock<ShareLinkRepository>(),
            appRoleAssignmentRepository = mock<AppRoleAssignmentRepository>(),
            principalGroupMemberRepository = mock<PrincipalGroupMemberRepository>(),
            organizationMembershipRepository = membershipRepository,
            principalGroupRepository = groupRepository,
            applicationService = mock<ApplicationService>(),
            resourceContextRegistry = registry,
        )
    }

    private fun membershipIn(organization: UUID) = OrganizationMembership().apply {
        appUserId = userId
        organizationId = organization
        roles = mutableSetOf(OrganizationRoleName.ORG_ADMIN)
    }
}

