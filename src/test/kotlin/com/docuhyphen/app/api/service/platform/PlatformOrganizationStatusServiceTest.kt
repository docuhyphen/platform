package com.docuhyphen.app.api.service.platform

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.PlatformOrganizationStatusUpdateRequest
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.subscription.SubscriptionPolicyService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class PlatformOrganizationStatusServiceTest
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
    private val auditService = mock<AuthAuditService>()
    private val subscriptionPolicyService = mock<SubscriptionPolicyService>()
    private val service = PlatformOrganizationStatusService(
        authTokenContext,
        userRoleService,
        organizationRepository,
        auditService,
        subscriptionPolicyService,
    )

    @Test
    fun `status update persists active and verification flags with required audit evidence`()
    {
        val organization = Organization().apply {
            id = UUID.randomUUID()
            name = "Acme"
            registrationNumber = "REG-Acme"
            isActive = false
            verificationComplete = false
        }
        whenever(userRoleService.isAppAdmin(actor.id)).thenReturn(true)
        whenever(organizationRepository.findById(organization.id)).thenReturn(organization)

        val result = service.update(
            organizationId = organization.id.toString(),
            request = PlatformOrganizationStatusUpdateRequest(
                active = true,
                verificationComplete = true,
                changeReason = "Completed account review",
            ),
            adminApprovalContext = AdminApprovalContext("request-id"),
        )

        verify(organizationRepository).update(organization)
        assertTrue(result.active)
        assertTrue(result.verificationComplete)
        val auditArguments = mockingDetails(auditService).invocations
            .last { it.method.name == "emitRequired" }
            .arguments
        assertEquals("PLATFORM_ORGANIZATION_STATUS_UPDATE", auditArguments[0])
        assertEquals("Completed account review", auditArguments[8])
        assertEquals("active=false;verificationComplete=false", auditArguments[9])
        assertEquals("active=true;verificationComplete=true", auditArguments[10])
        assertEquals("ORGANIZATION", auditArguments[11])
        assertEquals(organization.id.toString(), auditArguments[12])
    }
}
