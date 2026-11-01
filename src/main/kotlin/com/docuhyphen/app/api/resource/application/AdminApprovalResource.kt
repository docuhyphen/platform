package com.docuhyphen.app.api.resource.application

import com.docuhyphen.app.api.resource.application.operations.AdminApprovalResourceOperations
import com.docuhyphen.app.api.resource.model.AdminApprovalInitiateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.application.AdminApprovalService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

class AdminApprovalResource @Inject constructor(
    private val adminApprovalService: AdminApprovalService,
) : AdminApprovalResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AdminApprovalResource::class.java)
    }

    override fun initiate(
        payload: AdminApprovalInitiateRequest,
        requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(adminApprovalService.initiate(payload, requestId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error initiating admin approval", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while initiating the admin approval"))
                        .build()
                }
            }
        }
    }

    override fun approve(
        approvalId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(adminApprovalService.approve(UUID.fromString(approvalId), requestId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error approving admin approval", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while approving the admin approval"))
                        .build()
                }
            }
        }
    }
}
