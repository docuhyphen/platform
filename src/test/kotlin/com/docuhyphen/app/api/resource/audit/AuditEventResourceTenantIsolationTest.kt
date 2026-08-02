package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.resource.audit.AuditExchangeDocumentEventsResource
import com.docuhyphen.app.api.resource.audit.AuditExchangeEventsResource

import com.docuhyphen.app.api.service.audit.AuditSearchProjectionService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.util.UUID

class AuditEventResourceTenantIsolationTest
{
    private val actorId = UUID.randomUUID()
    private val activeOrganizationId = UUID.randomUUID()
    private val foreignOrganizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(actorId)
    private val context = AuthorizationContext(activeOrgId = activeOrganizationId)

    private lateinit var authorizationService: AuthorizationService
    private lateinit var contextFactory: AuthorizationContextFactory
    private lateinit var projectionService: AuditSearchProjectionService
    private lateinit var resourceContextRegistry: ResourceAuthorizationContextRegistry

    @BeforeEach
    fun setup()
    {
        authorizationService = mock()
        contextFactory = mock()
        projectionService = mock()
        resourceContextRegistry = mock()

        whenever(contextFactory.currentPrincipal()).thenReturn(principal)
        whenever(contextFactory.currentContext()).thenReturn(context)
        whenever(
            authorizationService.authorize(
                principal,
                Action.ORG_READ_AUDIT,
                ResourceRef.organization(foreignOrganizationId),
                context,
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "foreign organization"))
        whenever(
            authorizationService.capabilities(
                principal,
                ResourceRef.organization(foreignOrganizationId),
                context,
            ),
        ).thenReturn(emptySet())
    }

    @Test
    fun `organization event route authorizes the organization from the path`()
    {
        val resource = AuditOrganizationEventsResource(
            authorizationService,
            contextFactory,
            projectionService,
        )

        val response = resource.listOrganizationEvents(
            foreignOrganizationId.toString(),
            null,
            null,
            null,
            null,
            null,
            50,
        )

        assertDeniedWithoutEvidenceQuery(response)
        verifyOrganizationAuthorization()
    }

    @Test
    fun `Exchange event route authorizes the owning organization`()
    {
        val exchangeId = UUID.randomUUID()
        stubOrganizationOwner(ResourceRef.exchange(exchangeId))
        val resource = AuditExchangeEventsResource(
            authorizationService,
            contextFactory,
            resourceContextRegistry,
            projectionService,
        )

        val response = resource.listExchangeEvents(exchangeId.toString(), null, null, 50)

        assertDeniedWithoutEvidenceQuery(response)
        verifyOrganizationAuthorization()
    }

    @Test
    fun `Exchange document event route authorizes the Exchange owning organization`()
    {
        val exchangeId = UUID.randomUUID()
        stubOrganizationOwner(ResourceRef.exchange(exchangeId))
        val resource = AuditExchangeDocumentEventsResource(
            authorizationService,
            contextFactory,
            resourceContextRegistry,
            projectionService,
        )

        val response = resource.listExchangeDocumentEvents(
            exchangeId.toString(),
            UUID.randomUUID().toString(),
            null,
            null,
            50,
        )

        assertDeniedWithoutEvidenceQuery(response)
        verifyOrganizationAuthorization()
    }

    @Test
    fun `Workflow Definition event route authorizes the owning organization`()
    {
        val definitionId = UUID.randomUUID()
        stubOrganizationOwner(ResourceRef.workflowDefinition(definitionId))
        val resource = AuditWorkflowDefinitionEventsResource(
            authorizationService,
            contextFactory,
            resourceContextRegistry,
            projectionService,
        )

        val response = resource.listWorkflowDefinitionEvents(definitionId.toString(), null, null, 50)

        assertDeniedWithoutEvidenceQuery(response)
        verifyOrganizationAuthorization()
    }

    private fun stubOrganizationOwner(resource: ResourceRef)
    {
        whenever(resourceContextRegistry.resolve(resource)).thenReturn(
            ResourceAuthorizationContext(OwnerContext.Organization(foreignOrganizationId)),
        )
    }

    private fun assertDeniedWithoutEvidenceQuery(response: Response)
    {
        assertEquals(Response.Status.FORBIDDEN.statusCode, response.status)
        verify(projectionService).recordDeniedAttempt(
            org.mockito.kotlin.any(),
            eq(foreignOrganizationId),
            eq("ORGANIZATION"),
            eq(foreignOrganizationId.toString()),
            eq(Decision.REASON_NO_GRANT),
            anyOrNull(),
        )
        verifyNoMoreInteractions(projectionService)
    }

    private fun verifyOrganizationAuthorization()
    {
        verify(authorizationService).authorize(
            principal,
            Action.ORG_READ_AUDIT,
            ResourceRef.organization(foreignOrganizationId),
            context,
        )
    }
}
