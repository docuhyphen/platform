package com.docuhyphen.app.api.resource.blueprint

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.blueprint.operations.BlueprintDefinitionResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.blueprint.BlueprintDefinitionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class BlueprintDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val blueprintService: BlueprintDefinitionService,
) : BlueprintDefinitionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(BlueprintDefinitionResource::class.java)

        private const val INVALID_BLUEPRINT_ID = "Invalid blueprint id"
    }

    override fun listBlueprints(
        scope: String?,
        tag: String?,
        isTemplate: Boolean?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val items = blueprintService.listBlueprints(scope, tag, isTemplate)
            Response.ok(items.toTypedArray()).build()
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
                        "Listing blueprints refused by the subscription plan check: plan={}",
                        exception.denial.planCode,
                    )
                    throw exception
                }

                else ->
                {
                    logger.error("Failed to list blueprints", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list blueprints")).build()
                }
            }
        }
    }

    override fun createBlueprint(
        request: CreateBlueprintRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            RequestGuards.requireNotBlank(request.name, "name is required")
            val dto = blueprintService.createBlueprint(request, AdminApprovalContext(requestId = requestId))
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
                        "Creating a blueprint was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to create blueprint", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create blueprint")).build()
                }
            }
        }
    }

    override fun getBlueprint(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val blueprintId = RequestGuards.uuid(id, INVALID_BLUEPRINT_ID)
            val dto = blueprintService.getBlueprint(blueprintId)
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
                        "Reading a blueprint was refused by the subscription plan check: plan={}",
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

                else ->
                {
                    logger.error("Failed to get blueprint {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to get blueprint")).build()
                }
            }
        }
    }

    override fun updateBlueprint(
        id: String,
        request: UpdateBlueprintRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val blueprintId = RequestGuards.uuid(id, INVALID_BLUEPRINT_ID)
            val dto = blueprintService.updateBlueprint(blueprintId, request, AdminApprovalContext(requestId = requestId))
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
                        "Updating a blueprint was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to update blueprint {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update blueprint")).build()
                }
            }
        }
    }

    override fun patchBlueprintStatus(
        id: String,
        request: PatchBlueprintStatusRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val blueprintId = RequestGuards.uuid(id, INVALID_BLUEPRINT_ID)
            val dto = blueprintService.patchStatus(blueprintId, request, AdminApprovalContext(requestId = requestId))
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
                        "Changing a blueprint status was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to patch blueprint status {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to patch blueprint status"))
                        .build()
                }
            }
        }
    }

    override fun patchBlueprintPublished(
        id: String,
        request: PatchBlueprintPublishedRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val blueprintId = RequestGuards.uuid(id, INVALID_BLUEPRINT_ID)
            val dto = blueprintService.patchPublished(blueprintId, request, AdminApprovalContext(requestId = requestId))
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
                        "Publishing a blueprint was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to patch blueprint published {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to patch blueprint published"))
                        .build()
                }
            }
        }
    }

    override fun deleteBlueprint(
        id: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val blueprintId = RequestGuards.uuid(id, INVALID_BLUEPRINT_ID)
            blueprintService.deleteBlueprint(blueprintId, AdminApprovalContext(requestId = requestId))
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
                        "Deleting a blueprint was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to delete blueprint {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete blueprint")).build()
                }
            }
        }
    }

    override fun cloneBlueprint(
        id: String,
        request: CloneBlueprintRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val blueprintId = RequestGuards.uuid(id, INVALID_BLUEPRINT_ID)
            val dto = blueprintService.cloneBlueprint(blueprintId, request, AdminApprovalContext(requestId = requestId))
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
                        "Cloning a blueprint was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to clone blueprint {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to clone blueprint")).build()
                }
            }
        }
    }
}
