package com.docuhyphen.app.api.service.auth.authz

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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class ShareRoleCapabilityRegistryTest
{
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

    /**
     * The complete actor-capability matrix for every request-party Share role, resolved through
     * the same [DefaultAuthorizationService] path a real decision uses rather than by reading
     * [RoleCapabilities] directly. Each role's grant is exactly the set the request lifecycle
     * needs for its actor and nothing more; every capability withheld here stays default-deny for
     * that actor until a different Share role grants it.
     */
    private val expectedCapabilitiesByRole: Map<InformationRequestShareRoleKey, Set<Capability>> = mapOf(
        InformationRequestShareRoleKey.SUBJECT to setOf(
            Capability.INFORMATION_REQUEST_READ,
            Capability.INFORMATION_REQUEST_ATTEST,
        ),
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
    fun `each request-party Share role denies the concrete actions its capabilities do not cover`()
    {
        val principalId = UUID.randomUUID()
        val requestId = UUID.randomUUID()

        val subjectShare = activeShare(principalId, ResourceType.INFORMATION_REQUEST, requestId, "SUBJECT")
        val subjectService = buildService(subjectShare)
        val subjectResource = ResourceRef.informationRequest(requestId)
        val subjectPrincipal = PrincipalRef.user(principalId)
        val context = AuthorizationContext()

        assertTrue(
            subjectService.authorize(subjectPrincipal, Action.INFORMATION_REQUEST_VIEW, subjectResource, context)
                is Decision.Allow,
        )
        assertTrue(
            subjectService.authorize(subjectPrincipal, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, subjectResource, context)
                is Decision.Deny,
        )
        assertTrue(
            subjectService.authorize(subjectPrincipal, Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW, subjectResource, context)
                is Decision.Deny,
        )
        assertTrue(
            subjectService.authorize(subjectPrincipal, Action.INFORMATION_REQUEST_CANCEL, subjectResource, context)
                is Decision.Deny,
        )

        val attestorShare = activeShare(principalId, ResourceType.INFORMATION_REQUEST, requestId, "ATTESTOR")
        val attestorService = buildService(attestorShare)
        assertTrue(
            attestorService.authorize(subjectPrincipal, Action.INFORMATION_REQUEST_REQUIREMENT_ATTEST, subjectResource, context)
                is Decision.Allow,
        )
        assertTrue(
            attestorService.authorize(subjectPrincipal, Action.INFORMATION_REQUEST_REQUIREMENT_RESPOND, subjectResource, context)
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
