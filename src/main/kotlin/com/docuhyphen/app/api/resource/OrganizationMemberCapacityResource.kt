package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.repository.OrganizationSubscriptionPolicyRepository
import com.docuhyphen.app.api.resource.model.OrgMemberCapacityResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.auth.PlatformOrganizationSubscriptionPolicyService
import com.docuhyphen.app.api.service.organization.OrganizationService
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
    private val authTokenContext: AuthTokenContext,
    private val organizationService: OrganizationService,
    private val organizationSubscriptionPolicyRepository: OrganizationSubscriptionPolicyRepository,
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
            val appUser = authTokenContext.authToken.appUser!!
            val orgUuid = runCatching { UUID.fromString(orgId) }.getOrElse {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Invalid organization ID."))
                    .build()
            }

            val isPlatformAdmin = appUser.role == AppUserRole.PLATFORM_ADMIN
            val isOrgAdmin = appUser.role == AppUserRole.ORG_ADMIN

            if (!isPlatformAdmin && !isOrgAdmin)
            {
                return Response.status(Response.Status.FORBIDDEN)
                    .entity(ResponseError("Access denied."))
                    .build()
            }

            if (isOrgAdmin)
            {
                val memberOrg = appUser.person?.contactDetails?.organization
                if (memberOrg?.id != orgUuid)
                {
                    return Response.status(Response.Status.FORBIDDEN)
                        .entity(ResponseError("Access denied."))
                        .build()
                }
            }

            val org = runCatching { organizationService.getOrganizationById(orgUuid) }.getOrElse {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(ResponseError("Organization not found."))
                    .build()
            }

            val policy = organizationSubscriptionPolicyRepository.findByOrganizationId(orgUuid)
            val tierCode = policy?.tierCode ?: PlatformOrganizationSubscriptionPolicyService.FREE_TIER_CODE
            val maxUsers = policy?.maxUsers
                ?: if (tierCode.equals(PlatformOrganizationSubscriptionPolicyService.FREE_TIER_CODE, ignoreCase = true))
                    PlatformOrganizationSubscriptionPolicyService.FREE_TIER_MAX_USERS
                else null
            val activeUsers = org.appUsers.count { it.isActive }.toLong()
            val atCap = maxUsers != null && activeUsers >= maxUsers
            val nearCap = maxUsers != null && activeUsers >= (maxUsers * 0.8).toLong()

            Response.ok(
                OrgMemberCapacityResponse(
                    organizationId = orgUuid.toString(),
                    tierCode = tierCode,
                    maxUsers = maxUsers,
                    activeUsers = activeUsers,
                    atCap = atCap,
                    nearCap = nearCap,
                )
            ).build()
        }
        catch (e: Exception)
        {
            logger.error("Error fetching org member capacity", e)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to fetch member capacity."))
                .build()
        }
    }
}
