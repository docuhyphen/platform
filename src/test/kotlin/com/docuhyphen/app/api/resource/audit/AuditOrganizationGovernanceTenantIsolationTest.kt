package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.service.audit.AuditAnalyticsReconciliationService
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.audit.AuditLegalHoldService
import com.docuhyphen.app.api.service.audit.AuditRetentionPolicyService
import com.docuhyphen.app.api.service.audit.export.AuditExportService
import com.docuhyphen.app.api.service.audit.export.AuditIntegrityService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class AuditOrganizationGovernanceTenantIsolationTest
{
    private val activeOrganizationId = UUID.randomUUID()
    private val foreignOrganizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val context = AuthorizationContext(activeOrgId = activeOrganizationId)

    private lateinit var authorizationService: AuthorizationService
    private lateinit var contextFactory: AuthorizationContextFactory

    @BeforeEach
    fun setup()
    {
        authorizationService = mock()
        contextFactory = mock()
        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(context)
        whenever(
            authorizationService.authorize(
                eq(principal),
                any(),
                eq(ResourceRef.organization(foreignOrganizationId)),
                eq(context),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "foreign organization"))
    }

    @Test
    fun `export resource denies a foreign organization path before listing exports`()
    {
        val service = mock<AuditExportService>()
        val resource = AuditOrganizationExportsResource(authorizationService, contextFactory, service)

        val response = resource.listOrganizationExports(foreignOrganizationId.toString())

        assertDenied(response, Action.ORG_READ_AUDIT)
        verify(service).recordDeniedAttempt(
            principal.id,
            foreignOrganizationId,
            foreignOrganizationId.toString(),
            Decision.REASON_NO_GRANT,
        )
    }

    @Test
    fun `retention resource denies a foreign organization path before listing policies`()
    {
        val service = mock<AuditRetentionPolicyService>()
        val resource = AuditOrganizationRetentionPolicyResource(authorizationService, contextFactory, service)

        val response = resource.listOrganizationRetentionPolicies(foreignOrganizationId.toString())

        assertDenied(response, Action.ORG_READ_AUDIT)
        verifyNoInteractions(service)
    }

    @Test
    fun `analytics resource denies a foreign organization path before reconciliation`()
    {
        val service = mock<AuditAnalyticsReconciliationService>()
        val resource = AuditOrganizationAnalyticsResource(authorizationService, contextFactory, service)

        val response = resource.getOrganizationAnalyticsReconciliation(foreignOrganizationId.toString())

        assertDenied(response, Action.AUDIT_INTEGRITY_VERIFY)
        verifyNoInteractions(service)
    }

    @Test
    fun `integrity resource denies a foreign organization path before checking evidence`()
    {
        val service = mock<AuditIntegrityService>()
        val resource = AuditOrganizationIntegrityResource(authorizationService, contextFactory, service)

        val response = resource.getOrganizationIntegrity(foreignOrganizationId.toString())

        assertDenied(response, Action.ORG_READ_AUDIT)
        verifyNoInteractions(service)
    }

    @Test
    fun `engagement resource denies a foreign organization path before listing engagements`()
    {
        val service = mock<AuditEngagementService>()
        val resource = AuditOrganizationEngagementResource(authorizationService, contextFactory, service)

        val response = resource.listOrganizationEngagements(foreignOrganizationId.toString())

        assertDenied(response, Action.ORG_READ_AUDIT)
        verifyNoInteractions(service)
    }

    @Test
    fun `legal hold resource denies a foreign organization path before listing holds`()
    {
        val service = mock<AuditLegalHoldService>()
        val resource = AuditOrganizationLegalHoldResource(authorizationService, contextFactory, service)

        val response = resource.listOrganizationLegalHolds(foreignOrganizationId.toString())

        assertDenied(response, Action.ORG_READ_AUDIT)
        verify(service).recordDeniedAttempt(principal.id, foreignOrganizationId, Decision.REASON_NO_GRANT)
    }

    private fun assertDenied(response: Response, action: Action)
    {
        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
        verify(authorizationService).authorize(
            principal,
            action,
            ResourceRef.organization(foreignOrganizationId),
            context,
        )
    }
}
