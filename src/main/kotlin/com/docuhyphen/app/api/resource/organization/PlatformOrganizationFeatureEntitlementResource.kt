package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.dto.PlatformOrganizationFeatureEntitlementsUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.PlatformOrganizationFeatureEntitlementResourceOperations
import com.docuhyphen.app.api.service.platform.PlatformOrganizationService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformOrganizationFeatureEntitlementResource @Inject constructor(
    private val platformOrganizationService: PlatformOrganizationService,
) : PlatformOrganizationFeatureEntitlementResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationFeatureEntitlementResource::class.java)
    }

    override fun get(
        organizationId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            Response.ok(
                platformOrganizationService.getFeatureEntitlements(organizationId, requestId),
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
                    logger.error("Error fetching platform organization feature entitlements", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun replace(
        organizationId: String,
        requestId: String?,
        payload: PlatformOrganizationFeatureEntitlementsUpdateRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                platformOrganizationService.replaceFeatureEntitlements(
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
                    logger.error("Error replacing platform organization feature entitlements", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
