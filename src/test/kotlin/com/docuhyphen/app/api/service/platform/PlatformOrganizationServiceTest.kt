package com.docuhyphen.app.api.service.platform

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.PlatformOrganizationDtoMapper
import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementUpdateDto
import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.repository.subscription.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.repository.subscription.SubscriptionFeatureEntitlementRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.subscription.SubscriptionFeatureEntitlementAdminService
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import io.quarkus.security.UnauthorizedException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class PlatformOrganizationServiceTest
{
    private val actor = AppUser().apply {
        id = UUID.randomUUID()
        email = "platform-admin@example.com"
        isActive = true
    }
    private val authTokenContext = AuthTokenContext().apply {
        authToken = AuthToken().apply { appUser = actor }
    }
    private val userRoleService = mock<UserRoleService>()
    private val organizationRepository = mock<OrganizationRepository>()
    private val policyRepository = mock<OrganizationSubscriptionPolicyRepository>()
    private val entitlementRepository = mock<SubscriptionFeatureEntitlementRepository>()
    private val membershipService = mock<OrganizationMembershipService>()
    private val auditService = mock<AuthAuditService>()
    private val service = PlatformOrganizationService(
        authTokenContext = authTokenContext,
        userRoleService = userRoleService,
        organizationRepository = organizationRepository,
        organizationSubscriptionPolicyRepository = policyRepository,
        featureEntitlementAdminService = SubscriptionFeatureEntitlementAdminService(entitlementRepository),
        organizationMembershipService = membershipService,
        mapper = PlatformOrganizationDtoMapper(),
        authAuditService = auditService,
    )

    @Test
    fun `APP_ADMIN is required before organizations are queried`()
    {
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(false)

        assertThrows(UnauthorizedException::class.java) {
            service.list(null, null, null, null, null, 50, 0, null)
        }

        verify(organizationRepository, never()).findForPlatformAdministration(
            any(), any(), any(), any(), any(), any(), any(),
        )
    }

    @Test
    fun `list returns only the explicit platform account summary fields`()
    {
        val organization = organization("Acme")
        val policy = OrganizationSubscriptionPolicy().apply {
            this.organization = organization
            tierCode = "BUSINESS"
            maxUsers = 25
        }
        val entitlement = entitlement(organization.id, "WORKFLOWS", true)
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(
            organizationRepository.findForPlatformAdministration(
                "acme", true, "BUSINESS", "name", "asc", 20, 0,
            ),
        ).thenReturn(listOf(organization))
        whenever(
            organizationRepository.countForPlatformAdministration("acme", true, "BUSINESS"),
        ).thenReturn(1)
        whenever(policyRepository.findByOrganizationIds(listOf(organization.id))).thenReturn(listOf(policy))
        whenever(entitlementRepository.findByOrganizationIds(listOf(organization.id)))
            .thenReturn(listOf(entitlement))
        whenever(membershipService.activeProvisionedMemberCounts(listOf(organization.id)))
            .thenReturn(mapOf(organization.id to 7))

        val result = service.list(
            query = " Acme ",
            status = "active",
            tierCode = "business",
            sort = "name",
            direction = "asc",
            limit = 20,
            offset = 0,
            requestId = "request-id",
        )

        assertEquals(1, result.total)
        assertEquals(1, result.items.size)
        with(result.items.single())
        {
            assertEquals(organization.id.toString(), organizationId)
            assertEquals("Acme", name)
            assertEquals("REG-Acme", registrationNumber)
            assertTrue(active)
            assertFalse(verificationComplete)
            assertEquals("BUSINESS", tierCode)
            assertEquals(25, maxUsers)
            assertEquals(7, activeUsers)
            assertEquals("WORKFLOWS", featureEntitlements.single().featureCode)
        }
    }

    @Test
    fun `feature entitlement replacement normalizes codes and removes absent rows`()
    {
        val organization = organization("Acme")
        val removed = entitlement(organization.id, "LEGACY", true)
        val retained = entitlement(organization.id, "WORKFLOWS", false)
        val updated = entitlement(organization.id, "WORKFLOWS", true)
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(organizationRepository.findById(organization.id)).thenReturn(organization)
        whenever(entitlementRepository.findByOrganizationId(organization.id))
            .thenReturn(listOf(removed, retained))
            .thenReturn(listOf(updated))

        val result = service.replaceFeatureEntitlements(
            organizationId = organization.id.toString(),
            request = PlatformOrganizationFeatureEntitlementsUpdateRequest(
                entitlements = listOf(
                    PlatformOrganizationFeatureEntitlementUpdateDto(" workflows ", true),
                ),
                changeReason = "Enable workflow access",
            ),
            adminApprovalContext = AdminApprovalContext("request-id"),
        )

        verify(entitlementRepository).delete(removed)
        verify(entitlementRepository).update(retained)
        assertEquals(listOf("WORKFLOWS"), result.entitlements.map { it.featureCode })
        assertTrue(result.entitlements.single().enabled)
        val auditArguments = mockingDetails(auditService).invocations
            .last { it.method.name == "emitRequired" }
            .arguments
        assertEquals("PLATFORM_ORG_FEATURE_ENTITLEMENTS_UPDATE", auditArguments[0])
        assertEquals(actor.id, auditArguments[3])
        assertEquals("APP_ADMIN", auditArguments[4])
        assertEquals(null, auditArguments[6])
        assertEquals("ORGANIZATION", auditArguments[11])
        assertEquals(organization.id.toString(), auditArguments[12])
        @Suppress("UNCHECKED_CAST")
        val details = auditArguments[13] as Map<String, String>
        assertTrue(details.getValue("before_state").contains("WORKFLOWS=false"))
        assertTrue(details.getValue("after_state").contains("WORKFLOWS=true"))
    }

    @Test
    fun `a newly recorded decision names the organization as its owner`()
    {
        val organization = organization("Acme")
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(organizationRepository.findById(organization.id)).thenReturn(organization)
        whenever(entitlementRepository.findByOrganizationId(organization.id))
            .thenReturn(emptyList())
            .thenReturn(listOf(entitlement(organization.id, "INFORMATION_REQUESTS", true)))

        service.replaceFeatureEntitlements(
            organizationId = organization.id.toString(),
            request = PlatformOrganizationFeatureEntitlementsUpdateRequest(
                entitlements = listOf(
                    PlatformOrganizationFeatureEntitlementUpdateDto("INFORMATION_REQUESTS", true),
                ),
            ),
            adminApprovalContext = AdminApprovalContext("request-id"),
        )

        val saved = argumentCaptor<SubscriptionFeatureEntitlement>()
        verify(entitlementRepository).save(saved.capture())
        assertEquals(SubscriptionOwnerType.ORGANIZATION.name, saved.firstValue.ownerType)
        assertEquals(organization.id, saved.firstValue.organizationId)
        assertEquals(null, saved.firstValue.appUserId)
    }

    @Test
    fun `feature entitlement replacement rejects duplicate normalized codes`()
    {
        val organization = organization("Acme")
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(organizationRepository.findById(organization.id)).thenReturn(organization)

        assertThrows(IllegalArgumentException::class.java) {
            service.replaceFeatureEntitlements(
                organizationId = organization.id.toString(),
                request = PlatformOrganizationFeatureEntitlementsUpdateRequest(
                    entitlements = listOf(
                        PlatformOrganizationFeatureEntitlementUpdateDto("WORKFLOWS", true),
                        PlatformOrganizationFeatureEntitlementUpdateDto("workflows", false),
                    ),
                ),
                adminApprovalContext = AdminApprovalContext(),
            )
        }

        verify(entitlementRepository, never()).save(any())
        verify(entitlementRepository, never()).update(any())
    }

    private fun organization(name: String): Organization = Organization().apply {
        id = UUID.randomUUID()
        this.name = name
        registrationNumber = "REG-$name"
        isActive = true
        verificationComplete = false
        createdDate = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
    }

    private fun entitlement(
        organizationId: UUID,
        code: String,
        enabled: Boolean,
    ): SubscriptionFeatureEntitlement = SubscriptionFeatureEntitlement().apply {
        ownerType = SubscriptionOwnerType.ORGANIZATION.name
        this.organizationId = organizationId
        featureCode = code
        isEnabled = enabled
        updatedByAppUserId = actor.id
    }
}
