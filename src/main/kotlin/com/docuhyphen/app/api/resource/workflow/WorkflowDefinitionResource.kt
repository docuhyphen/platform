package com.docuhyphen.app.api.resource.workflow

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.model.workflow.CloneWorkflowRequest
import com.docuhyphen.app.api.model.workflow.CreateWorkflowDefinitionRequest
import com.docuhyphen.app.api.model.workflow.PatchWorkflowPublishedRequest
import com.docuhyphen.app.api.model.workflow.PatchWorkflowStatusRequest
import com.docuhyphen.app.api.model.workflow.UpdateWorkflowDefinitionRequest
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.workflow.operations.WorkflowDefinitionResourceOperations
import com.docuhyphen.app.api.service.workflow.WorkflowDefinitionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class WorkflowDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val workflowDefinitionService: WorkflowDefinitionService,
) : WorkflowDefinitionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(WorkflowDefinitionResource::class.java)

        private const val INVALID_DEFINITION_ID = "Invalid definition id"
        private const val INVALID_INSTANCE_ID = "Invalid instance id"
    }

    override fun listDefinitions(
        scope: String?,
        tag: String?,
        triggerEvent: String?,
        isTemplate: Boolean?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val items = workflowDefinitionService.listDefinitions(scope, tag, triggerEvent, isTemplate)
            Response.ok(items.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to list workflow definitions", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to list workflow definitions")).build()
                }
            }
        }
    }

    override fun createDefinition(request: CreateWorkflowDefinitionRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            RequestGuards.requireNotBlank(request.name, "name is required")
            RequestGuards.requireNotBlank(request.triggerEvent, "triggerEvent is required")
            val dto = workflowDefinitionService.createDefinition(request)
            Response.status(CREATED).entity(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow definition creation refused by subscription policy", exception)
                    throw exception
                }

                else ->
                {
                    logger.error("Failed to create workflow definition", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to create workflow definition")).build()
                }
            }
        }
    }

    override fun getDefinition(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val definitionId = RequestGuards.uuid(id, INVALID_DEFINITION_ID)
            val dto = workflowDefinitionService.getDefinition(definitionId)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to get workflow definition {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to get workflow definition")).build()
                }
            }
        }
    }

    override fun updateDefinition(
        id: String,
        request: UpdateWorkflowDefinitionRequest,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val definitionId = RequestGuards.uuid(id, INVALID_DEFINITION_ID)
            val dto = workflowDefinitionService.updateDefinition(definitionId, request)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalStateException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow definition update refused by subscription policy for {}", id, exception)
                    throw exception
                }

                else ->
                {
                    logger.error("Failed to update workflow definition {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to update workflow definition")).build()
                }
            }
        }
    }

    override fun patchStatus(
        id: String,
        request: PatchWorkflowStatusRequest,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val definitionId = RequestGuards.uuid(id, INVALID_DEFINITION_ID)
            val dto = workflowDefinitionService.patchStatus(definitionId, request.isActive)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Workflow definition status change refused by subscription policy for {}",
                        id,
                        exception,
                    )
                    throw exception
                }

                else ->
                {
                    logger.error("Failed to patch status for workflow definition {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to patch workflow definition status")).build()
                }
            }
        }
    }

    override fun patchPublished(
        id: String,
        request: PatchWorkflowPublishedRequest,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val definitionId = RequestGuards.uuid(id, INVALID_DEFINITION_ID)
            val dto = workflowDefinitionService.patchPublished(definitionId, request.isPublished)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn(
                        "Workflow definition publication change refused by subscription policy for {}",
                        id,
                        exception,
                    )
                    throw exception
                }

                else ->
                {
                    logger.error("Failed to patch published for workflow definition {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to update workflow definition")).build()
                }
            }
        }
    }

    override fun deleteDefinition(
        id: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val definitionId = RequestGuards.uuid(id, INVALID_DEFINITION_ID)
            workflowDefinitionService.deleteDefinition(definitionId, AdminApprovalContext(requestId = requestId))
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow definition deletion refused by subscription policy for {}", id, exception)
                    throw exception
                }

                is WebApplicationException -> throw exception

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalStateException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to delete workflow definition {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to delete workflow definition")).build()
                }
            }
        }
    }

    override fun cloneDefinition(
        id: String,
        request: CloneWorkflowRequest,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val definitionId = RequestGuards.uuid(id, INVALID_DEFINITION_ID)
            val dto = workflowDefinitionService.cloneDefinition(definitionId, request.newName)
            Response.status(CREATED).entity(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is SubscriptionDenialException ->
                {
                    logger.warn("Workflow definition clone refused by subscription policy for {}", id, exception)
                    throw exception
                }

                else ->
                {
                    logger.error("Failed to clone workflow definition {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to clone workflow definition")).build()
                }
            }
        }
    }

    override fun listTriggers(): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val triggers = workflowDefinitionService.listTriggers()
            Response.ok(triggers.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to list workflow triggers", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to list workflow triggers")).build()
                }
            }
        }
    }

    override fun entityLookup(
        lookupType: String?,
        q: String?,
    ): Response
    {
        return try
        {
            val actor = RequestGuards.requireUser(authTokenContext)
            val statedLookupType = RequestGuards.requireNotBlank(lookupType, "lookupType is required")
            val results = workflowDefinitionService.entityLookup(actor, statedLookupType, q)
            Response.ok(results.toTypedArray()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to perform entity lookup for type {}", lookupType, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Entity lookup failed")).build()
                }
            }
        }
    }

    override fun listInstances(
        status: String?,
        subjectResourceType: String?,
        page: Int,
        pageSize: Int,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val items = workflowDefinitionService.listInstances(
                status,
                subjectResourceType,
                page.coerceAtLeast(0),
                pageSize.coerceIn(1, 100),
            )
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

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to list workflow instances", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to list workflow instances")).build()
                }
            }
        }
    }

    override fun getInstanceDetail(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val instanceId = RequestGuards.uuid(id, INVALID_INSTANCE_ID)
            val dto = workflowDefinitionService.getInstanceDetail(instanceId)
            Response.ok(dto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is RequestValidationException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to get workflow instance {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to get workflow instance")).build()
                }
            }
        }
    }
}
