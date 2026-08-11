package com.docuhyphen.app.api.resource.variable

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CreateVariableRequest
import com.docuhyphen.app.api.model.dto.UpdateVariableRequest
import com.docuhyphen.app.api.model.entity.VariableScope
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.variable.AvailableVariablesService
import com.docuhyphen.app.api.service.variable.VariableDefinitionService
import io.quarkus.security.ForbiddenException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

@Path("/variables")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class VariableDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val variableService: VariableDefinitionService,
    private val availableVariablesService: AvailableVariablesService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(VariableDefinitionResource::class.java)
    }

    @GET
    @Path("/available")
    fun getAvailableVariables(): Response
    {
        val actor = authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        return try
        {
            val dto = availableVariablesService.getAvailableVariables(actor.id, authTokenContext.activeOrganizationId)
            Response.ok(dto).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to get available variables", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to get available variables")).build()
        }
    }

    @GET
    fun listVariables(@QueryParam("scope") scopeParam: String?): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val scope = scopeParam?.let {
            runCatching { VariableScope.valueOf(it.uppercase()) }.getOrElse {
                return Response.status(BAD_REQUEST).entity(ResponseError("Invalid scope '$scopeParam'")).build()
            }
        } ?: VariableScope.PERSONAL

        return try
        {
            val items = variableService.listVariables(scope)
            Response.ok(items.toTypedArray()).build()
        }
        catch (e: Exception)
        {
            logger.error("Failed to list variables", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list variables")).build()
        }
    }

    @POST
    fun createVariable(
        request: CreateVariableRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        if (request.key.isBlank())
            return Response.status(BAD_REQUEST).entity(ResponseError("key is required")).build()

        return try
        {
            val dto = variableService.createVariable(request, AdminApprovalContext(requestId = requestId))
            Response.status(CREATED).entity(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn("Creating a variable was refused by the subscription plan check: plan={}", e.denial.planCode)
            throw e
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
            logger.error("Failed to create variable", e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create variable")).build()
        }
    }

    @PUT
    @Path("/{id}")
    fun updateVariable(
        @PathParam("id") id: String,
        request: UpdateVariableRequest,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val varId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid variable id")).build()
        }

        return try
        {
            val dto = variableService.updateVariable(varId, request, AdminApprovalContext(requestId = requestId))
            Response.ok(dto).build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn("Updating a variable was refused by the subscription plan check: plan={}", e.denial.planCode)
            throw e
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
            logger.error("Failed to update variable {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update variable")).build()
        }
    }

    @DELETE
    @Path("/{id}")
    fun deleteVariable(
        @PathParam("id") id: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        authTokenContext.authToken.appUser
            ?: return Response.status(UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()

        val varId = runCatching { UUID.fromString(id) }.getOrElse {
            return Response.status(BAD_REQUEST).entity(ResponseError("Invalid variable id")).build()
        }

        return try
        {
            variableService.deleteVariable(varId, AdminApprovalContext(requestId = requestId))
            Response.noContent().build()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn("Deleting a variable was refused by the subscription plan check: plan={}", e.denial.planCode)
            throw e
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
            logger.error("Failed to delete variable {}", id, e)
            Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete variable")).build()
        }
    }
}
