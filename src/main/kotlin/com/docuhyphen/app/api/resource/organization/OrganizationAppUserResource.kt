package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.resource.model.AddOrganizationAppUserRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.model.UpdateOrganizationAppUserRequest
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.organization.OrganizationAppUserService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.inject.Inject
import java.util.UUID
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
    private val organizationAppUserService: OrganizationAppUserService,
    private val organizationMembershipService: OrganizationMembershipService,
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
        return try
        {
            val appUser = with(addOrganizationAppUserRequest) {
                val adminApprovalContext = AdminApprovalContext(
                    requestId = requestId,
                )

                organizationAppUserService.addAppUser(
                    organizationId,
                    roles,
                    email,
                    person?.firstName,
                    person?.lastName,
                    adminApprovalContext,
                )
            }

            return Response
                .status(CREATED)
                .entity(DetailedEntityToDtoTransformer.toPublicDto(appUser))
                .build()
        }
        catch (exception: SubscriptionDenialException)
        {
            logger.error("Subscription denied while adding an organization app user", exception)
            throw exception
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
        return try
        {
            val members = organizationAppUserService.getAppUsers(organizationId)
            // organizationId is a valid org UUID here (getAppUsers validates/resolves it first).
            val roles = organizationMembershipService.rolesOf(UUID.fromString(organizationId))
            val appUsers = members
                .map { DetailedEntityToDtoTransformer.toPublicDto(it, roles[it.id].orEmpty()) }
                .toTypedArray()

            Response
                .ok(appUsers)
                .build()
        }
        catch (exception: SubscriptionDenialException)
        {
            logger.error("Subscription denied while updating an organization app user", exception)
            throw exception
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
                    rolesToAdd,
                    rolesToRemove,
                    isActive,
                    email,
                    this.person?.firstName,
                    this.person?.lastName,
                    adminApprovalContext,
                )
            }

            Response.status(NO_CONTENT).build()
        }
        catch (exception: SubscriptionDenialException)
        {
            logger.warn("Subscription denied while changing an organization app user", exception)
            throw exception
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
        catch (exception: SubscriptionDenialException)
        {
            logger.warn("Subscription denied while removing an organization app user", exception)
            throw exception
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
