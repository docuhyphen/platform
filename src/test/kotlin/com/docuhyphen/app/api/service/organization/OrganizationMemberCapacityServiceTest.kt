package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.subscription.EffectiveSubscription
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.PlanCatalog
import com.docuhyphen.app.api.service.subscription.SubscriptionAccessService
import com.docuhyphen.app.api.service.subscription.SubscriptionOwnerType
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import com.docuhyphen.app.api.service.subscription.SubscriptionUsage
import io.quarkus.security.UnauthorizedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
    private val subscriptionAccessService = mock<SubscriptionAccessService>()
    private val userRoleService = mock<UserRoleService>()

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
        verify(subscriptionAccessService, never()).resolve(org.mockito.kotlin.any())
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
        val subscription = EffectiveSubscription(
            ownerType = SubscriptionOwnerType.ORGANIZATION,
            ownerId = organizationId,
            planCode = PlanCode.BUSINESS,
            status = SubscriptionStatus.ACTIVE,
            features = emptySet(),
            limits = PlanCatalog.definitionOf(PlanCode.BUSINESS).limits,
            billingFrequency = null,
            currentPeriodStart = null,
            currentPeriodEnd = null,
            gracePeriodEnd = null,
            purchasedSeats = 20,
            upgradePlanCode = null,
        )
        whenever(userRoleService.isOrgAdminIn(user.id, organizationId)).thenReturn(true)
        whenever(organizationService.getOrganizationById(organizationId)).thenReturn(organization)
        whenever(subscriptionAccessService.resolve(org.mockito.kotlin.any())).thenReturn(subscription)
        whenever(subscriptionAccessService.measureUsage(eq(subscription), any())).thenReturn(
            SubscriptionUsage(activeSeats = 8),
        )

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
            subscriptionAccessService = subscriptionAccessService,
            userRoleService = userRoleService,
        )
}
