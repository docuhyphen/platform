package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.repository.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.service.auth.UserRoleService
import io.quarkus.security.UnauthorizedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationMemberCapacityServiceTest
{
    private val user = AppUser().apply {
        id = UUID.randomUUID()
        email = "admin@example.com"
        isActive = true
    }
    private val organizationId = UUID.randomUUID()
    private val organizationService = mock<OrganizationService>()
    private val policyRepository = mock<OrganizationSubscriptionPolicyRepository>()
    private val userRoleService = mock<UserRoleService>()
    private val membershipService = mock<OrganizationMembershipService>()

    @Test
    fun `global APP_ADMIN alone cannot use the tenant member capacity service`()
    {
        val context = tokenContext(activeOrganizationId = null)
        whenever(userRoleService.isAppAdmin(user.id)).thenReturn(true)
        val service = service(context)

        assertThrows(UnauthorizedException::class.java) {
            service.getForOrganization(organizationId)
        }

        verify(organizationService, never()).getOrganizationById(organizationId)
        verify(policyRepository, never()).findByOrganizationId(organizationId)
    }

    @Test
    fun `organization admin in the active organization receives aggregate capacity only`()
    {
        val context = tokenContext(activeOrganizationId = organizationId)
        val organization = Organization().apply {
            id = organizationId
            name = "Acme"
            registrationNumber = "REG-1"
        }
        val policy = OrganizationSubscriptionPolicy().apply {
            this.organization = organization
            tierCode = "BUSINESS"
            maxUsers = 20
        }
        whenever(userRoleService.isOrgAdminIn(user.id, organizationId)).thenReturn(true)
        whenever(organizationService.getOrganizationById(organizationId)).thenReturn(organization)
        whenever(policyRepository.findByOrganizationId(organizationId)).thenReturn(policy)
        whenever(membershipService.activeProvisionedMemberCount(organizationId)).thenReturn(8)

        val result = service(context).getForOrganization(organizationId)

        assertEquals(organizationId, result.organizationId)
        assertEquals("BUSINESS", result.tierCode)
        assertEquals(20, result.maxUsers)
        assertEquals(8, result.activeUsers)
    }

    private fun tokenContext(activeOrganizationId: UUID?): AuthTokenContext = AuthTokenContext().apply {
        authToken = AuthToken().apply { appUser = user }
        this.activeOrganizationId = activeOrganizationId
    }

    private fun service(context: AuthTokenContext): OrganizationMemberCapacityService =
        OrganizationMemberCapacityService(
            authTokenContext = context,
            organizationService = organizationService,
            organizationSubscriptionPolicyRepository = policyRepository,
            userRoleService = userRoleService,
            organizationMembershipService = membershipService,
        )
}
