package com.docuhyphen.app.api.resource.workflow.handler

import com.docuhyphen.app.api.model.WorkflowWebhookEndpointDtoMapper
import com.docuhyphen.app.api.model.application.RegisterWebhookRequest
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.model.RegisterWebhookRequestBody
import com.docuhyphen.app.api.service.application.WorkflowWebhookEndpointManagementService
import jakarta.ws.rs.core.Response
import java.util.*

class WorkflowWebhookEndpointRequestHandler(
    private val webhookManagementService: WorkflowWebhookEndpointManagementService,
)
{
    fun list(orgId: UUID, principal: PrincipalRef, context: AuthorizationContext): Response
    {
        val endpoints = webhookManagementService.listByOrganization(orgId, principal, context)
        return Response.ok(endpoints.map(WorkflowWebhookEndpointDtoMapper::toDto).toTypedArray()).build()
    }

    fun register(
        orgId: UUID,
        body: RegisterWebhookRequestBody,
        principal: PrincipalRef,
        context: AuthorizationContext,
    ): Response
    {
        val workflowDefinitionId =
            RequestGuards.requireNotBlank(body.workflowDefinitionId, "workflowDefinitionId is required")
        val targetUrl = RequestGuards.requireNotBlank(body.targetUrl, "targetUrl is required")
        val request = RegisterWebhookRequest(
            workflowDefinitionId = RequestGuards.uuid(workflowDefinitionId, "Invalid id: $workflowDefinitionId"),
            targetUrl = targetUrl,
            permittedEventTypes = body.permittedEventTypes ?: emptyList(),
            registeredApplicationId = body.registeredApplicationId?.let { RequestGuards.uuid(it, "Invalid id: $it") },
        )
        val (endpoint, secret) = webhookManagementService.register(orgId, request, principal, context)
        return Response.status(Response.Status.CREATED)
            .entity(WorkflowWebhookEndpointDtoMapper.toCreatedDto(endpoint, secret))
            .build()
    }

    fun get(endpointId: UUID, principal: PrincipalRef, context: AuthorizationContext): Response
    {
        val endpoint = webhookManagementService.getById(endpointId, principal, context)
        return Response.ok(WorkflowWebhookEndpointDtoMapper.toDto(endpoint)).build()
    }

    fun enable(endpointId: UUID, principal: PrincipalRef, context: AuthorizationContext): Response
    {
        webhookManagementService.enable(endpointId, principal, context)
        return Response.ok().build()
    }

    fun disable(endpointId: UUID, principal: PrincipalRef, context: AuthorizationContext): Response
    {
        webhookManagementService.disable(endpointId, principal, context)
        return Response.ok().build()
    }

    fun rotateSigningSecret(endpointId: UUID, principal: PrincipalRef, context: AuthorizationContext): Response
    {
        val secret = webhookManagementService.rotateSigningSecret(endpointId, principal, context)
        return Response.ok(WorkflowWebhookEndpointDtoMapper.toRotatedDto(secret)).build()
    }
}
