package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.fields.AssignSchemaRequest
import com.docuhyphen.app.api.service.fields.FieldValidationException
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import com.docuhyphen.app.api.service.fields.SetFieldValuesRequest
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.CONFLICT
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import jakarta.ws.rs.core.Response.Status.NOT_FOUND
import jakarta.ws.rs.core.Response.Status.NO_CONTENT
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * REST endpoints for an Exchange's Fields: its Schema Assignment and typed field values.
 * All business logic is delegated to [SchemaAssignmentService] through the EXCHANGE resource adapter.
 *
 *   GET    /exchanges/{id}/schema  - current assignment + resolved values (204 if none)
 *   PUT    /exchanges/{id}/schema  - assign a published schema to the exchange
 *   DELETE /exchanges/{id}/schema  - remove the assignment and its values
 *   PUT    /exchanges/{id}/fields  - upsert typed field values
 */
@Path("/exchanges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExchangeFieldsResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val schemaAssignmentService: SchemaAssignmentService,
)
{
    companion object
    {
        private const val RESOURCE_TYPE = "EXCHANGE"
        private val logger = LoggerFactory.getLogger(ExchangeFieldsResource::class.java)
    }

    @GET
    @Path("/{id}/schema")
    fun getSchema(@PathParam("id") id: String): Response = guard {
        val exchangeId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid exchange id")).build()
        val assignment = schemaAssignmentService.getAssignment(RESOURCE_TYPE, exchangeId)
            ?: return@guard Response.status(NO_CONTENT).build()
        Response.ok(assignment).build()
    }

    @PUT
    @Path("/{id}/schema")
    fun assignSchema(@PathParam("id") id: String, request: AssignSchemaRequest): Response = guard {
        val exchangeId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid exchange id")).build()
        Response.ok(
            schemaAssignmentService.assignSchema(RESOURCE_TYPE, exchangeId, request.schemaDefinitionId),
        ).build()
    }

    @DELETE
    @Path("/{id}/schema")
    fun unassignSchema(@PathParam("id") id: String): Response = guard {
        val exchangeId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid exchange id")).build()
        schemaAssignmentService.unassignSchema(RESOURCE_TYPE, exchangeId)
        Response.status(NO_CONTENT).build()
    }

    @PUT
    @Path("/{id}/fields")
    fun setValues(@PathParam("id") id: String, request: SetFieldValuesRequest): Response = guard {
        val exchangeId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid exchange id")).build()
        Response.ok(schemaAssignmentService.setValues(RESOURCE_TYPE, exchangeId, request.values)).build()
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
        catch (e: Exception)
        {
            logger.error("Exchange fields request failed", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
        }
    }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}
