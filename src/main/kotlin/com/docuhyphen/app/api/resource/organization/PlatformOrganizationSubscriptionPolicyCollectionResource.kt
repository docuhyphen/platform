package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.model.PlatformOrganizationDtoMapper
import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyListResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.organization.operations.PlatformOrganizationSubscriptionPolicyCollectionResourceOperations
import com.docuhyphen.app.api.service.subscription.PlatformOrganizationSubscriptionPolicyService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PlatformOrganizationSubscriptionPolicyCollectionResource @Inject constructor(
    private val platformOrganizationSubscriptionPolicyService: PlatformOrganizationSubscriptionPolicyService,
    private val platformOrganizationDtoMapper: PlatformOrganizationDtoMapper,
) : PlatformOrganizationSubscriptionPolicyCollectionResourceOperations
{
    companion object
    {
        private val logger =
            LoggerFactory.getLogger(PlatformOrganizationSubscriptionPolicyCollectionResource::class.java)
        private const val ALL_ORGANIZATIONS = "all"
        private const val DEFAULT_LIMIT = 50
    }

    override fun list(
        limit: Int?,
        offset: Int?,
        tierCode: String?,
        persistedOnly: Boolean?,
        requestId: String?,
    ): Response
    {
        return try
        {
            val result = platformOrganizationSubscriptionPolicyService.listPolicies(
                organizationId = ALL_ORGANIZATIONS,
                limit = limit ?: DEFAULT_LIMIT,
                offset = offset ?: 0,
                tierCode = tierCode,
                persistedOnly = persistedOnly,
                requestId = requestId,
            )
            Response.ok(
                PlatformOrganizationSubscriptionPolicyListResponse(
                    total = result.total,
                    limit = result.limit,
                    offset = result.offset,
                    items = result.items.map(platformOrganizationDtoMapper::toSubscriptionPolicy),
                ),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing platform organization subscription policy resources", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An unexpected error occurred"))
                        .build()
                }
            }
        }
    }
}
