package com.docuhyphen.app.api.resource.fields

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.fields.CreateFieldDefinitionRequest
import com.docuhyphen.app.api.service.fields.FieldContractRequest
import com.docuhyphen.app.api.service.fields.FieldDefinitionService
import com.docuhyphen.app.api.service.fields.FieldValidationException
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PATCH
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
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * REST endpoints for Field Definitions, their immutable contract versions, and the field type
 * registry. All business logic is delegated to [FieldDefinitionService].
 *
 *   GET   /fields/types                              - registered field value types
 *   GET   /fields/definitions                        - list definitions in caller's scope
 *   POST  /fields/definitions                        - create a definition + its first contract
 *   GET   /fields/definitions/{id}                   - get a definition
 *   GET   /fields/definitions/{id}/contracts         - list contract versions
 *   POST  /fields/definitions/{id}/contracts         - add a new immutable contract version
 *   PATCH /fields/definitions/{id}/status            - retire a definition
 */
@Path("/fields")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class FieldDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val fieldDefinitionService: FieldDefinitionService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(FieldDefinitionResource::class.java)
    }

    @GET
    @Path("/types")
    fun listTypes(): Response = guard {
        Response.ok(fieldDefinitionService.listTypes().toTypedArray()).build()
    }

    @GET
    @Path("/definitions")
    fun listDefinitions(@QueryParam("scopeKind") scopeKindParam: String?): Response = guard {
        val scopeKind = scopeKindParam?.let { FieldScopeKind.valueOf(it.uppercase()) }
        Response.ok(fieldDefinitionService.listDefinitions(scopeKind).toTypedArray()).build()
    }

    @POST
    @Path("/definitions")
    fun createDefinition(request: CreateFieldDefinitionRequest): Response = guard {
        Response.status(CREATED).entity(fieldDefinitionService.createDefinition(request)).build()
    }

    @GET
    @Path("/definitions/{id}")
    fun getDefinition(@PathParam("id") id: String): Response = guard {
        val defId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()
        Response.ok(fieldDefinitionService.getDefinition(defId)).build()
    }

    @GET
    @Path("/definitions/{id}/contracts")
    fun listContracts(@PathParam("id") id: String): Response = guard {
        val defId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()
        Response.ok(fieldDefinitionService.listContracts(defId).toTypedArray()).build()
    }

    @POST
    @Path("/definitions/{id}/contracts")
    fun addContract(@PathParam("id") id: String, request: FieldContractRequest): Response = guard {
        val defId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()
        Response.status(CREATED).entity(fieldDefinitionService.addContractVersion(defId, request)).build()
    }

    @PATCH
    @Path("/definitions/{id}/status")
    fun patchStatus(@PathParam("id") id: String, request: FieldStatusRequest): Response = guard {
        val defId = parseUuid(id)
            ?: return@guard Response.status(BAD_REQUEST).entity(ResponseError("Invalid definition id")).build()
        if (request.status != FieldLifecycleStatus.RETIRED)
            return@guard Response.status(BAD_REQUEST).entity(ResponseError("Only RETIRED is supported")).build()
        Response.ok(fieldDefinitionService.retireDefinition(defId)).build()
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
            logger.warn("Field definition request refused by subscription policy", e)
            throw e
        }
        catch (e: Exception)
        {
            logger.error("Field definition request failed", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
        }
    }

    private fun parseUuid(raw: String): UUID? = runCatching { UUID.fromString(raw) }.getOrNull()
}

@Serializable
data class FieldStatusRequest(val status: FieldLifecycleStatus)
