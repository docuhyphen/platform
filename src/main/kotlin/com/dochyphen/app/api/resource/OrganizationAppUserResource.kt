package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.model.entity.EntityToDtoTransformer
import com.dochyphen.app.api.resource.model.AddOrganizationAppUserRequest
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.service.OrganizationAppUserService
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

@Path("organizations")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationAppUserResource @Inject constructor(
    private val organizationAppUserService: OrganizationAppUserService
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationAppUserResource::class.java)
    }

    @Path("/{organizationId}/app-users")
    @POST
    @Transactional
    fun addAppUser(
        @PathParam("organizationId") organizationId: String,
        addOrganizationAppUserRequest: AddOrganizationAppUserRequest
    ): Response
    {
        return try
        {
            val appUser = with(addOrganizationAppUserRequest) {

                organizationAppUserService.addAppUser(
                    organizationId,
                    role,
                    email,
                    person?.firstName,
                    person?.lastName
                )
            }

            return Response
                .status(CREATED)
                .entity(EntityToDtoTransformer.toDto(appUser))
                .build()
        }
        catch (exception: Exception)
        {
            logger.error("Error adding organization app user", exception)

            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response
                        .status(BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while adding an organization app user")

                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @GET
    @Path("/{organizationId}/app-users")
    fun getAppUsers(organizationId: String?): Response
    {
        return try
        {
            val appUsers = organizationAppUserService.getAppUsers(organizationId)
                .map { EntityToDtoTransformer.toDto(it) }
                .toTypedArray()

            Response
                .ok(appUsers)
                .build()
        }
        catch (exception: Exception)
        {
            logger.error("Error getting organization app users", exception)

            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response
                        .status(NOT_FOUND)
                        .entity(responseError)
                        .build()
                }

                is IllegalArgumentException ->
                {
                    val responseError = ResponseError(exception.message)

                    Response
                        .status(BAD_REQUEST)
                        .entity(responseError)
                        .build()
                }

                else ->
                {
                    val responseError = ResponseError("An error occurred while getting organization app users")

                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}