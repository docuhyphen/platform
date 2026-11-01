package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.model.PlatformOrganizationDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.model.auth.AdminApprovalContext
import com.docuhyphen.app.api.resource.organization.operations.PlatformOrganizationSubscriptionPolicyResourceOperations
import com.docuhyphen.app.api.service.subscription.PlatformOrganizationSubscriptionPolicyService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformOrganizationSubscriptionPolicyResource @Inject constructor(
    private val platformOrganizationSubscriptionPolicyService: PlatformOrganizationSubscriptionPolicyService,
    private val platformOrganizationDtoMapper: PlatformOrganizationDtoMapper,
) : PlatformOrganizationSubscriptionPolicyResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PlatformOrganizationSubscriptionPolicyResource::class.java)
    }

    override fun getEffectivePolicy(
        organizationId: String,
        requestId: String?,
    ): Response
    {
        return try
        {
            val policy = platformOrganizationSubscriptionPolicyService.getEffectivePolicy(organizationId, requestId)
            Response.ok(platformOrganizationDtoMapper.toSubscriptionPolicy(policy)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching platform organization subscription policy", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }

    override fun upsertPolicy(
        organizationId: String,
        requestId: String?,
        payload: PlatformOrganizationSubscriptionPolicyRequest,
    ): Response
    {
        return try
        {
            val policy = platformOrganizationSubscriptionPolicyService.upsertPolicy(
                organizationId = organizationId,
                request = payload,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )
            Response.ok(platformOrganizationDtoMapper.toSubscriptionPolicy(policy)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception

                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error upserting platform organization subscription policy", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}



