package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CreateSequenceRequest
import com.docuhyphen.app.api.model.dto.UpdateSequenceRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import com.docuhyphen.app.api.service.variable.SequenceDefinitionService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

@Path("/sequences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SequenceDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val sequenceService: SequenceDefinitionService,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SequenceDefinitionResource::class.java)
    }

    @GET
    fun listSequences(@QueryParam("isActive") isActive: Boolean?): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
            ?: return Response.ok(emptyArray<Any>()).build()

        return try
        {
            val items = sequenceService.listSequences(callerOrgId, isActive)
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list sequences", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list sequences")).build()
        }
    }

    @POST
    fun createSequence(request: CreateSequenceRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (request.name.isBlank())
            return Response.status(BAD_REQUEST).entity(ResponseError("name is required")).build()

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
            ?: return Response.status(BAD_REQUEST).entity(ResponseError("Organization context required")).build()

        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = userRoleService.isOrgAdminIn(actor.id, callerOrgId)

        return try
        {
            val dto = sequenceService.createSequence(callerOrgId, request, actor.id, isOrgAdmin, isAppAdmin)
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
            logger.error("Failed to create sequence", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create sequence")).build()
        }
    }

    @GET
    @Path("/{id}")
    fun getSequence(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val seqId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid sequence id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId?.let { userRoleService.isOrgAdminIn(actor.id, it) } ?: false

        return try
        {
            val dto = sequenceService.getSequence(seqId, callerOrgId, isOrgAdmin, isAppAdmin)
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
            logger.error("Failed to get sequence {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to get sequence")).build()
        }
    }

    @PUT
    @Path("/{id}")
    fun updateSequence(@PathParam("id") id: String, request: UpdateSequenceRequest): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val seqId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid sequence id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId?.let { userRoleService.isOrgAdminIn(actor.id, it) } ?: false

        return try
        {
            val dto = sequenceService.updateSequence(seqId, request, callerOrgId, isOrgAdmin, isAppAdmin)
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
            logger.error("Failed to update sequence {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update sequence")).build()
        }
    }

    @DELETE
    @Path("/{id}")
    fun deleteSequence(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val seqId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid sequence id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId?.let { userRoleService.isOrgAdminIn(actor.id, it) } ?: false

        return try
        {
            sequenceService.deleteSequence(seqId, callerOrgId, isOrgAdmin, isAppAdmin)
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
            logger.error("Failed to delete sequence {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete sequence")).build()
        }
    }

    @PATCH
    @Path("/{id}/reset")
    fun resetCounter(@PathParam("id") id: String): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val seqId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid sequence id")).build()
        }

        val callerOrgId = organizationMembershipService.primaryOrganizationId(actor.id)
        val isAppAdmin = userRoleService.isAppAdmin(actor.id)
        val isOrgAdmin = callerOrgId?.let { userRoleService.isOrgAdminIn(actor.id, it) } ?: false

        return try
        {
            val dto = sequenceService.resetCounter(seqId, callerOrgId, isOrgAdmin, isAppAdmin)
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
            logger.error("Failed to reset counter for sequence {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to reset counter")).build()
        }
    }
}
