package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.organization.OrganizationMemberCapacityDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.organization.operations.OrganizationMemberCapacityResourceOperations
import com.docuhyphen.app.api.service.organization.OrganizationMemberCapacityService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class OrganizationMemberCapacityResource @Inject constructor(
    private val organizationMemberCapacityService: OrganizationMemberCapacityService,
) : OrganizationMemberCapacityResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationMemberCapacityResource::class.java)
    }

    override fun getMemberCapacity(orgId: String): Response
    {
        return try
        {
            val capacity = organizationMemberCapacityService.getForOrganizationId(orgId)
            Response.ok(OrganizationMemberCapacityDtoMapper.toResponse(capacity)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching organization member capacity", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to fetch member capacity."))
                        .build()
                }
            }
        }
    }
}
