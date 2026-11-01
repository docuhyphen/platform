package com.docuhyphen.app.api.resource.communication

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.communication.operations.CommunicationResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.communication.CommunicationService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class CommunicationResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val communicationService: CommunicationService,
) : CommunicationResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(CommunicationResource::class.java)

        private const val INVALID_COMMUNICATION_ID = "Invalid communication id"
    }

    override fun listTemplates(
        scope: String?,
        tag: String?,
        isTemplate: Boolean?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val items = communicationService.listTemplates(scope, tag, isTemplate)
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
                    logger.error("Failed to list communications", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to list communications"))
                        .build()
                }
            }
        }
    }

    override fun createTemplate(request: CreateCommunicationRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            RequestGuards.requireNotBlank(request.name, "name is required")
            val dto = communicationService.createTemplate(request)
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
                        "Creating a communication was refused by the subscription plan check: plan={}",
                        exception.denial.planCode
                    )
                    throw exception
                }

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to create communication", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to create communication"))
                        .build()
                }
            }
        }
    }

    override fun getTemplate(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val communicationId = RequestGuards.uuid(id, INVALID_COMMUNICATION_ID)
            val dto = communicationService.getTemplate(communicationId)
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
                    logger.error("Failed to get communication {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to get communication")).build()
                }
            }
        }
    }

    override fun updateTemplate(id: String, request: UpdateCommunicationRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val communicationId = RequestGuards.uuid(id, INVALID_COMMUNICATION_ID)
            val dto = communicationService.updateTemplate(communicationId, request)
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
                        "Updating communication {} was refused by the subscription plan check: plan={}",
                        id,
                        exception.denial.planCode
                    )
                    throw exception
                }

                is RequestValidationException,
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Failed to update communication {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to update communication"))
                        .build()
                }
            }
        }
    }

    override fun patchTemplateStatus(id: String, request: PatchCommunicationStatusRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val communicationId = RequestGuards.uuid(id, INVALID_COMMUNICATION_ID)
            val dto = communicationService.patchStatus(communicationId, request)
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
                        "Changing communication status {} was refused by the subscription plan check: plan={}",
                        id,
                        exception.denial.planCode
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
                    logger.error("Failed to patch communication status {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to patch communication status"))
                        .build()
                }
            }
        }
    }

    override fun patchTemplatePublished(id: String, request: PatchCommunicationPublishedRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val communicationId = RequestGuards.uuid(id, INVALID_COMMUNICATION_ID)
            val dto = communicationService.patchPublished(communicationId, request)
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
                        "Publishing communication {} was refused by the subscription plan check: plan={}",
                        id,
                        exception.denial.planCode
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
                    logger.error("Failed to patch communication published {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to patch communication published"))
                        .build()
                }
            }
        }
    }

    override fun deleteTemplate(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val communicationId = RequestGuards.uuid(id, INVALID_COMMUNICATION_ID)
            communicationService.deleteTemplate(communicationId)
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
                        "Deleting communication {} was refused by the subscription plan check: plan={}",
                        id,
                        exception.denial.planCode
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
                    logger.error("Failed to delete communication {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to delete communication"))
                        .build()
                }
            }
        }
    }

    override fun previewTemplate(id: String, request: PreviewCommunicationRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val communicationId = RequestGuards.uuid(id, INVALID_COMMUNICATION_ID)
            val rendered = communicationService.preview(communicationId, request.sampleVariables)
            Response.ok(rendered).build()
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
                    logger.error("Failed to preview communication {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to preview communication"))
                        .build()
                }
            }
        }
    }

    override fun cloneTemplate(id: String, request: CloneCommunicationRequest): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val communicationId = RequestGuards.uuid(id, INVALID_COMMUNICATION_ID)
            val dto = communicationService.cloneTemplate(communicationId, request)
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

                else ->
                {
                    logger.error("Failed to clone communication {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to clone communication"))
                        .build()
                }
            }
        }
    }
}
