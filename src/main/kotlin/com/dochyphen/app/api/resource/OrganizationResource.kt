package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.UpdateOrganizationRequest
import com.dochyphen.app.api.service.organization.OrganizationService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

@Path("organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationResource @Inject constructor(
    private val organizationService: OrganizationService
)
{

    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationResource::class.java)
    }

    @Path("/{organizationId}")
    @PUT
    fun updateOrganization(
        @PathParam("organizationId") organizationId: String?,
        updateOrganizationRequest: UpdateOrganizationRequest
    ): Response
    {
        return try
        {
            with(updateOrganizationRequest)
            {
                organizationService.updateOrganization(
                    organizationId,
                    name,
                    registrationNumber
                )
            }

            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error updating organization group", exception)

            when (exception)
            {
                is UnauthorizedException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(UNAUTHORIZED).entity(responseError).build()
                }

                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(NOT_FOUND).entity(responseError).build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response.status(BAD_REQUEST).entity(responseError).build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while updating organization")

                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }
}