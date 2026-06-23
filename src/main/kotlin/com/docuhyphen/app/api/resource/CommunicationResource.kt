package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CloneCommunicationRequest
import com.docuhyphen.app.api.model.dto.CreateCommunicationRequest
import com.docuhyphen.app.api.model.dto.PatchCommunicationPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchCommunicationStatusRequest
import com.docuhyphen.app.api.model.dto.PreviewCommunicationRequest
import com.docuhyphen.app.api.model.dto.UpdateCommunicationRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.communication.CommunicationService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
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
import jakarta.ws.rs.core.Response.Status.CREATED
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * REST endpoints for communication management.
 *
 *   GET    /communications                     - list accessible communications
 *   POST   /communications                     - create communication
 *   GET    /communications/{id}                - get full communication
 *   PUT    /communications/{id}                - update communication
 *   PATCH  /communications/{id}/status         - activate / deactivate
 *   PATCH  /communications/{id}/published      - publish / unpublish (ORG/PLATFORM scope)
 *   DELETE /communications/{id}                - soft-delete
 *   POST   /communications/{id}/clone          - clone to PERSONAL scope
 *   POST   /communications/{id}/preview        - render communication with sample variables
 */
@Path("/communications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CommunicationResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val communicationService: CommunicationService,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(CommunicationResource::class.java)
    }

    @GET
    fun listTemplates(
        @QueryParam("scope") scope: String?,
        @QueryParam("tag") tag: String?,
        @QueryParam("isTemplate") isTemplate: Boolean?,
    ): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId != null && userRoleService.isOrgAdminIn(actor.id, callerOrgId)

        return try
        {
            val items = communicationService.listTemplates(actor.id, callerOrgId, isOrgAdmin, isAppAdmin, scope, tag, isTemplate)
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list communications", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list communications")).build()
        }
    }

    @POST
    fun createTemplate(request: CreateCommunicationRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (request.name.isBlank())
        {
            return Response.status(BAD_REQUEST).entity(ResponseError("name is required")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId != null && userRoleService.isOrgAdminIn(actor.id, callerOrgId)

        return try
        {
            val dto = communicationService.createTemplate(request, actor.id, callerOrgId, isOrgAdmin, isAppAdmin)
            Response.status(CREATED).entity(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to create communication", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create communication")).build()
        }
    }

    @GET
    @Path("/{id}")
    fun getTemplate(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val communicationId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid communication id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = communicationService.getTemplate(communicationId, actor.id, callerOrgId, isAppAdmin)
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
            logger.error("Failed to get communication {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to get communication")).build()
        }
    }

    @PUT
    @Path("/{id}")
    fun updateTemplate(@PathParam("id") id: String, request: UpdateCommunicationRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val communicationId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid communication id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = communicationService.updateTemplate(communicationId, request, actor.id, callerOrgId, isAppAdmin)
            Response.ok(dto).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to update communication {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update communication")).build()
        }
    }

    @PATCH
    @Path("/{id}/status")
    fun patchTemplateStatus(@PathParam("id") id: String, request: PatchCommunicationStatusRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val communicationId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid communication id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = communicationService.patchStatus(communicationId, request, actor.id, callerOrgId, isAppAdmin)
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
            logger.error("Failed to patch communication status {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to patch communication status")).build()
        }
    }

    @PATCH
    @Path("/{id}/published")
    fun patchTemplatePublished(@PathParam("id") id: String, request: PatchCommunicationPublishedRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val communicationId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid communication id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId != null && userRoleService.isOrgAdminIn(actor.id, callerOrgId)

        if (!isOrgAdmin && !isAppAdmin)
        {
            return Response.status(FORBIDDEN).entity(ResponseError("Org admin or app admin role required")).build()
        }

        return try
        {
            val dto = communicationService.patchPublished(communicationId, request, actor.id, callerOrgId, isAppAdmin)
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
            logger.error("Failed to patch communication published {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to patch communication published")).build()
        }
    }

    @DELETE
    @Path("/{id}")
    fun deleteTemplate(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val communicationId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid communication id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            communicationService.deleteTemplate(communicationId, actor.id, callerOrgId, isAppAdmin)
            Response.noContent().build()
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
            logger.error("Failed to delete communication {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete communication")).build()
        }
    }

    @POST
    @Path("/{id}/preview")
    fun previewTemplate(@PathParam("id") id: String, request: PreviewCommunicationRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val communicationId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid communication id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val rendered = communicationService.preview(communicationId, request.sampleVariables, actor.id, callerOrgId, isAppAdmin)
            Response.ok(rendered).build()
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
            logger.error("Failed to preview communication {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to preview communication")).build()
        }
    }

    @POST
    @Path("/{id}/clone")
    fun cloneTemplate(@PathParam("id") id: String, request: CloneCommunicationRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val communicationId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid communication id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)

        return try
        {
            val dto = communicationService.cloneTemplate(communicationId, request, actor.id, callerOrgId, isAppAdmin)
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
            logger.error("Failed to clone communication {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to clone communication")).build()
        }
    }
}
