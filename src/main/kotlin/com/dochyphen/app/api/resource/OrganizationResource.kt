package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.resource.model.AddOrganizationGroupRequest
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.service.OrganizationService
import jakarta.inject.Inject
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("organizations")
@Produces(MediaType.APPLICATION_JSON)
class OrganizationResource @Inject constructor(
    private val organizationService: OrganizationService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationResource::class.java)
    }

    @Path("/{organizationId}")
    fun addOrganizationGroup(
        @PathParam("organizationId") organizationId: String,
        addOrganizationGroupRequest: AddOrganizationGroupRequest
    ): Response
    {
        return try
        {
            with(addOrganizationGroupRequest)
            {
                organizationService.addOrganizationGroup(organizationId, name, members)
            }

            return Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    logger.error("Error getting sharing session document audit logs", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    logger.error("Error getting organization groups", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting organization groups", exception)

                    val responseError =
                        ResponseError("An error occurred while getting organization groups")

                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @Path("/{organizationId}/groups")
    fun getOrganizationGroups(
        @PathParam("organizationId") organizationId: String
    ): Response
    {
        return try
        {
            var organizationGroups = organizationService.getOrganizationGroups(organizationId)


        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    logger.error("Error getting organization groups", exception)

                    val responseError = ResponseError(exception.message)

                    Response
                        .status(Response.Status.NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    logger.error("Error getting organization groups", exception)

                    val responseError =
                        ResponseError("An error occurred while getting organization groups")

                    Response
                        .status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}