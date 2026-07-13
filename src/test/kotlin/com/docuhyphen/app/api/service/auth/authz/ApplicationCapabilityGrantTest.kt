package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.Application
import com.docuhyphen.app.api.model.entity.ApplicationRoleName
import com.docuhyphen.app.api.model.entity.ApplicationType
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.AppRoleAssignmentRepository
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.PrincipalGroupRepository
import com.docuhyphen.app.api.repository.ShareLinkRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.application.ApplicationService
import org.junit.jupiter.api.Assertions.assertFalse
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
 * APPLICATION principal with and without EXCHANGE_INITIATE grant.
 *
 * Scenario: Acme registers a CLM integration as an APPLICATION. The integration is
 * explicitly granted EXCHANGE_INITIATE for Acme. It must be able to initiate an Acme-owned
 * Exchange but must gain no other Exchange, document, or customer-content capability.
 *
 * A second application (no capability grant) must be denied EXCHANGE_INITIATE entirely.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationCapabilityGrantTest
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

    private val acmeOrgId: UUID = UUID.randomUUID()
    private val clmAppId: UUID = UUID.randomUUID()
    private val otherAppId: UUID = UUID.randomUUID()
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

        `when`(shareRepository.findActiveForPrincipalOnResource(
            PrincipalKind.APPLICATION, clmAppId, ResourceType.EXCHANGE, exchangeId,
        )).thenReturn(emptyList())
        `when`(shareRepository.findActiveForPrincipalOnResource(
            PrincipalKind.APPLICATION, otherAppId, ResourceType.EXCHANGE, exchangeId,
        )).thenReturn(emptyList())
        `when`(resourceContextRegistry.resolve(resource))
            .thenReturn(ResourceAuthorizationContext(ownerContext = OwnerContext.Organization(acmeOrgId)))
    }

    @Test
    fun `CLM application with EXCHANGE_INITIATE grant receives that capability`()
    {
        val clm = acmeClm(grantedCapabilitiesJson = """["EXCHANGE_INITIATE"]""")
        `when`(applicationService.findActive(clmAppId)).thenReturn(clm)

        val principal = PrincipalRef(PrincipalKind.APPLICATION, clmAppId)
        val context = AuthorizationContext(applicationId = clmAppId)
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)

        val caps = service.capabilities(principal, resource, context)

        assertTrue(caps.contains(Capability.EXCHANGE_INITIATE)) {
            "CLM with explicit EXCHANGE_INITIATE grant must have that capability"
        }
    }

    @Test
    fun `CLM application with EXCHANGE_INITIATE grant does not receive EXCHANGE_READ`()
    {
        val clm = acmeClm(grantedCapabilitiesJson = """["EXCHANGE_INITIATE"]""")
        `when`(applicationService.findActive(clmAppId)).thenReturn(clm)

        val principal = PrincipalRef(PrincipalKind.APPLICATION, clmAppId)
        val context = AuthorizationContext(applicationId = clmAppId)
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)

        val caps = service.capabilities(principal, resource, context)

        assertFalse(caps.contains(Capability.EXCHANGE_READ)) {
            "EXCHANGE_INITIATE must not imply EXCHANGE_READ"
        }
        assertFalse(caps.contains(Capability.EXCHANGE_ADMIN)) {
            "EXCHANGE_INITIATE must not imply EXCHANGE_ADMIN"
        }
        assertFalse(caps.contains(Capability.EXCHANGE_RESCIND)) {
            "EXCHANGE_INITIATE must not imply EXCHANGE_RESCIND"
        }
        assertFalse(caps.contains(Capability.DOCUMENT_READ)) {
            "EXCHANGE_INITIATE must not imply DOCUMENT_READ"
        }
    }

    @Test
    fun `application without capability grant has no capabilities on the exchange`()
    {
        val other = application(id = otherAppId, grantedCapabilitiesJson = "[]")
        `when`(applicationService.findActive(otherAppId)).thenReturn(other)

        val principal = PrincipalRef(PrincipalKind.APPLICATION, otherAppId)
        val context = AuthorizationContext(applicationId = otherAppId)
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)

        val caps = service.capabilities(principal, resource, context)

        assertTrue(caps.isEmpty()) {
            "An application with no granted capabilities must receive no capabilities from the role alone"
        }
    }

    @Test
    fun `EXCHANGE_INITIATE authorize returns Allow for CLM with explicit grant`()
    {
        val clm = acmeClm(grantedCapabilitiesJson = """["EXCHANGE_INITIATE"]""")
        `when`(applicationService.findActive(clmAppId)).thenReturn(clm)

        val principal = PrincipalRef(PrincipalKind.APPLICATION, clmAppId)
        val context = AuthorizationContext(applicationId = clmAppId)
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)

        val decision = service.authorize(principal, Action.EXCHANGE_INITIATE, resource, context)

        assertTrue(decision.isAllowed) {
            "CLM with EXCHANGE_INITIATE grant must be allowed to initiate"
        }
    }

    @Test
    fun `EXCHANGE_INITIATE authorize returns Deny for application without the grant`()
    {
        val other = application(id = otherAppId, grantedCapabilitiesJson = "[]")
        `when`(applicationService.findActive(otherAppId)).thenReturn(other)

        val principal = PrincipalRef(PrincipalKind.APPLICATION, otherAppId)
        val context = AuthorizationContext(applicationId = otherAppId)
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)

        val decision = service.authorize(principal, Action.EXCHANGE_INITIATE, resource, context)

        assertFalse(decision.isAllowed) {
            "Application without EXCHANGE_INITIATE grant must be denied"
        }
    }

    @Test
    fun `organization audit grant is limited to the application owning organization`()
    {
        val foreignOrganizationId = UUID.randomUUID()
        val application = acmeClm(grantedCapabilitiesJson = """["ORG_AUDIT_READ"]""")
        `when`(applicationService.findActive(clmAppId)).thenReturn(application)
        val principal = PrincipalRef.application(clmAppId)
        val context = AuthorizationContext(applicationId = clmAppId)

        val ownOrganizationDecision = service.authorize(
            principal,
            Action.ORG_READ_AUDIT,
            ResourceRef.organization(acmeOrgId),
            context,
        )
        val foreignOrganizationDecision = service.authorize(
            principal,
            Action.ORG_READ_AUDIT,
            ResourceRef.organization(foreignOrganizationId),
            context,
        )

        assertTrue(ownOrganizationDecision.isAllowed)
        assertFalse(foreignOrganizationDecision.isAllowed) {
            "An application capability grant must not cross its owning organization boundary"
        }
    }

    @Test
    fun `organization-owned application cannot use a platform audit grant`()
    {
        val application = acmeClm(grantedCapabilitiesJson = """["APP_AUDIT_READ"]""")
        `when`(applicationService.findActive(clmAppId)).thenReturn(application)
        val principal = PrincipalRef.application(clmAppId)
        val context = AuthorizationContext(applicationId = clmAppId)
        val platformAuditResource = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))

        val decision = service.authorize(
            principal,
            Action.APP_READ_AUDIT,
            platformAuditResource,
            context,
        )

        assertFalse(decision.isAllowed) {
            "An organization-owned application must not gain platform audit access"
        }
    }

    @Test
    fun `inactive application receives no capabilities`()
    {
        `when`(applicationService.findActive(clmAppId)).thenReturn(null)

        val principal = PrincipalRef(PrincipalKind.APPLICATION, clmAppId)
        val context = AuthorizationContext(applicationId = clmAppId)
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)

        val caps = service.capabilities(principal, resource, context)

        assertTrue(caps.isEmpty()) {
            "Inactive or unknown application must receive no capabilities"
        }
    }

    @Test
    fun `malformed grantedCapabilitiesJson silently yields no extra capabilities`()
    {
        val clm = acmeClm(grantedCapabilitiesJson = "{not-valid-json}")
        `when`(applicationService.findActive(clmAppId)).thenReturn(clm)

        val principal = PrincipalRef(PrincipalKind.APPLICATION, clmAppId)
        val context = AuthorizationContext(applicationId = clmAppId)
        val resource = ResourceRef(ResourceType.EXCHANGE, exchangeId)

        val caps = service.capabilities(principal, resource, context)

        assertTrue(caps.isEmpty()) {
            "Malformed granted_capabilities JSON must fail closed; no capabilities granted"
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private fun acmeClm(grantedCapabilitiesJson: String): Application =
        application(id = clmAppId, ownerOrgId = acmeOrgId, grantedCapabilitiesJson = grantedCapabilitiesJson)

    private fun application(
        id: UUID,
        ownerOrgId: UUID? = null,
        grantedCapabilitiesJson: String = "[]",
    ): Application = Application().apply {
        this.id = id
        name = "test-app"
        apiKey = "key"
        apiSecretHash = "hash"
        roleName = ApplicationRoleName.APPLICATION
        applicationType = ApplicationType.SERVICE
        isActive = true
        ownerOrganizationId = ownerOrgId
        this.grantedCapabilitiesJson = grantedCapabilitiesJson
    }
}
