package com.docuhyphen.app.api.resource.blueprint

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CloneBlueprintRequest
import com.docuhyphen.app.api.model.dto.CreateBlueprintRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintPublishedRequest
import com.docuhyphen.app.api.model.dto.PatchBlueprintStatusRequest
import com.docuhyphen.app.api.model.dto.UpdateBlueprintRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
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
 * REST endpoints for blueprint definition management.
 *
 *   GET    /blueprints                     - list accessible blueprints
 *   POST   /blueprints                     - create blueprint
 *   GET    /blueprints/{id}                - get full blueprint
 *   PUT    /blueprints/{id}                - update blueprint
 *   PATCH  /blueprints/{id}/status         - activate / deactivate
 *   PATCH  /blueprints/{id}/published      - publish / unpublish (ORG/APP scope)
 *   DELETE /blueprints/{id}                - soft-delete
 *   POST   /blueprints/{id}/clone          - clone to PERSONAL scope
 */
@Path("/blueprints")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class BlueprintDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val blueprintService: BlueprintDefinitionService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(BlueprintDefinitionResource::class.java)
    }

    @GET
    fun listBlueprints(
        @QueryParam("scope") scope: String?,
        @QueryParam("tag") tag: String?,
        @QueryParam("isTemplate") isTemplate: Boolean?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        return try
        {
            val items = blueprintService.listBlueprints(scope, tag, isTemplate)
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list blueprints", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list blueprints")).build()
        }
    }

    @POST
    fun createBlueprint(
        request: CreateBlueprintRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (request.name.isBlank())
        {
            return Response.status(BAD_REQUEST).entity(ResponseError("name is required")).build()
        }

        return try
        {
            val dto = blueprintService.createBlueprint(request, AdminApprovalContext(requestId = requestId))
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
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            logger.error("Failed to create blueprint", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create blueprint")).build()
        }
    }

    @GET
    @Path("/{id}")
    fun getBlueprint(@PathParam("id") id: String): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val bpId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid blueprint id")).build()
        }

        return try
        {
            val dto = blueprintService.getBlueprint(bpId)
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
            logger.error("Failed to get blueprint {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to get blueprint")).build()
        }
    }

    @PUT
    @Path("/{id}")
    fun updateBlueprint(
        @PathParam("id") id: String,
        request: UpdateBlueprintRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val bpId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid blueprint id")).build()
        }

        return try
        {
            val dto = blueprintService.updateBlueprint(bpId, request, AdminApprovalContext(requestId = requestId))
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
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            logger.error("Failed to update blueprint {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update blueprint")).build()
        }
    }

    @PATCH
    @Path("/{id}/status")
    fun patchBlueprintStatus(
        @PathParam("id") id: String,
        request: PatchBlueprintStatusRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val bpId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid blueprint id")).build()
        }

        return try
        {
            val dto = blueprintService.patchStatus(bpId, request, AdminApprovalContext(requestId = requestId))
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
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            logger.error("Failed to patch blueprint status {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to patch blueprint status")).build()
        }
    }

    @PATCH
    @Path("/{id}/published")
    fun patchBlueprintPublished(
        @PathParam("id") id: String,
        request: PatchBlueprintPublishedRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val bpId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid blueprint id")).build()
        }

        return try
        {
            val dto = blueprintService.patchPublished(bpId, request, AdminApprovalContext(requestId = requestId))
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
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            logger.error("Failed to patch blueprint published {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to patch blueprint published")).build()
        }
    }

    @DELETE
    @Path("/{id}")
    fun deleteBlueprint(
        @PathParam("id") id: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val bpId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid blueprint id")).build()
        }

        return try
        {
            blueprintService.deleteBlueprint(bpId, AdminApprovalContext(requestId = requestId))
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
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            logger.error("Failed to delete blueprint {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete blueprint")).build()
        }
    }

    @POST
    @Path("/{id}/clone")
    fun cloneBlueprint(
        @PathParam("id") id: String,
        request: CloneBlueprintRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val bpId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid blueprint id")).build()
        }

        return try
        {
            val dto = blueprintService.cloneBlueprint(bpId, request, AdminApprovalContext(requestId = requestId))
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
            if (e is jakarta.ws.rs.WebApplicationException) throw e
            logger.error("Failed to clone blueprint {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to clone blueprint")).build()
        }
    }
}
