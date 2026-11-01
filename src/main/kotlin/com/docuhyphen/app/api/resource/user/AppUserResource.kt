package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.exception.DataIntegrityException
import com.docuhyphen.app.api.exception.OrganizationNotFoundException
import com.docuhyphen.app.api.model.DetailedEntityToDtoTransformer
import com.docuhyphen.app.api.model.dto.AppUserSettingsDto
import com.docuhyphen.app.api.model.dto.PersonBasicDto
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.user.operations.AppUserResourceOperations
import com.docuhyphen.app.api.service.auth.SessionService
import com.docuhyphen.app.api.service.organization.OrganizationGroupService
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.user.SignedInAppUserService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.util.*

class AppUserResource @Inject constructor(
    private val signedInAppUserService: SignedInAppUserService,
    private val organizationGroupService: OrganizationGroupService,
    private val appUserService: AppUserService,
    private val sessionService: SessionService,
) : AppUserResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppUserResource::class.java)
    }

    override fun getSignInAppUser(): Response
    {
        return try
        {
            signedInAppUserService.currentUserDetails()
                ?.let { Response.ok(it).build() }
                ?: Response.status(BAD_REQUEST).entity(ResponseError("No user found")).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching signed-in app user", exception)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("A server error occurred while signing up."))
                .build()
        }
    }

    override fun getCurrentSession(): Response
    {
        return try
        {
            Response.ok(sessionService.currentSession()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching current session", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while fetching the session."))
                        .build()
                }
            }
        }
    }

    override fun getAppUserPersonOrganization(
        appUserId: String,
        personId: String,
    ): Response
    {
        return try
        {
            val organization = organizationGroupService.getOrganizationByAppUserIdAndPersonId(
                UUID.fromString(appUserId),
                UUID.fromString(personId),
            )

            Response.ok(DetailedEntityToDtoTransformer.toDto(organization)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is OrganizationNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching organization", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while fetching the organization."))
                        .build()
                }
            }
        }
    }

    override fun updateCurrentUserSettings(settingsDto: AppUserSettingsDto): Response
    {
        return try
        {
            appUserService.updateSettings(null, settingsDto)
            Response.ok(settingsDto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN)
                        .entity(ResponseError(exception.message ?: "Not authorized to update settings"))
                        .build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND)
                        .entity(ResponseError(exception.message ?: "User not found"))
                        .build()

                is DataIntegrityException ->
                    Response.status(BAD_REQUEST)
                        .entity(ResponseError(exception.message ?: "Invalid data format"))
                        .build()

                else ->
                {
                    logger.error("Error updating current user settings", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Server error while updating settings"))
                        .build()
                }
            }
        }
    }

    override fun updateUserSettings(
        userId: String,
        settingsDto: AppUserSettingsDto,
    ): Response
    {
        return try
        {
            appUserService.updateSettings(userId, settingsDto)
            Response.ok(settingsDto).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN)
                        .entity(ResponseError(exception.message ?: "Not authorized to update settings"))
                        .build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND)
                        .entity(ResponseError(exception.message ?: "User not found"))
                        .build()

                is DataIntegrityException ->
                    Response.status(BAD_REQUEST)
                        .entity(ResponseError(exception.message ?: "Invalid data format"))
                        .build()

                else ->
                {
                    logger.error("Error updating user settings by id", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Server error while updating settings"))
                        .build()
                }
            }
        }
    }

    override fun updatePerson(personDto: PersonBasicDto): Response
    {
        return try
        {
            appUserService.updateCurrentPerson(personDto)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating user person", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while updating user person."))
                        .build()
                }
            }
        }
    }
}
