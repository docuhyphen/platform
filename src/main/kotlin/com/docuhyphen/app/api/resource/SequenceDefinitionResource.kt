package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CreateSequenceRequest
import com.docuhyphen.app.api.model.dto.UpdateSequenceRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
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
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SequenceDefinitionResource::class.java)
    }

    @GET
    fun listSequences(@QueryParam("isActive") isActive: Boolean?): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        return try
        {
            val items = sequenceService.listSequences(isActive)
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list sequences", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list sequences")).build()
        }
    }

    @POST
    fun createSequence(
        request: CreateSequenceRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (request.name.isBlank())
            return Response.status(BAD_REQUEST).entity(ResponseError("name is required")).build()

        return try
        {
            val dto = sequenceService.createSequence(request, AdminApprovalContext(requestId = requestId))
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
            if (e is WebApplicationException) throw e
            logger.error("Failed to create sequence", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create sequence")).build()
        }
    }

    @GET
    @Path("/{id}")
    fun getSequence(@PathParam("id") id: String): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val seqId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid sequence id")).build()
        }

        return try
        {
            val dto = sequenceService.getSequence(seqId)
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
    fun updateSequence(
        @PathParam("id") id: String,
        request: UpdateSequenceRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val seqId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid sequence id")).build()
        }

        return try
        {
            val dto = sequenceService.updateSequence(seqId, request, AdminApprovalContext(requestId = requestId))
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
            if (e is WebApplicationException) throw e
            logger.error("Failed to update sequence {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update sequence")).build()
        }
    }

    @DELETE
    @Path("/{id}")
    fun deleteSequence(
        @PathParam("id") id: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val seqId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid sequence id")).build()
        }

        return try
        {
            sequenceService.deleteSequence(seqId, AdminApprovalContext(requestId = requestId))
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
            if (e is WebApplicationException) throw e
            logger.error("Failed to delete sequence {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete sequence")).build()
        }
    }

    @PATCH
    @Path("/{id}/reset")
    fun resetCounter(
        @PathParam("id") id: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val seqId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid sequence id")).build()
        }

        return try
        {
            val dto = sequenceService.resetCounter(seqId, AdminApprovalContext(requestId = requestId))
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
            if (e is WebApplicationException) throw e
            logger.error("Failed to reset counter for sequence {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to reset counter")).build()
        }
    }
}
