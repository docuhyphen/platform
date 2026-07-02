package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.ShareLinkRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

/**
 * Phase 4 exit test: [DefaultAuthorizationService.collectOrgMembershipGrants] must use the
 * resource owner resolved by [ResourceAuthorizationContextRegistry], not the caller's active org.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeOwnerContextResolutionTest
{
    @Mock private lateinit var shareRepository: ShareRepository
    @Mock private lateinit var shareLinkRepository: ShareLinkRepository
    @Mock private lateinit var appRoleAssignmentRepository: AppRoleAssignmentRepository
    @Mock private lateinit var principalGroupMemberRepository: PrincipalGroupMemberRepository
    @Mock private lateinit var organizationMembershipRepository: OrganizationMembershipRepository
    @Mock private lateinit var principalGroupRepository: PrincipalGroupRepository
    @Mock private lateinit var applicationService: ApplicationService
    @Mock private lateinit var resourceContextRegistry: ResourceAuthorizationContextRegistry

    private lateinit var service: DefaultAuthorizationService

    private val callerUserId: UUID = UUID.randomUUID()
    private val orgAId: UUID = UUID.randomUUID()
    private val orgBId: UUID = UUID.randomUUID()
    private val exchangeId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setup()
    {
        service = DefaultAuthorizationService(
            shareRepository = shareRepository,
            shareLinkRepository = shareLinkRepository,
            appRoleAssignmentRepository = appRoleAssignmentRepository,
            principalGroupMemberRepository = principalGroupMemberRepository,
            organizationMembershipRepository = organizationMembershipRepository,
            principalGroupRepository = principalGroupRepository,
            applicationService = applicationService,
            resourceContextRegistry = resourceContextRegistry,
        )

        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)

        `when`(appRoleAssignmentRepository.findActiveForUser(callerUserId)).thenReturn(emptyList())
        `when`(
            shareRepository.findActiveForPrincipalOnResource(
                PrincipalKind.USER, callerUserId, ResourceType.EXCHANGE, exchangeId,
            )
        ).thenReturn(emptyList())
        `when`(principalGroupMemberRepository.findGroupsForPrincipal(PrincipalKind.USER, callerUserId))
            .thenReturn(emptyList())
    }

    @Test
    fun `org B membership grants are not applied to an exchange owned by org A`()
    {
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)
        `when`(resourceContextRegistry.resolve(resource))
            .thenReturn(ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(orgAId)))
        `when`(organizationMembershipRepository.findActiveByUserAndOrg(callerUserId, orgBId))
            .thenReturn(membership(orgBId))
        `when`(organizationMembershipRepository.findActiveByUserAndOrg(callerUserId, orgAId))
            .thenReturn(null)

        val principal = PrincipalRef.user(callerUserId)
        val context = AuthorizationContext(activeOrgId = orgBId)

        val grants = service.grantsOn(principal, resource, context)

        val orgGrants = grants.filter { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }
        assertTrue(orgGrants.isEmpty()) {
            "Expected no Org B membership grants on an exchange owned by Org A, got: $orgGrants"
        }
    }

    @Test
    fun `org A membership grants are applied when the exchange is owned by org A`()
    {
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)
        `when`(resourceContextRegistry.resolve(resource))
            .thenReturn(ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(orgAId)))
        `when`(organizationMembershipRepository.findActiveByUserAndOrg(callerUserId, orgAId))
            .thenReturn(membership(orgAId))

        val principal = PrincipalRef.user(callerUserId)
        val context = AuthorizationContext(activeOrgId = orgAId)

        val grants = service.grantsOn(principal, resource, context)

        val orgGrants = grants.filter { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }
        assertTrue(orgGrants.isNotEmpty()) {
            "Expected Org A membership grants for an exchange owned by Org A"
        }
    }

    @Test
    fun `active org B is ignored when the exchange is owned by org A`()
    {
        // Caller has activeOrgId = Org B, but exchange is owned by Org A.
        // The active org must not bleed into the ownership resolution.
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)
        `when`(resourceContextRegistry.resolve(resource))
            .thenReturn(ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(orgAId)))
        `when`(organizationMembershipRepository.findActiveByUserAndOrg(callerUserId, orgAId))
            .thenReturn(null)

        val principal = PrincipalRef.user(callerUserId)
        val context = AuthorizationContext(activeOrgId = orgBId)

        val grants = service.grantsOn(principal, resource, context)

        val orgGrants = grants.filter { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }
        assertTrue(orgGrants.isEmpty()) {
            "Active org B must not contribute grants on an exchange owned by Org A"
        }
    }

    @Test
    fun `personal exchange yields no org membership grants`()
    {
        val personalOwnerId = UUID.randomUUID()
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)
        `when`(resourceContextRegistry.resolve(resource))
            .thenReturn(ResourceAuthorizationContext(ownerContext = OwnerContext.Personal(personalOwnerId)))

        val principal = PrincipalRef.user(callerUserId)
        val context = AuthorizationContext(activeOrgId = orgBId)

        val grants = service.grantsOn(principal, resource, context)

        val orgGrants = grants.filter { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }
        assertTrue(orgGrants.isEmpty()) {
            "Personal exchange must not yield org membership grants"
        }
    }

    @Test
    fun `unresolvable exchange yields no org membership grants`()
    {
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)
        `when`(resourceContextRegistry.resolve(resource)).thenReturn(null)

        val principal = PrincipalRef.user(callerUserId)
        val context = AuthorizationContext(activeOrgId = orgBId)

        val grants = service.grantsOn(principal, resource, context)

        val orgGrants = grants.filter { it.sourceKind == Grant.SourceKind.ORG_MEMBERSHIP }
        assertTrue(orgGrants.isEmpty()) {
            "Unresolvable exchange must yield no org membership grants (fail closed)"
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private fun membership(orgId: UUID): OrganizationMembership =
        OrganizationMembership().apply {
            id = UUID.randomUUID()
            organizationId = orgId
            appUserId = callerUserId
            roles = mutableSetOf(OrganizationRoleName.ORG_MEMBER)
        }
}
