package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.service.audit.AuditAnalyticsReconciliationService
import com.docuhyphen.app.api.service.audit.AuditEngagementNotFoundException
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.audit.AuditLegalHoldService
import com.docuhyphen.app.api.service.audit.AuditRetentionPolicyService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AuditEngagementResourceTest
{
    @Test
    fun `organization route hides an engagement outside its scope on approve`()
    {
        val organizationId = UUID.randomUUID()
        val engagementId = UUID.randomUUID()
        val principal = PrincipalRef.user(UUID.randomUUID())
        val engagementService = mock<AuditEngagementService>()
        val contextFactory = authorizedContextFactory(principal)
        val authorizationService = allowingAuthorizationService()
        whenever(engagementService.approveEngagement(engagementId, organizationId, principal.id))
            .thenThrow(AuditEngagementNotFoundException())
        val resource = AuditOrganizationEngagementResource(
            authorizationService,
            contextFactory,
            engagementService,
        )

        val response = resource.approveOrganizationEngagement(organizationId.toString(), engagementId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(engagementService).approveEngagement(engagementId, organizationId, principal.id)
    }

    @Test
    fun `organization route hides an engagement outside its scope on revoke`()
    {
        val organizationId = UUID.randomUUID()
        val engagementId = UUID.randomUUID()
        val principal = PrincipalRef.user(UUID.randomUUID())
        val engagementService = mock<AuditEngagementService>()
        val contextFactory = authorizedContextFactory(principal)
        val authorizationService = allowingAuthorizationService()
        whenever(engagementService.revokeEngagement(engagementId, organizationId, principal.id))
            .thenThrow(AuditEngagementNotFoundException())
        val resource = AuditOrganizationEngagementResource(
            authorizationService,
            contextFactory,
            engagementService,
        )

        val response = resource.revokeOrganizationEngagement(organizationId.toString(), engagementId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(engagementService).revokeEngagement(engagementId, organizationId, principal.id)
    }

    @Test
    fun `platform route hides an organization-scoped engagement`()
    {
        val engagementId = UUID.randomUUID()
        val principal = PrincipalRef.user(UUID.randomUUID())
        val engagementService = mock<AuditEngagementService>()
        val contextFactory = authorizedContextFactory(principal)
        val authorizationService = allowingAuthorizationService()
        whenever(engagementService.approveEngagement(engagementId, null, principal.id))
            .thenThrow(AuditEngagementNotFoundException())
        val resource = AuditGovernanceResource(
            authorizationService,
            contextFactory,
            mock<AuditRetentionPolicyService>(),
            mock<AuditLegalHoldService>(),
            mock<AuditAnalyticsReconciliationService>(),
            engagementService,
        )

        val response = resource.approvePlatformEngagement(engagementId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(engagementService).approveEngagement(engagementId, null, principal.id)
    }

    @Test
    fun `organization route denies an unauthorized request without calling the service`()
    {
        val organizationId = UUID.randomUUID()
        val principal = PrincipalRef.user(UUID.randomUUID())
        val engagementService = mock<AuditEngagementService>()
        val contextFactory = authorizedContextFactory(principal)
        val authorizationService = mock<AuthorizationService>()
        whenever(authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "no capability"))
        val resource = AuditOrganizationEngagementResource(
            authorizationService,
            contextFactory,
            engagementService,
        )

        val response = resource.listOrganizationEngagements(organizationId.toString())

        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
        verify(engagementService, never()).listForOrganization(any())
    }

    private fun authorizedContextFactory(principal: PrincipalRef): AuthorizationContextFactory =
        mock<AuthorizationContextFactory>().also { factory ->
            whenever(factory.currentPrincipal()).thenReturn(principal)
            whenever(factory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        }

    private fun allowingAuthorizationService(): AuthorizationService =
        mock<AuthorizationService>().also { service ->
            whenever(service.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        }
}
