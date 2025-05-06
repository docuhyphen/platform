package com.dochyphen.app.api.resource

import com.dochyphen.app.api.exception.AppUserNotFoundException
import com.dochyphen.app.api.exception.OrganizationNotFoundException
import com.dochyphen.app.api.model.DetailedEntityToDtoTransformer
import com.dochyphen.app.api.resource.model.AddOrganizationAppUserRequest
import com.dochyphen.app.api.resource.model.ResponseError
import com.dochyphen.app.api.resource.model.UpdateOrganizationAppUserRequest
import com.dochyphen.app.api.service.organization.OrganizationAppUserService
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
                .entity(DetailedEntityToDtoTransformer.toDto(appUser))
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
    fun getAppUsers(@PathParam("organizationId") organizationId: String?): Response
    {
        return try
        {
            val appUsers = organizationAppUserService.getAppUsers(organizationId)
                .map { DetailedEntityToDtoTransformer.toDto(it) }
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

    @PUT
    @Path("/{organizationId}/app-users/{appUserId}")
    @Transactional
    fun updateAppUser(
        @PathParam("organizationId") organizationId: String?,
        @PathParam("appUserId") appUserId: String?,
        updateOrganizationAppUserRequest: UpdateOrganizationAppUserRequest
    ): Response
    {
        return try
        {
            with(updateOrganizationAppUserRequest)
            {
                organizationAppUserService.updateAppUser(
                    organizationId,
                    appUserId,
                    role,
                    isActive,
                    email,
                    this.person?.firstName,
                    this.person?.lastName,
                )
            }

            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error updating organization app user", exception)

            when (exception)
            {
                is OrganizationNotFoundException,
                is AppUserNotFoundException ->
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
                    val responseError = ResponseError("An error occurred while updating an organization app user")

                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }

    @Path("/{organizationId}/app-users/{appUserId}")
    @DELETE
    fun deactivateAppUser(@PathParam("organizationId") organizationId: String?,
                          @PathParam("appUserId") appUserId: String?): Response
    {
        return try
        {
            organizationAppUserService.deactivateAppUser(organizationId, appUserId)

            Response
                .status(NO_CONTENT)
                .build()
        }
        catch (exception: Exception)
        {
            logger.error("Error deactivating organization app user", exception)

            when (exception)
            {
                is OrganizationNotFoundException,
                is AppUserNotFoundException ->
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
                    val responseError = ResponseError("An error occurred while deactivating an organization app user")

                    Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(responseError)
                        .build()
                }
            }
        }
    }
}