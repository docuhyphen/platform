package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationTrustRelationship
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.communication.EmailService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationTrustNotificationServiceTest
{
    @Test
    fun `administrative notifications reach both organizations and delivery failure is contained`()
    {
        val organizationService = mock<OrganizationService>()
        val membershipService = mock<OrganizationMembershipService>()
        val emailService = mock<EmailService>()
        val inAppService = mock<InAppNotificationService>()
        val configurationService = mock<ConfigurationService>()
        val organizationA = organization("Organization A")
        val organizationB = organization("Organization B")
        val adminA = appUser("admin-a@example.test")
        val adminB = appUser("admin-b@example.test")
        val relationship = OrganizationTrustRelationship().apply {
            organizationAId = organizationA.id
            organizationBId = organizationB.id
        }
        whenever(organizationService.getOrganizationById(organizationA.id)).thenReturn(organizationA)
        whenever(organizationService.getOrganizationById(organizationB.id)).thenReturn(organizationB)
        whenever(membershipService.activeAdmins(organizationA.id)).thenReturn(listOf(adminA))
        whenever(membershipService.activeAdmins(organizationB.id)).thenReturn(listOf(adminB))
        whenever(configurationService.emailSubjectTitle).thenReturn("DocuHyphen")
        whenever(configurationService.baseUrl).thenReturn("https://app.example.test")
        doThrow(IllegalStateException("mail unavailable"))
            .whenever(emailService).sendEmail(eq(adminA.email), any(), any(), any())
        val service = OrganizationTrustNotificationService(
            organizationService,
            membershipService,
            emailService,
            inAppService,
            configurationService,
        )

        assertDoesNotThrow {
            service.publish(AuditEventType.ORG_TRUST_ACCEPTED, relationship)
        }

        verify(inAppService, times(2)).publishAdministrative(any(), any(), any(), any(), any())
        verify(emailService, times(2)).sendEmail(any(), any(), any(), any())
    }

    private fun organization(name: String): Organization = Organization().apply {
        id = UUID.randomUUID()
        this.name = name
        registrationNumber = id.toString()
    }

    private fun appUser(email: String): AppUser = AppUser().apply {
        id = UUID.randomUUID()
        this.email = email
        isActive = true
    }
}
