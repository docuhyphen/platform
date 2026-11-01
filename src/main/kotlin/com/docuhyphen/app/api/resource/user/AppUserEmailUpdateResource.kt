package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.exception.AppUserNotFoundException
import com.docuhyphen.app.api.resource.model.CompleteAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.ConfirmOldEmailForUpdateRequest
import com.docuhyphen.app.api.resource.model.InitiateAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.user.operations.AppUserEmailUpdateResourceOperations
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class AppUserEmailUpdateResource @Inject constructor(
    private val appUserService: AppUserService,
) : AppUserEmailUpdateResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppUserEmailUpdateResource::class.java)
    }

    override fun initiateEmailUpdate(addOrUpdateEmailRequest: InitiateAddOrUpdateEmailRequest): Response
    {
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
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating user email", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while updating user email."))
                        .build()
                }
            }
        }
    }

    override fun confirmOldEmailForUpdate(request: ConfirmOldEmailForUpdateRequest): Response
    {
        return try
        {
            appUserService.confirmOldEmailForUpdate(request.verificationCode)
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
                    logger.error("Error confirming old email for update", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while confirming the current email."))
                        .build()
                }
            }
        }
    }

    override fun completeEmailUpdate(request: CompleteAddOrUpdateEmailRequest): Response
    {
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
                    Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AppUserNotFoundException ->
                    Response.status(NOT_FOUND).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error completing app user email update", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("A server error occurred while completing email update."))
                        .build()
                }
            }
        }
    }
}
