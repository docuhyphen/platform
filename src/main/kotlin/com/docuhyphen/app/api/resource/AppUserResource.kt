package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.DataIntegrityException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.AppUserSettingsDto
import com.docuhyphen.app.api.model.dto.PersonBasicDto
import com.docuhyphen.app.api.resource.model.CompleteAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.InitiateAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory
import java.util.*

@Path("/app-user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AppUserResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val organizationGroupService: OrganizationGroupService,
    private val appUserService: AppUserService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppUserResource::class.java)
    }

    @GET
    fun getSignInAppUser(): Response
    {
        return try
        {
            authTokenContext.authToken.appUser?.let {

                Response.ok(DetailedEntityToDtoTransformer.toDto(it)).build()

            } ?: Response.status(BAD_REQUEST).entity(ResponseError("No user found")).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error initiating sign up", exception)
            val responseError = ResponseError("A server error occurred while signing up.")
            Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
        }
    }

    @GET
    @Path("{appUserId}/person/{personId}/organization")
    fun getAppUserPersonOrganization(
        @PathParam("appUserId") appUserId: String,
        @PathParam("personId") personId: String
    ): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(300, 600)

        return try
        {
            val organization =
                organizationGroupService.getOrganizationByAppUserIdAndPersonId(
                    UUID.fromString(appUserId),
                    UUID.fromString(personId)
                )

            Response.ok(DetailedEntityToDtoTransformer.toDto(organization)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OrganizationNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error fetching organization", exception)
                    val responseError = ResponseError("A server error occurred while fetching the organization.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    /**
     * Get the settings of the currently logged in appUser
     */
    @PUT
    @Path("/settings")
    fun updateCurrentUserSettings(settingsDto: AppUserSettingsDto): Response
    {
        return try
        {
            appUserService.updateSettings(null, settingsDto)
            Response.ok(settingsDto).build()
        }
        catch (exception: Exception)
        {
            handleSettingsUpdateException(exception)
        }
    }

    /**
     * Update user settings for a specific user
     */
    @PUT
    @Path("/{userId}/settings")
    fun updateUserSettings(
        @PathParam("userId") userId: String,
        settingsDto: AppUserSettingsDto
    ): Response
    {
        return try
        {
            appUserService.updateSettings(userId, settingsDto)
            Response.ok(settingsDto).build()
        }
        catch (exception: Exception)
        {
            handleSettingsUpdateException(exception)
        }
    }

    /**
     * Update current logged in user person
     */
    @PUT
    @Path("/person")
    @Transactional
    fun updatePerson(personDto: PersonBasicDto): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1500, 2500)

        return try
        {
            appUserService.updatePerson(authTokenContext.authToken.appUser!!, personDto)

            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                is AppUserNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error updating user person", exception)
                    val responseError = ResponseError("A server error occurred while updating user person.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    /**
     * Initiate email update of the currently logged in appUser
    * */
    @PUT
    @Path("email/update-initiation")
    @Transactional
    fun initiateEmailUpdate(addOrUpdateEmailRequest: InitiateAddOrUpdateEmailRequest): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1500, 2500)

        return try
        {
            appUserService.initiateEmailUpdate(addOrUpdateEmailRequest.email)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                is AppUserNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error updating user email", exception)
                    val responseError = ResponseError("A server error occurred while updating user email.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    /**
     * Complete email update of the currently logged in appUser
     * */
    @POST
    @Path("email/update-completion")
    @Transactional
    fun completeEmailUpdate(request: CompleteAddOrUpdateEmailRequest): Response
    {
        ResourceEndpointDelayHelper.delayEndpoint(1500, 2500)

        return try
        {
            appUserService.completeEmailUpdate(request.email, request.verificationCode)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
                }

                is AppUserNotFoundException ->
                {
                    val responseError = ResponseError(exception.message)
                    Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
                }

                else ->
                {
                    logger.error("Error completing email update", exception)
                    val responseError = ResponseError("A server error occurred while completing email update.")
                    Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
                }
            }
        }
    }

    private fun handleSettingsUpdateException(exception: Exception): Response
    {
        logger.error("Error updating user settings", exception)
        return when (exception)
        {
            is UnauthorizedException ->
            {
                val responseError = ResponseError(exception.message ?: "Not authorized to update settings")
                Response.status(Response.Status.FORBIDDEN).entity(responseError).build()
            }

            is AppUserNotFoundException ->
            {
                val responseError = ResponseError(exception.message ?: "User not found")
                Response.status(Response.Status.NOT_FOUND).entity(responseError).build()
            }

            is DataIntegrityException ->
            {
                val responseError = ResponseError(exception.message ?: "Invalid data format")
                Response.status(BAD_REQUEST).entity(responseError).build()
            }

            else ->
            {
                val responseError = ResponseError("Server error while updating settings")
                Response.status(INTERNAL_SERVER_ERROR).entity(responseError).build()
            }
        }
    }
}