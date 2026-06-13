package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.workflow.CloneWorkflowRequest
import com.docuhyphen.app.api.service.workflow.CreateWorkflowDefinitionRequest
import com.docuhyphen.app.api.service.workflow.PatchWorkflowStatusRequest
import com.docuhyphen.app.api.service.workflow.UpdateWorkflowDefinitionRequest
import com.docuhyphen.app.api.service.workflow.WorkflowDefinitionService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * REST endpoints for workflow definition management, trigger event registry, and
 * instance queries. All business logic is delegated to [WorkflowDefinitionService].
 *
 * Endpoints:
 *   GET    /workflows/definitions                   - list accessible definitions
 *   POST   /workflows/definitions                   - create definition
 *   GET    /workflows/definitions/{id}              - get full definition
 *   PUT    /workflows/definitions/{id}              - update definition
 *   PATCH  /workflows/definitions/{id}/status       - activate / deactivate
 *   DELETE /workflows/definitions/{id}              - soft-delete
 *   POST   /workflows/definitions/{id}/clone        - clone to caller's org
 *   GET    /workflows/triggers                      - list trigger event registry
 *   GET    /workflows/instances                     - paginated instances for org
 *   GET    /workflows/instances/{id}                - instance detail with step timeline
 */
@Path("/workflows")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class WorkflowDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val workflowDefinitionService: WorkflowDefinitionService,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WorkflowDefinitionResource::class.java)
    }

    // ── Definitions ───────────────────────────────────────────────────────────

    @GET
    @Path("/definitions")
    fun listDefinitions(
        @QueryParam("tag") tag: String?,
        @QueryParam("triggerEvent") triggerEvent: String?,
        @QueryParam("isTemplate") isTemplate: Boolean?,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        return try
        {
            val items = workflowDefinitionService.listDefinitions(callerOrgId, tag, triggerEvent, isTemplate)
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list workflow definitions", e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to list workflow definitions")).build()
        }
    }

    @POST
    @Path("/definitions")
    fun createDefinition(request: CreateWorkflowDefinitionRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
            ?: return Response.status(FORBIDDEN)
                .entity(ResponseError("Organization membership required to create workflow definitions")).build()

        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        if (!isAppAdmin && !userRoleService.isOrgAdminIn(actor.id, callerOrgId))
        {
            return Response.status(FORBIDDEN)
                .entity(ResponseError("Organization admin role required")).build()
        }

        if (request.name.isBlank())
        {
            return Response.status(BAD_REQUEST).entity(ResponseError("name is required")).build()
        }
        if (request.triggerEvent.isBlank())
        {
            return Response.status(BAD_REQUEST).entity(ResponseError("triggerEvent is required")).build()
        }

        return try
        {
            val dto = workflowDefinitionService.createDefinition(request, callerOrgId, actor.id, isAppAdmin)
            Response.status(CREATED).entity(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to create workflow definition", e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to create workflow definition")).build()
        }
    }

    @GET
    @Path("/definitions/{id}")
    fun getDefinition(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val definitionId = parseUuid(id)
            ?: return Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = workflowDefinitionService.getDefinition(definitionId, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to get workflow definition {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to get workflow definition")).build()
        }
    }

    @PUT
    @Path("/definitions/{id}")
    fun updateDefinition(
        @PathParam("id") id: String,
        request: UpdateWorkflowDefinitionRequest,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val definitionId = parseUuid(id)
            ?: return Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = workflowDefinitionService.updateDefinition(definitionId, request, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: IllegalStateException)
        {
            Response.status(CONFLICT).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to update workflow definition {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to update workflow definition")).build()
        }
    }

    @PATCH
    @Path("/definitions/{id}/status")
    fun patchStatus(
        @PathParam("id") id: String,
        request: PatchWorkflowStatusRequest,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val definitionId = parseUuid(id)
            ?: return Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = workflowDefinitionService.patchStatus(definitionId, request.isActive, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to patch status for workflow definition {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to patch workflow definition status")).build()
        }
    }

    @DELETE
    @Path("/definitions/{id}")
    fun deleteDefinition(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val definitionId = parseUuid(id)
            ?: return Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            workflowDefinitionService.deleteDefinition(definitionId, callerOrgId, isAppAdmin)
            Response.noContent().build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: IllegalStateException)
        {
            Response.status(CONFLICT).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to delete workflow definition {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to delete workflow definition")).build()
        }
    }

    @POST
    @Path("/definitions/{id}/clone")
    fun cloneDefinition(
        @PathParam("id") id: String,
        request: CloneWorkflowRequest,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val definitionId = parseUuid(id)
            ?: return Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
            ?: return Response.status(FORBIDDEN)
                .entity(ResponseError("Organization membership required to clone workflow definitions")).build()

        return try
        {
            val dto = workflowDefinitionService.cloneDefinition(definitionId, callerOrgId, actor.id, request.newName)
            Response.status(CREATED).entity(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to clone workflow definition {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to clone workflow definition")).build()
        }
    }

    // ── Triggers ──────────────────────────────────────────────────────────────

    @GET
    @Path("/triggers")
    fun listTriggers(): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        return try
        {
            val triggers = workflowDefinitionService.listTriggers()
            Response.ok(triggers.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list workflow triggers", e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to list workflow triggers")).build()
        }
    }

    // ── Instances ─────────────────────────────────────────────────────────────

    @GET
    @Path("/instances")
    fun listInstances(
        @QueryParam("status") status: String?,
        @QueryParam("subjectResourceType") subjectResourceType: String?,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("pageSize") @DefaultValue("20") pageSize: Int,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
            ?: return Response.status(FORBIDDEN)
                .entity(ResponseError("Organization membership required")).build()

        return try
        {
            val items = workflowDefinitionService.listInstances(
                callerOrgId,
                status,
                subjectResourceType,
                page.coerceAtLeast(0),
                pageSize.coerceIn(1, 100),
            )
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list workflow instances", e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to list workflow instances")).build()
        }
    }

    @GET
    @Path("/instances/{id}")
    fun getInstanceDetail(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val instanceId = parseUuid(id)
            ?: return Response.status(BAD_REQUEST).entity(ResponseError("Invalid instance id")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = workflowDefinitionService.getInstanceDetail(instanceId, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to get workflow instance {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to get workflow instance")).build()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}

