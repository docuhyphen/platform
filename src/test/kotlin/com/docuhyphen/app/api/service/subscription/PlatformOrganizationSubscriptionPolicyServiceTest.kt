package com.docuhyphen.app.api.service.subscription

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationSubscriptionPolicy
import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyRequest
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.organization.OrganizationService
import com.docuhyphen.app.api.service.subscription.PlanCode
import com.docuhyphen.app.api.service.subscription.SubscriptionLifecycleValidator
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import com.docuhyphen.app.api.service.subscription.SubscriptionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class PlatformOrganizationSubscriptionPolicyServiceTest
{
    @Test
    fun `updating an organization subscription locks its explicit policy row`()
    {
        val organizationId = UUID.randomUUID()
        val actor = AppUser().apply { id = UUID.randomUUID() }
        val organization = Organization().apply {
            id = organizationId
            name = "Locking test organization"
        }
        val policy = OrganizationSubscriptionPolicy().apply {
            this.organization = organization
            tierCode = PlanCode.BUSINESS.name
            subscriptionStatus = SubscriptionStatus.ACTIVE.name
            changeReason = "Existing policy"
        }
        val authTokenContext = AuthTokenContext().apply {
            authToken = AuthToken().apply { appUser = actor }
        }
        val organizationService = mock<OrganizationService>()
        val subscriptionPolicyService = mock<SubscriptionPolicyService>()
        val userRoleService = mock<UserRoleService>()
        val membershipService = mock<OrganizationMembershipService>()
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(organizationService.getOrganizationById(organizationId)).thenReturn(organization)
        whenever(subscriptionPolicyService.ensureOrganizationPolicy(organizationId)).thenReturn(policy)
        whenever(subscriptionPolicyService.findOrganizationPolicyForUpdate(organizationId)).thenReturn(policy)
        whenever(membershipService.membersOf(organizationId)).thenReturn(emptyList())
        val service = PlatformOrganizationSubscriptionPolicyService(
            authTokenContext,
            organizationService,
            subscriptionPolicyService,
            mock<AuthAuditService>(),
            userRoleService,
            membershipService,
            SubscriptionLifecycleValidator(),
        )

        val result = service.upsertPolicy(
            organizationId.toString(),
            PlatformOrganizationSubscriptionPolicyRequest(
                tierCode = PlanCode.BUSINESS.name,
                maxUsers = 20,
                changeReason = "Purchased additional seats",
            ),
            AdminApprovalContext(requestId = "request-123"),
        )

        assertEquals(20L, result.maxUsers)
        verify(subscriptionPolicyService).ensureOrganizationPolicy(organizationId)
        verify(subscriptionPolicyService).findOrganizationPolicyForUpdate(organizationId)
        verify(subscriptionPolicyService, never()).findOrganizationPolicy(organizationId)
        verify(subscriptionPolicyService).updateOrganizationPolicy(policy)
    }
}
