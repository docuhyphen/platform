package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.fields.CreateSchemaRequest
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.PublishSchemaRequest
import com.docuhyphen.app.api.service.fields.SchemaDefinitionService
import com.docuhyphen.app.api.service.fields.UpdateBindingsRequest
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
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
 * REST endpoints for Schema Definitions, their draft/published versions, and bindings.
 * All business logic is delegated to [SchemaDefinitionService].
 *
 *   GET   /schemas/definitions                       - list schemas in caller's scope
 *   POST  /schemas/definitions                       - create a schema + its first draft version
 *   GET   /schemas/definitions/{id}                  - get a schema (draft + latest published)
 *   GET   /schemas/definitions/{id}/resolved         - resolved view of latest published version
 *   PUT   /schemas/definitions/{id}/draft/bindings   - replace the draft version's bindings
 *   POST  /schemas/definitions/{id}/draft/publish    - publish the draft version
 *   POST  /schemas/definitions/{id}/versions         - open a new draft version
 *   PATCH /schemas/definitions/{id}/status           - retire a schema
 */
@Path("/schemas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SchemaResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val schemaDefinitionService: SchemaDefinitionService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SchemaResource::class.java)
    }

    @GET
    @Path("/definitions")
    fun listSchemas(@QueryParam("scopeKind") scopeKindParam: String?): Response = guard {
        val scopeKind = scopeKindParam?.let { FieldScopeKind.valueOf(it.uppercase()) }
        Response.ok(schemaDefinitionService.listSchemas(scopeKind).toTypedArray()).build()
    }

    @POST
    @Path("/definitions")
    fun createSchema(request: CreateSchemaRequest): Response = guard {
        Response.status(CREATED).entity(schemaDefinitionService.createSchema(request)).build()
    }

    @GET
    @Path("/definitions/{id}")
    fun getSchema(@PathParam("id") id: String): Response = guard {
        val schemaId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid schema id")).build()
        Response.ok(schemaDefinitionService.getSchema(schemaId)).build()
    }

    @GET
    @Path("/definitions/{id}/resolved")
    fun getResolved(@PathParam("id") id: String): Response = guard {
        val schemaId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid schema id")).build()
        Response.ok(schemaDefinitionService.getResolvedLatestPublished(schemaId)).build()
    }

    @PUT
    @Path("/definitions/{id}/draft/bindings")
    fun updateDraftBindings(@PathParam("id") id: String, request: UpdateBindingsRequest): Response = guard {
        val schemaId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid schema id")).build()
        Response.ok(schemaDefinitionService.updateDraftBindings(schemaId, request.bindings)).build()
    }

    @POST
    @Path("/definitions/{id}/draft/publish")
    fun publishDraft(@PathParam("id") id: String, request: PublishSchemaRequest?): Response = guard {
        val schemaId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid schema id")).build()
        Response.ok(schemaDefinitionService.publishDraft(schemaId, request?.compatibility)).build()
    }

    @POST
    @Path("/definitions/{id}/versions")
    fun createDraftVersion(@PathParam("id") id: String): Response = guard {
        val schemaId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid schema id")).build()
        Response.status(CREATED).entity(schemaDefinitionService.createDraftVersion(schemaId)).build()
    }

    @PATCH
    @Path("/definitions/{id}/status")
    fun patchStatus(@PathParam("id") id: String, request: FieldStatusRequest): Response = guard {
        val schemaId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid schema id")).build()
        if (request.status != FieldLifecycleStatus.RETIRED)
            return@guard Response.status(BAD_REQUEST).entity(ResponseError("Only RETIRED is supported")).build()
        Response.ok(schemaDefinitionService.retireSchema(schemaId)).build()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun guard(block: () -> Response): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        return try
        {
            block()
        }
        catch (e: FieldValidationException)
        {
            Response.status(BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
        catch (e: IllegalStateException)
        {
            Response.status(CONFLICT).entity(ResponseError(e.message)).build()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: ForbiddenException)
        {
            Response.status(FORBIDDEN).entity(ResponseError(e.message)).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn("Schema definition request refused by subscription policy", e)
            throw e
        }
        catch (e: Exception)
        {
            logger.error("Schema request failed", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
        }
    }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}
