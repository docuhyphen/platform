package com.docuhyphen.app.api.resource.workflow

import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.application.RegisterWebhookRequest
import com.docuhyphen.app.api.service.application.WorkflowWebhookEndpointManagementService
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.NO_CONTENT
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("/organizations/{orgId}/workflow-webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class WorkflowWebhookEndpointResource @Inject constructor(
    private val webhookManagementService: WorkflowWebhookEndpointManagementService,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WorkflowWebhookEndpointResource::class.java)
    }

    @GET
    fun listWebhooks(@PathParam("orgId") orgId: String): Response = guarded {
        val orgUuid = parseUuid(orgId)
        val endpoints = webhookManagementService.listByOrganization(orgUuid, principal(), context())
        val dtos = endpoints.map { ep ->
            WebhookEndpointDto(
                id = ep.id.toString(),
                ownerOrganizationId = ep.ownerOrganizationId.toString(),
                workflowDefinitionId = ep.workflowDefinitionId.toString(),
                registeredApplicationId = ep.registeredApplicationId?.toString(),
                targetUrl = ep.targetUrl,
                isEnabled = ep.isEnabled,
                signingSecretVersion = ep.signingSecretVersion,
                permittedEventTypes = ep.permittedEventTypes,
                createdDate = ep.createdDate.toInstant().toString(),
                updatedDate = ep.updatedDate.toInstant().toString(),
            )
        }
        Response.ok(dtos).build()
    }

    @POST
    fun registerWebhook(
        @PathParam("orgId") orgId: String,
        body: RegisterWebhookRequestBody
    ): Response = guarded {
        val orgUuid = parseUuid(orgId)
        require(!body.workflowDefinitionId.isNullOrBlank()) { "workflowDefinitionId is required" }
        require(!body.targetUrl.isNullOrBlank()) { "targetUrl is required" }
        val request = RegisterWebhookRequest(
            workflowDefinitionId = parseUuid(body.workflowDefinitionId),
            targetUrl = body.targetUrl,
            permittedEventTypes = body.permittedEventTypes ?: emptyList(),
            registeredApplicationId = body.registeredApplicationId?.let { parseUuid(it) },
        )
        val (endpoint, secret) = webhookManagementService.register(orgUuid, request, principal(), context())
        Response.status(CREATED).entity(
            WebhookEndpointCreatedDto(
                id = endpoint.id.toString(),
                targetUrl = endpoint.targetUrl,
                rawSigningSecret = secret.rawSecret,
                signingSecretVersion = secret.version,
            )
        ).build()
    }

    @GET
    @Path("/{id}")
    fun getWebhook(
        @PathParam("orgId") orgId: String,
        @PathParam("id") id: String
    ): Response = guarded {
        val endpointId = parseUuid(id)
        val ep = webhookManagementService.getById(endpointId, principal(), context())
        Response.ok(
            WebhookEndpointDto(
                id = ep.id.toString(),
                ownerOrganizationId = ep.ownerOrganizationId.toString(),
                workflowDefinitionId = ep.workflowDefinitionId.toString(),
                registeredApplicationId = ep.registeredApplicationId?.toString(),
                targetUrl = ep.targetUrl,
                isEnabled = ep.isEnabled,
                signingSecretVersion = ep.signingSecretVersion,
                permittedEventTypes = ep.permittedEventTypes,
                createdDate = ep.createdDate.toInstant().toString(),
                updatedDate = ep.updatedDate.toInstant().toString(),
            )
        ).build()
    }

    @POST
    @Path("/{id}/enable")
    fun enableWebhook(
        @PathParam("orgId") orgId: String,
        @PathParam("id") id: String
    ): Response = guarded {
        webhookManagementService.enable(parseUuid(id), principal(), context())
        Response.ok().build()
    }

    @POST
    @Path("/{id}/disable")
    fun disableWebhook(
        @PathParam("orgId") orgId: String,
        @PathParam("id") id: String
    ): Response = guarded {
        webhookManagementService.disable(parseUuid(id), principal(), context())
        Response.ok().build()
    }

    @POST
    @Path("/{id}/signing-secret/rotate")
    fun rotateSigningSecret(
        @PathParam("orgId") orgId: String,
        @PathParam("id") id: String
    ): Response = guarded {
        val result = webhookManagementService.rotateSigningSecret(parseUuid(id), principal(), context())
        Response.ok(WebhookSigningSecretRotatedDto(result.rawSecret, result.version)).build()
    }

    // -------------------------------------------------------------------------

    private fun principal() = authorizationContextFactory.currentPrincipal()
        ?: throw SecurityException("Authentication required")

    private fun context() = authorizationContextFactory.currentContext()

    private fun parseUuid(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrElse { throw IllegalArgumentException("Invalid id: $value") }

    private fun guarded(block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: Exception)
        {
            logger.error("Error in webhook endpoint management", e)
            when (e)
            {
                is SecurityException ->
                    Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
                is NoSuchElementException ->
                    Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
                else ->
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred")).build()
            }
        }
    }
}

@Serializable
data class RegisterWebhookRequestBody(
    val workflowDefinitionId: String?,
    val targetUrl: String?,
    val permittedEventTypes: List<String>?,
    val registeredApplicationId: String?,
)

@Serializable
data class WebhookEndpointDto(
    val id: String,
    val ownerOrganizationId: String,
    val workflowDefinitionId: String,
    val registeredApplicationId: String?,
    val targetUrl: String,
    val isEnabled: Boolean,
    val signingSecretVersion: Int,
    val permittedEventTypes: String,
    val createdDate: String,
    val updatedDate: String,
)

@Serializable
data class WebhookEndpointCreatedDto(
    val id: String,
    val targetUrl: String,
    val rawSigningSecret: String,
    val signingSecretVersion: Int,
)

@Serializable
data class WebhookSigningSecretRotatedDto(val rawSecret: String, val version: Int)
