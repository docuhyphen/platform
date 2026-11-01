package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.user.operations.AppUserAvatarResourceOperations
import com.docuhyphen.app.api.service.user.AppUserAvatarService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

class AppUserAvatarResource @Inject constructor(
    private val appUserAvatarService: AppUserAvatarService,
) : AppUserAvatarResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppUserAvatarResource::class.java)
    }

    override fun uploadAvatar(
        file: File?,
        extension: String?,
    ): Response
    {
        return try
        {
            if (file == null)
            {
                return Response.status(BAD_REQUEST).entity(ResponseError("Image file is required")).build()
            }

            appUserAvatarService.uploadAvatar(file, extension)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error uploading user avatar", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while uploading the profile picture."))
                        .build()
                }
            }
        }
    }

    override fun getAvatar(): Response
    {
        return try
        {
            val avatar = appUserAvatarService.getAvatar()
                ?: return Response.status(NOT_FOUND).build()

            Response.ok(avatar.bytes)
                .header(HttpHeaders.CONTENT_TYPE, avatar.contentType)
                .header(HttpHeaders.CONTENT_LENGTH, avatar.bytes.size)
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching user avatar", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while fetching the profile picture."))
                        .build()
                }
            }
        }
    }

    override fun getUserAvatar(id: UUID): Response
    {
        return try
        {
            val avatar = appUserAvatarService.getAvatar(id)
                ?: return Response.status(NOT_FOUND).build()

            Response.ok(avatar.bytes)
                .header(HttpHeaders.CONTENT_TYPE, avatar.contentType)
                .header(HttpHeaders.CONTENT_LENGTH, avatar.bytes.size)
                .build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching avatar for user {}", id, exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while fetching the profile picture."))
                        .build()
                }
            }
        }
    }

    override fun deleteAvatar(): Response
    {
        return try
        {
            appUserAvatarService.deleteAvatar()
            Response.noContent().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error deleting user avatar", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while removing the profile picture."))
                        .build()
                }
            }
        }
    }
}
