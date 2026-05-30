package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.model.AddOrganizationAppUserRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateOrganizationAppUserRequest
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.organization.OrganizationAppUserService
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory


enum class APP_USER_CHECK
{
    DELETABLE
}

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
        @HeaderParam("X-Request-Id") requestId: String?,
        addOrganizationAppUserRequest: AddOrganizationAppUserRequest
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            val appUser = with(addOrganizationAppUserRequest) {
                val adminApprovalContext = AdminApprovalContext(
                    requestId = requestId,
                )

                organizationAppUserService.addAppUser(
                    organizationId,
                    role,
                    email,
                    person?.firstName,
                    person?.lastName,
                    adminApprovalContext,
                )
            }

            return Response
                .status(CREATED)
                .entity(DetailedEntityToDtoTransformer.toDto(appUser))
                .build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

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
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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
        @HeaderParam("X-Request-Id") requestId: String?,
        updateOrganizationAppUserRequest: UpdateOrganizationAppUserRequest
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            with(updateOrganizationAppUserRequest)
            {
                val adminApprovalContext = AdminApprovalContext(
                    requestId = requestId,
                )
                organizationAppUserService.updateAppUser(
                    organizationId,
                    appUserId,
                    role,
                    isActive,
                    email,
                    this.person?.firstName,
                    this.person?.lastName,
                    adminApprovalContext,
                )
            }

            Response.status(NO_CONTENT).build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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
    fun deleteAppUser(
        @PathParam("organizationId") organizationId: String?,
        @PathParam("appUserId") appUserId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            val adminApprovalContext = AdminApprovalContext(
                requestId = requestId,
            )
            organizationAppUserService.deleteAppUser(organizationId, appUserId, adminApprovalContext)

            Response
                .status(NO_CONTENT)
                .build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
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

    @Path("/{organizationId}/app-users/{appUserId}")
    @GET
    fun doCheck(
        @QueryParam("check") check: String?,
        @PathParam("organizationId") organizationId: String?,
        @PathParam("appUserId") appUserId: String?
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            var response = Response.ok().build()

            if (check.isNullOrBlank())
            {
                //toDo: return organiation app user
            }
            else
            {
                when (APP_USER_CHECK.valueOf(check.uppercase()))
                {
                    APP_USER_CHECK.DELETABLE ->
                    {
                        if(!organizationAppUserService.isAppUserIsDeletable(organizationId, appUserId))
                        {
                            response = Response.status(CONFLICT).build()
                        }
                    }
                }
            }

            response
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
            logger.error("Error checking organization app user", exception)
            val responseError = ResponseError("An error occurred while checking organization app user.")
            Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
        }
    }
}
