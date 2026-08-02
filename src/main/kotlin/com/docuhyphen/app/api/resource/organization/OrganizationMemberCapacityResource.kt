package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.resource.model.OrgMemberCapacityResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.organization.OrganizationMemberCapacityService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("/auth/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class OrganizationMemberCapacityResource @Inject constructor(
    private val organizationMemberCapacityService: OrganizationMemberCapacityService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationMemberCapacityResource::class.java)
    }

    @GET
    @Path("/{orgId}/member-capacity")
    fun getMemberCapacity(@PathParam("orgId") orgId: String): Response
    {
        return try
        {
            val orgUuid = runCatching { UUID.fromString(orgId) }.getOrElse {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Invalid organization ID."))
                    .build()
            }

            val capacity = organizationMemberCapacityService.getForOrganization(orgUuid)
            val atCap = capacity.maxUsers != null && capacity.activeUsers >= capacity.maxUsers
            val nearCap = capacity.maxUsers != null && capacity.activeUsers >= (capacity.maxUsers * 0.8).toLong()

            Response.ok(
                OrgMemberCapacityResponse(
                    organizationId = capacity.organizationId.toString(),
                    tierCode = capacity.tierCode,
                    maxUsers = capacity.maxUsers,
                    activeUsers = capacity.activeUsers,
                    atCap = atCap,
                    nearCap = nearCap,
                )
            ).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching organization member capacity", exception)
            when (exception)
            {
                is UnauthorizedException -> Response.status(Response.Status.FORBIDDEN)
                    .entity(ResponseError(exception.message)).build()
                is com.docuhyphen.app.api.exception.OrganizationNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND)
                        .entity(ResponseError(exception.message)).build()
                else -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ResponseError("Failed to fetch member capacity.")).build()
            }
        }
    }
}
