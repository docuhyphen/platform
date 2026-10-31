package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.auth.authz.Decision
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class ShareRoleCapabilityRegistryTest
{
    @Test
    fun `Document and Principal Group Shares keep the capabilities of their Exchange role names`() {
        ExchangeShareRoleName.entries.forEach { role ->
            val expected = RoleCapabilities.forExchangeShareRole(role)
            assertEquals(
                expected,
                RoleCapabilities.forShareRole(ResourceType.DOCUMENT, role.name),
                "DOCUMENT ${role.name}"
            )
            assertEquals(
                expected,
                RoleCapabilities.forShareRole(ResourceType.PRINCIPAL_GROUP, role.name),
                "PRINCIPAL_GROUP ${role.name}"
            )
        }
        assertTrue(RoleCapabilities.forShareRole(ResourceType.DOCUMENT, "UNKNOWN_ROLE").isEmpty())
        assertTrue(
            RoleCapabilities.forShareRole(
                ResourceType.PRINCIPAL_GROUP,
                InformationRequestShareRoleKey.CONTRIBUTOR.name
            ).isEmpty()
        )
        ResourceType.entries
            .filterNot {
                it in setOf(
                    ResourceType.EXCHANGE,
                    ResourceType.DOCUMENT,
                    ResourceType.PRINCIPAL_GROUP,
                    ResourceType.INFORMATION_REQUEST
                )
            }
            .forEach { type -> assertTrue(RoleCapabilities.forShareRole(type, "OWNER").isEmpty(), type.name) }
    }

    @Test
    fun `a retained record owner Share reads the request and its evidence and does nothing else`() {
        assertEquals(
            setOf(
                Capability.INFORMATION_REQUEST_READ,
                Capability.INFORMATION_REQUEST_EVIDENCE_READ,
                Capability.INFORMATION_REQUEST_RETAINED_OWNER_READ,
            ),
            RoleCapabilities.forShareRole(
                ResourceType.INFORMATION_REQUEST,
                InformationRequestShareRoleKey.RECORD_OWNER.name
            ),
        )
        assertTrue(
            RoleCapabilities.forShareRole(
                ResourceType.EXCHANGE,
                InformationRequestShareRoleKey.RECORD_OWNER.name
            ).isEmpty()
        )
    }

    @Test
    fun `Document and Principal Group Shares resolve through the central authorization service like Exchange Shares`() {
        val references = listOf<(UUID) -> ResourceRef>(ResourceRef::exchange, ResourceRef::document, ResourceRef::group)
        ExchangeShareRoleName.entries.forEach { role ->
            references.forEach { reference ->
                val principalId = UUID.randomUUID()
                val resource = reference(UUID.randomUUID())
                val service = buildService(activeShare(principalId, resource.type, resource.id, role.name))

                val capabilities =
                    service.capabilities(PrincipalRef.user(principalId), resource, AuthorizationContext())

                assertEquals(RoleCapabilities.forExchangeShareRole(role), capabilities, "${resource.type} ${role.name}")
            }
        }
    }

    private val requestCapabilities = setOf(
        Capability.INFORMATION_REQUEST_CREATE,
        Capability.INFORMATION_REQUEST_READ,
        Capability.INFORMATION_REQUEST_WRITE,
        Capability.INFORMATION_REQUEST_ISSUE,
        Capability.INFORMATION_REQUEST_CANCEL,
        Capability.INFORMATION_REQUEST_ADMIN,
        Capability.INFORMATION_REQUEST_RESPOND,
        Capability.INFORMATION_REQUEST_ATTEST,
        Capability.INFORMATION_REQUEST_SUBMIT,
        Capability.INFORMATION_REQUEST_REVIEW,
        Capability.INFORMATION_REQUEST_EVIDENCE_READ,
        Capability.INFORMATION_REQUEST_EVIDENCE_WRITE,
        Capability.INFORMATION_REQUEST_EVIDENCE_ADMIN,
        Capability.INFORMATION_REQUEST_EXPORT,
    )

    @Test
    fun `runtime request reviewer Share role grants only reviewer request capabilities`()
    {
        val principalId = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        val share = activeShare(
            principalId = principalId,
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = requestId,
            roleKey = "REVIEWER",
        )
        val service = buildService(share)

        val capabilities = service.capabilities(
            PrincipalRef.user(principalId),
            ResourceRef.informationRequest(requestId),
            AuthorizationContext(),
        )

        assertEquals(
            setOf(
                Capability.INFORMATION_REQUEST_READ,
                Capability.INFORMATION_REQUEST_REVIEW,
                Capability.INFORMATION_REQUEST_EVIDENCE_READ,
            ),
            capabilities.intersect(requestCapabilities),
        )
        assertFalse(capabilities.contains(Capability.INFORMATION_REQUEST_RESPOND))
        assertFalse(capabilities.contains(Capability.INFORMATION_REQUEST_ATTEST))
        assertFalse(capabilities.contains(Capability.EXCHANGE_READ))
        assertFalse(capabilities.contains(Capability.DOCUMENT_READ))
    }

    private val expectedCapabilitiesByRole: Map<InformationRequestShareRoleKey, Set<Capability>> = mapOf(
        InformationRequestShareRoleKey.CONTRIBUTOR to setOf(
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_RESPOND,
            Capability.INFORMATION_REQUEST_ATTEST,
            Capability.INFORMATION_REQUEST_SUBMIT,
            Capability.INFORMATION_REQUEST_EVIDENCE_READ,
            Capability.INFORMATION_REQUEST_EVIDENCE_WRITE,
        ),
        InformationRequestShareRoleKey.PREPARER to setOf(
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_RESPOND,
            Capability.INFORMATION_REQUEST_ATTEST,
            Capability.INFORMATION_REQUEST_SUBMIT,
            Capability.INFORMATION_REQUEST_EVIDENCE_READ,
            Capability.INFORMATION_REQUEST_EVIDENCE_WRITE,
        ),
        InformationRequestShareRoleKey.ATTESTOR to setOf(
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_ATTEST,
            Capability.INFORMATION_REQUEST_EVIDENCE_READ,
        ),
        InformationRequestShareRoleKey.REVIEWER to setOf(
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_REVIEW,
            Capability.INFORMATION_REQUEST_EVIDENCE_READ,
        ),
        InformationRequestShareRoleKey.DECISION_MAKER to setOf(
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_WRITE,
            Capability.INFORMATION_REQUEST_ISSUE,
            Capability.INFORMATION_REQUEST_CANCEL,
            Capability.INFORMATION_REQUEST_ADMIN,
            Capability.INFORMATION_REQUEST_EXPORT,
            Capability.INFORMATION_REQUEST_EVIDENCE_READ,
            Capability.INFORMATION_REQUEST_EVIDENCE_ADMIN,
        ),
    )

    @Test
    fun `every request-party Share role resolves through the central authorization service to exactly its expected capabilities`()
    {
        expectedCapabilitiesByRole.forEach { (roleKey, expected) ->
            val principalId = UUID.randomUUID()
            val requestId = UUID.randomUUID()
            val share = activeShare(
                principalId = principalId,
                resourceType = ResourceType.INFORMATION_REQUEST,
                resourceId = requestId,
                roleKey = roleKey.name,
            )
            val service = buildService(share)

            val capabilities = service.capabilities(
                PrincipalRef.user(principalId),
                ResourceRef.informationRequest(requestId),
                AuthorizationContext(),
            )

            assertEquals(expected, capabilities.intersect(requestCapabilities)) {
                "$roleKey must resolve to exactly $expected through DefaultAuthorizationService"
            }
            assertFalse(capabilities.contains(Capability.EXCHANGE_READ)) { "$roleKey must not leak Exchange capabilities" }
            assertFalse(capabilities.contains(Capability.DOCUMENT_READ)) { "$roleKey must not leak Document capabilities" }
        }
    }

    @Test
    fun `no request-party Share role authors the very first request, since that grant belongs to the exchange owner alone`()
    {
        expectedCapabilitiesByRole.values.forEach { capabilities ->
            assertFalse(capabilities.contains(Capability.INFORMATION_REQUEST_CREATE))
        }
    }

    @Test
    fun `the request-party Share roles together cover every runtime request capability apart from creation`()
    {
        val union = expectedCapabilitiesByRole.values.reduce { acc, caps -> acc + caps }
        assertEquals(requestCapabilities - Capability.INFORMATION_REQUEST_CREATE, union)
    }

    @Test
    fun `a subject holds no request Share, so its role grants nothing and is not a valid Share role`() {
        assertFalse(RoleCapabilities.isShareRoleValid(ResourceType.INFORMATION_REQUEST, "SUBJECT"))
        assertEquals(emptySet<Capability>(), RoleCapabilities.forShareRole(ResourceType.INFORMATION_REQUEST, "SUBJECT"))
    }

    @Test
    fun `each request-party Share role denies the concrete actions its capabilities do not cover`()
    {
        val principalId = UUID.randomUUID()
        val requestId = UUID.randomUUID()

        val readingShare = activeShare(principalId, ResourceType.INFORMATION_REQUEST, requestId, "ATTESTOR")
        val readingService = buildService(readingShare)
        val requestResource = ResourceRef.informationRequest(requestId)
        val partyPrincipal = PrincipalRef.user(principalId)
        val context = AuthorizationContext()

        assertTrue(
            readingService.authorize(partyPrincipal, Action.INFORMATION_REQUEST_VIEW, requestResource, context)
                    is Decision.Allow,
        )
        assertTrue(
            readingService.authorize(
                partyPrincipal,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                requestResource,
                context
            )
                    is Decision.Deny,
        )
        assertTrue(
            readingService.authorize(
                partyPrincipal,
                Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW,
                requestResource,
                context
            )
                    is Decision.Deny,
        )
        assertTrue(
            readingService.authorize(partyPrincipal, Action.INFORMATION_REQUEST_CANCEL, requestResource, context)
                    is Decision.Deny,
        )

        val attestorShare = activeShare(principalId, ResourceType.INFORMATION_REQUEST, requestId, "ATTESTOR")
        val attestorService = buildService(attestorShare)
        assertTrue(
            attestorService.authorize(
                partyPrincipal,
                Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST,
                requestResource,
                context
            )
                    is Decision.Allow,
        )
        assertTrue(
            attestorService.authorize(
                partyPrincipal,
                Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND,
                requestResource,
                context
            )
                    is Decision.Deny,
        )
    }

    private fun activeShare(
        principalId: UUID,
        resourceType: ResourceType,
        resourceId: UUID,
        roleKey: String,
    ): Share = Share().apply {
        id = UUID.randomUUID()
        this.principalKind = PrincipalKind.USER
        this.principalId = principalId
        this.resourceType = resourceType
        this.resourceId = resourceId
        this.roleName = roleKey
        source = ShareSource.DIRECT
        status = ShareStatus.ACTIVE
    }

    private fun buildService(share: Share): DefaultAuthorizationService
    {
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findActiveForPrincipalOnResource(any(), any(), any(), any()))
            .thenReturn(listOf(share))
        val registry = mock<ResourceAuthorizationContextRegistry>()
        whenever(registry.resolution(any<ResourceRef>())).thenReturn(
            ResourceContextResolution.Resolved(
                ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(UUID.randomUUID())),
            ),
        )
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
            resourceContextRegistry = registry,
        )
    }
}
