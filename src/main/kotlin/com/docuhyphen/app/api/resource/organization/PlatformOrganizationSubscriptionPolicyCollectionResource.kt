package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.resource.model.PlatformOrganizationSubscriptionPolicyListResponse
import com.docuhyphen.app.api.model.PlatformOrganizationDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.subscription.PlatformOrganizationSubscriptionPolicyService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/platform/organizations/subscription-policies")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class PlatformOrganizationSubscriptionPolicyCollectionResource @Inject constructor(
    private val platformOrganizationSubscriptionPolicyService: PlatformOrganizationSubscriptionPolicyService,
    private val platformOrganizationDtoMapper: PlatformOrganizationDtoMapper,
)
{
    companion object
    {
        private val logger =
            LoggerFactory.getLogger(PlatformOrganizationSubscriptionPolicyCollectionResource::class.java)
    }

    @GET
    fun list(
        @QueryParam("limit") limit: Int?,
        @QueryParam("offset") offset: Int?,
        @QueryParam("tierCode") tierCode: String?,
        @QueryParam("persistedOnly") persistedOnly: Boolean?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        return try
        {
            val result = platformOrganizationSubscriptionPolicyService.listPolicies(
                organizationId = "all",
                limit = limit ?: 50,
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
            logger.error("Error listing platform organization subscription policy resources", exception)
            when (exception)
            {
                is UnauthorizedException -> Response.status(Response.Status.UNAUTHORIZED)
                    .entity(ResponseError(exception.message)).build()
                is ForbiddenException -> Response.status(Response.Status.FORBIDDEN)
                    .entity(ResponseError(exception.message)).build()
                is IllegalArgumentException -> Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError(exception.message)).build()
                else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseError("An unexpected error occurred")).build()
            }
        }
    }
}
