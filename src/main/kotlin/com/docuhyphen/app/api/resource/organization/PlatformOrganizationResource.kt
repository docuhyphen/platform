package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.dto.PlatformOrganizationStatusUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.PlatformOrganizationResourceOperations
import com.docuhyphen.app.api.service.platform.PlatformOrganizationService
import com.docuhyphen.app.api.service.platform.PlatformOrganizationStatusService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformOrganizationResource @Inject constructor(
    private val platformOrganizationService: PlatformOrganizationService,
    private val platformOrganizationStatusService: PlatformOrganizationStatusService,
) : PlatformOrganizationResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationResource::class.java)
        private const val DEFAULT_LIMIT = 50
    }

    override fun list(
        query: String?,
        status: String?,
        tierCode: String?,
        sort: String?,
        direction: String?,
        limit: Int?,
        offset: Int?,
        requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(
                platformOrganizationService.list(
                    query = query,
                    status = status,
                    tierCode = tierCode,
                    sort = sort,
                    direction = direction,
                    limit = limit ?: DEFAULT_LIMIT,
                    offset = offset ?: 0,
                    requestId = requestId,
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing restricted platform organization summaries", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun get(
        organizationId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(platformOrganizationService.get(organizationId, requestId)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching restricted platform organization summary", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun updateStatus(
        organizationId: String,
        requestId: String?,
        payload: PlatformOrganizationStatusUpdateRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                platformOrganizationStatusService.update(
                    organizationId = organizationId,
                    request = payload,
                    adminApprovalContext = AdminApprovalContext(requestId),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating platform organization status", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
