package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.service.audit.AuditAnalyticsReconciliationService
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.audit.AuditLegalHoldNotFoundException
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AuditLegalHoldResourceTest
{
    @Test
    fun `organization route hides a hold outside its scope`()
    {
        val organizationId = UUID.randomUUID()
        val holdId = UUID.randomUUID()
        val principal = PrincipalRef.user(UUID.randomUUID())
        val legalHoldService = mock<AuditLegalHoldService>()
        val contextFactory = authorizedContextFactory(principal)
        val authorizationService = allowingAuthorizationService()
        whenever(legalHoldService.releaseHold(holdId, organizationId, principal.id))
            .thenThrow(AuditLegalHoldNotFoundException())
        val resource = AuditOrganizationLegalHoldResource(
            authorizationService,
            contextFactory,
            legalHoldService,
        )

        val response = resource.releaseOrganizationLegalHold(organizationId.toString(), holdId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(legalHoldService).releaseHold(holdId, organizationId, principal.id)
    }

    @Test
    fun `platform route hides an organization hold`()
    {
        val holdId = UUID.randomUUID()
        val principal = PrincipalRef.user(UUID.randomUUID())
        val legalHoldService = mock<AuditLegalHoldService>()
        val contextFactory = authorizedContextFactory(principal)
        val authorizationService = allowingAuthorizationService()
        whenever(legalHoldService.releaseHold(holdId, null, principal.id))
            .thenThrow(AuditLegalHoldNotFoundException())
        val resource = AuditGovernanceResource(
            authorizationService,
            contextFactory,
            mock<AuditRetentionPolicyService>(),
            legalHoldService,
            mock<AuditAnalyticsReconciliationService>(),
            mock<AuditEngagementService>(),
        )

        val response = resource.releasePlatformLegalHold(holdId.toString())

        assertEquals(Response.Status.NOT_FOUND.statusCode, response.status)
        verify(legalHoldService).releaseHold(holdId, null, principal.id)
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
