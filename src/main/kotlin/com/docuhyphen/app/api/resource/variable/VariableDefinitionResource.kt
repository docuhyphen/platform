package com.docuhyphen.app.api.resource.variable

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.model.dto.CreateVariableRequest
import com.docuhyphen.app.api.model.dto.UpdateVariableRequest
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.variable.operations.VariableDefinitionResourceOperations
import com.docuhyphen.app.api.service.variable.AvailableVariablesService
import com.docuhyphen.app.api.service.variable.VariableDefinitionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class VariableDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val variableService: VariableDefinitionService,
    private val availableVariablesService: AvailableVariablesService,
) : VariableDefinitionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(VariableDefinitionResource::class.java)

        private const val INVALID_VARIABLE_ID = "Invalid variable id"
    }

    override fun getAvailableVariables(): Response
    {
        return try
        {
            val actor = RequestGuards.requireUser(authTokenContext)
            val dto = availableVariablesService.getAvailableVariables(actor.id, authTokenContext.activeOrganizationId)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to get available variables", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to get available variables"))
                        .build()
                }
            }
        }
    }

    override fun listVariables(scopeParam: String?): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val items = variableService.listVariables(scopeParam)
            Response.ok(items.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to list variables", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list variables")).build()
                }
            }
        }
    }

    override fun createVariable(
        request: CreateVariableRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            RequestGuards.requireNotBlank(request.key, "key is required")
            val dto = variableService.createVariable(request, AdminApprovalContext(requestId = requestId))
            Response.status(CREATED).entity(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Creating a variable was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is WebApplicationException -> throw exception

                else ->
                {
                    logger.error("Failed to create variable", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create variable")).build()
                }
            }
        }
    }

    override fun updateVariable(
        id: String,
        request: UpdateVariableRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val variableId = RequestGuards.uuid(id, INVALID_VARIABLE_ID)
            val dto = variableService.updateVariable(variableId, request, AdminApprovalContext(requestId = requestId))
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Updating a variable was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is WebApplicationException -> throw exception

                else ->
                {
                    logger.error("Failed to update variable {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update variable")).build()
                }
            }
        }
    }

    override fun deleteVariable(
        id: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val variableId = RequestGuards.uuid(id, INVALID_VARIABLE_ID)
            variableService.deleteVariable(variableId, AdminApprovalContext(requestId = requestId))
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Deleting a variable was refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is WebApplicationException -> throw exception

                else ->
                {
                    logger.error("Failed to delete variable {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete variable")).build()
                }
            }
        }
    }
}
