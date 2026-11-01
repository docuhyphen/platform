package com.docuhyphen.app.api.resource.variable

import com.docuhyphen.app.api.exception.RequestValidationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.model.dto.CreateSequenceRequest
import com.docuhyphen.app.api.model.dto.UpdateSequenceRequest
import com.docuhyphen.app.api.resource.RequestGuards
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.variable.operations.SequenceDefinitionResourceOperations
import com.docuhyphen.app.api.service.variable.SequenceDefinitionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class SequenceDefinitionResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val sequenceService: SequenceDefinitionService,
) : SequenceDefinitionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SequenceDefinitionResource::class.java)

        private const val INVALID_SEQUENCE_ID = "Invalid sequence id"
    }

    override fun listSequences(isActive: Boolean?): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val items = sequenceService.listSequences(isActive)
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
                    logger.error("Failed to list sequences", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to list sequences")).build()
                }
            }
        }
    }

    override fun createSequence(
        request: CreateSequenceRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            RequestGuards.requireNotBlank(request.name, "name is required")
            val dto = sequenceService.createSequence(request, AdminApprovalContext(requestId = requestId))
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
                        "Creating a sequence was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to create sequence", exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to create sequence")).build()
                }
            }
        }
    }

    override fun getSequence(id: String): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val sequenceId = RequestGuards.uuid(id, INVALID_SEQUENCE_ID)
            val dto = sequenceService.getSequence(sequenceId)
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
                    logger.error("Failed to get sequence {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to get sequence")).build()
                }
            }
        }
    }

    override fun updateSequence(
        id: String,
        request: UpdateSequenceRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val sequenceId = RequestGuards.uuid(id, INVALID_SEQUENCE_ID)
            val dto = sequenceService.updateSequence(sequenceId, request, AdminApprovalContext(requestId = requestId))
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
                        "Updating a sequence was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to update sequence {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to update sequence")).build()
                }
            }
        }
    }

    override fun deleteSequence(
        id: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val sequenceId = RequestGuards.uuid(id, INVALID_SEQUENCE_ID)
            sequenceService.deleteSequence(sequenceId, AdminApprovalContext(requestId = requestId))
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
                        "Deleting a sequence was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to delete sequence {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to delete sequence")).build()
                }
            }
        }
    }

    override fun resetCounter(
        id: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            RequestGuards.requireUser(authTokenContext)
            val sequenceId = RequestGuards.uuid(id, INVALID_SEQUENCE_ID)
            val dto = sequenceService.resetCounter(sequenceId, AdminApprovalContext(requestId = requestId))
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
                        "Resetting a sequence counter was refused by the subscription plan check: plan={}",
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
                    logger.error("Failed to reset counter for sequence {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("Failed to reset counter")).build()
                }
            }
        }
    }
}
