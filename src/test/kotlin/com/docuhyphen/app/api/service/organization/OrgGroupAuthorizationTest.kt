package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.WorkflowInstance
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.repository.workflow.WorkflowWebhookEndpointRepository
import com.docuhyphen.app.api.service.application.WebhookDeliveryService
import com.docuhyphen.app.api.service.application.WebhookDeliveryResult
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry.Companion.toResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourceKind
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.workflow.WebhookWorkflowActionHandler
import com.docuhyphen.app.api.service.workflow.WorkflowSpecJson
import com.docuhyphen.app.api.service.workflow.WorkflowStepSpec
import com.docuhyphen.app.api.model.entity.WorkflowStepInstance
import com.docuhyphen.app.api.model.entity.WorkflowStepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Phase 9 completion tests: organization resource auth provider, resource type registry
 * mapping, and the WEBHOOK_DELIVER action handler dispatch.
 */
class OrgGroupAuthorizationTest
{
    // ── OrganizationAuthorizationContextProvider ──────────────────────────────

    @Test
    fun `provider returns organization owner context for existing org`()
    {
        val orgId = UUID.randomUUID()
        val org = Organization().also { it.id = orgId }
        val repo = mock<OrganizationRepository>()
        whenever(repo.findById(orgId)).thenReturn(org)

        val provider = OrganizationAuthorizationContextProvider()
        val field = OrganizationAuthorizationContextProvider::class.java.getDeclaredField("organizationRepository")
        field.isAccessible = true
        field.set(provider, repo)

        val ctx = provider.resolve(orgId)!!
        assertEquals(OwnerContext.Organization(orgId), ctx.ownerContext)
        assertEquals(false, ctx.isArchived)
        assertEquals(false, ctx.isSuspended)
    }

    @Test
    fun `provider returns null for unknown org`()
    {
        val repo = mock<OrganizationRepository>()
        whenever(repo.findById(any())).thenReturn(null)

        val provider = OrganizationAuthorizationContextProvider()
        val field = OrganizationAuthorizationContextProvider::class.java.getDeclaredField("organizationRepository")
        field.isAccessible = true
        field.set(provider, repo)

        assertNull(provider.resolve(UUID.randomUUID()))
    }

    @Test
    fun `provider supported kind is ORGANIZATION`()
    {
        val provider = OrganizationAuthorizationContextProvider()
        val field = OrganizationAuthorizationContextProvider::class.java.getDeclaredField("organizationRepository")
        field.isAccessible = true
        field.set(provider, mock<OrganizationRepository>())

        assertEquals(ResourceKind.ORGANIZATION, provider.supportedKind)
    }

    // ── ResourceType.ORGANIZATION registry mapping ────────────────────────────

    @Test
    fun `ResourceType ORGANIZATION maps to ResourceKind ORGANIZATION`()
    {
        assertEquals(ResourceKind.ORGANIZATION, ResourceType.ORGANIZATION.toResourceKind())
    }

    @Test
    fun `ResourceRef organization factory produces correct type`()
    {
        val id = UUID.randomUUID()
        val ref = ResourceRef.organization(id)
        assertEquals(ResourceType.ORGANIZATION, ref.type)
        assertEquals(id, ref.id)
    }

    // ── WebhookWorkflowActionHandler ──────────────────────────────────────────

    @Test
    fun `handler fails when webhookEndpointId is not set in spec`()
    {
        val deliveryService = mock<WebhookDeliveryService>()
        val handler = WebhookWorkflowActionHandler(deliveryService)

        val spec = WorkflowStepSpec(type = WorkflowStepType.ACTION, actionHandlerKey = WebhookWorkflowActionHandler.KEY)
        val step = makeStep(spec)
        val instance = WorkflowInstance()

        val result = handler.execute(instance, step)
        assertEquals(false, result.success)
        assertEquals("webhookEndpointId not set in step spec", result.reason)
    }

    @Test
    fun `handler fails when webhookEndpointId is not a valid UUID`()
    {
        val deliveryService = mock<WebhookDeliveryService>()
        val handler = WebhookWorkflowActionHandler(deliveryService)

        val spec = WorkflowStepSpec(
            type = WorkflowStepType.ACTION,
            actionHandlerKey = WebhookWorkflowActionHandler.KEY,
            webhookEndpointId = "not-a-uuid",
        )
        val step = makeStep(spec)

        val result = handler.execute(WorkflowInstance(), step)
        assertEquals(false, result.success)
    }

    @Test
    fun `handler delegates to delivery service and returns success`()
    {
        val endpointId = UUID.randomUUID()
        val deliveryService = mock<WebhookDeliveryService>()
        whenever(deliveryService.deliver(eq(endpointId), any(), any()))
            .thenReturn(WebhookDeliveryResult(success = true, reason = null))

        val handler = WebhookWorkflowActionHandler(deliveryService)
        val spec = WorkflowStepSpec(
            type = WorkflowStepType.ACTION,
            actionHandlerKey = WebhookWorkflowActionHandler.KEY,
            webhookEndpointId = endpointId.toString(),
            webhookEventType = "exchange.activated",
        )
        val result = handler.execute(WorkflowInstance(), makeStep(spec))
        assertEquals(true, result.success)
    }

    @Test
    fun `handler propagates delivery failure`()
    {
        val endpointId = UUID.randomUUID()
        val deliveryService = mock<WebhookDeliveryService>()
        whenever(deliveryService.deliver(eq(endpointId), any(), any()))
            .thenReturn(WebhookDeliveryResult(success = false, reason = "HTTP 503"))

        val handler = WebhookWorkflowActionHandler(deliveryService)
        val spec = WorkflowStepSpec(
            type = WorkflowStepType.ACTION,
            actionHandlerKey = WebhookWorkflowActionHandler.KEY,
            webhookEndpointId = endpointId.toString(),
        )
        val result = handler.execute(WorkflowInstance(), makeStep(spec))
        assertEquals(false, result.success)
        assertEquals("HTTP 503", result.reason)
    }

    @Test
    fun `WebhookDeliveryService returns failure when endpoint not found`()
    {
        val repo = mock<WorkflowWebhookEndpointRepository>()
        whenever(repo.findById(any())).thenReturn(null)

        val auditService = mock<AuthAuditService>()
        val service = WebhookDeliveryService(repo, auditService)

        val result = service.deliver(UUID.randomUUID(), "test.event", WorkflowInstance())
        assertEquals(false, result.success)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeStep(spec: WorkflowStepSpec): WorkflowStepInstance
    {
        val step = WorkflowStepInstance()
        step.specSnapshotJson = WorkflowSpecJson.encodeStep(spec)
        return step
    }
}
