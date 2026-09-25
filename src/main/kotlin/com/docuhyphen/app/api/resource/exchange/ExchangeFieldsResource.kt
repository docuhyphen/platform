package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.resource.fields.FieldsPreconditionHeader
import com.docuhyphen.app.api.resource.model.AssignSchemaRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.SetFieldValuesRequest
import com.docuhyphen.app.api.service.fields.*
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

/**
 * REST endpoints for an Exchange's Fields: its Schema Assignment and typed field values.
 * All business logic is delegated to [SchemaAssignmentService] through the EXCHANGE resource adapter.
 *
 *   GET    /exchanges/{id}/schema  - current assignment + resolved values (204 if none)
 *   PUT    /exchanges/{id}/schema  - assign a published schema to the exchange
 *   DELETE /exchanges/{id}/schema  - remove the assignment and its values
 *   PATCH  /exchanges/{id}/fields  - sparse update of typed field values, conditioned on `If-Match`
 *
 * The read and every successful mutation carry an `ETag` naming the exact state of the exchange's
 * answers, which is what a client sends back to condition its next write on what it read.
 */
@Path("/exchanges")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ExchangeFieldsResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val schemaAssignmentService: SchemaAssignmentService,
    private val fieldsAccessContextFactory: FieldsAccessContextFactory,
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
        val assignment = schemaAssignmentService.getAssignment(
            FieldValueReadCommand(resource(exchangeId), fieldsAccessContextFactory.current()),
        ) ?: return@guard Response.status(NO_CONTENT).build()
        validated(assignment)
    }

    @PUT
    @Path("/{id}/schema")
    fun assignSchema(@PathParam("id") id: String, request: AssignSchemaRequest): Response = guard {
        val exchangeId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid exchange id")).build()
        val assignment = schemaAssignmentService.applySchemaAssignment(
            SchemaAssignmentCommand(
                resource = resource(exchangeId),
                access = fieldsAccessContextFactory.current(),
                operation = SchemaAssignmentOperation.ASSIGN,
                schemaDefinitionId = request.schemaDefinitionId,
            ),
        ) ?: return@guard Response.status(NO_CONTENT).build()
        validated(assignment)
    }

    @DELETE
    @Path("/{id}/schema")
    fun unassignSchema(@PathParam("id") id: String): Response = guard {
        val exchangeId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid exchange id")).build()
        schemaAssignmentService.applySchemaAssignment(
            SchemaAssignmentCommand(
                resource = resource(exchangeId),
                access = fieldsAccessContextFactory.current(),
                operation = SchemaAssignmentOperation.UNASSIGN,
            ),
        )
        Response.status(NO_CONTENT).build()
    }

    /**
     * Changes only the values it carries, and only while the exchange's answers still stand in the
     * version the caller states in `If-Match`. A caller that states no version is refused, because a
     * save that has not read what it is overwriting cannot know it is not discarding someone else's
     * answer.
     */
    @PATCH
    @Path("/{id}/fields")
    fun patchValues(
        @PathParam("id") id: String,
        request: SetFieldValuesRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
    ): Response = guard {
        val exchangeId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid exchange id")).build()
        validated(
            schemaAssignmentService.setValues(
                FieldValueWriteCommand(
                    resource = resource(exchangeId),
                    access = fieldsAccessContextFactory.current(),
                    entries = request.values,
                    precondition = FieldsPreconditionHeader.required(ifMatch),
                ),
            ),
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resource(exchangeId: UUID) = FieldsResourceRef(RESOURCE_TYPE, exchangeId)

    /**
     * The assignment projection plus the entity tag the service resolved for the state of its
     * answers, so a client can condition its next write on the state it just read. A projection with
     * no set of answers to validate is returned without a tag rather than with an invented one.
     */
    private fun validated(assignment: SchemaAssignmentDto): Response
    {
        val builder = Response.ok(assignment)
        assignment.etag?.let { builder.header("ETag", it) }
        return builder.build()
    }

    /**
     * A refused conditional change. A caller that named no state is told it must name one, a caller
     * whose state has moved on is told so separately, and both carry the machine code plus the state
     * that is now current, so a client can recover without guessing which of the two happened.
     */
    private fun preconditionRefused(e: FieldsPreconditionException): Response
    {
        val status = when (e.kind)
        {
            FieldsPreconditionException.Kind.REQUIRED -> PRECONDITION_REQUIRED
            FieldsPreconditionException.Kind.STALE -> PRECONDITION_FAILED
        }
        val builder = Response.status(status).entity(ResponseError(e.message, e.reasonCode))
        e.currentETag?.let { builder.header("ETag", it) }
        return builder.build()
    }

    private fun guard(block: () -> Response): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        return try
        {
            block()
        }
        catch (e: FieldsPreconditionException)
        {
            preconditionRefused(e)
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
            logger.warn("Exchange Fields request refused by subscription policy", e)
            throw e
        }
        catch (e: Exception)
        {
            logger.error("Exchange fields request failed", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
        }
    }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}
