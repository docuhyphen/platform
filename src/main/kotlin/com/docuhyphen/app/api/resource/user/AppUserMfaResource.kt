package com.docuhyphen.app.api.resource.user

import com.docuhyphen.app.api.resource.model.AuthenticatorEnrollmentRequest
import com.docuhyphen.app.api.resource.model.AuthenticatorEnrollmentVerificationRequest
import com.docuhyphen.app.api.resource.model.MfaConfigurationUpdateRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.user.operations.AppUserMfaResourceOperations
import com.docuhyphen.app.api.service.user.AppUserMfaService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class AppUserMfaResource @Inject constructor(
    private val appUserMfaService: AppUserMfaService,
) : AppUserMfaResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AppUserMfaResource::class.java)
    }

    override fun getConfiguration(): Response
    {
        return try
        {
            Response.ok(appUserMfaService.getConfiguration()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error fetching MFA configuration", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while fetching the MFA configuration."))
                        .build()
                }
            }
        }
    }

    override fun startEnrollment(request: AuthenticatorEnrollmentRequest): Response
    {
        return try
        {
            Response.status(CREATED).entity(appUserMfaService.startEnrollment(request.provider)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error starting authenticator enrollment", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while starting the authenticator enrollment."))
                        .build()
                }
            }
        }
    }

    override fun verifyEnrollment(
        enrollmentId: String,
        request: AuthenticatorEnrollmentVerificationRequest,
    ): Response
    {
        return try
        {
            Response.ok(
                appUserMfaService.completeEnrollment(enrollmentId, request.code, request.emailFallbackEnabled),
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error verifying authenticator enrollment", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while verifying the authenticator enrollment."))
                        .build()
                }
            }
        }
    }

    override fun updateConfiguration(request: MfaConfigurationUpdateRequest): Response
    {
        return try
        {
            Response.ok(appUserMfaService.updateEmailFallback(request.emailFallbackEnabled)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating MFA configuration", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while updating the MFA configuration."))
                        .build()
                }
            }
        }
    }

    override fun removeAuthenticator(): Response
    {
        return try
        {
            Response.ok(appUserMfaService.removeAuthenticator()).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is UnauthorizedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error removing authenticator", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while removing the authenticator."))
                        .build()
                }
            }
        }
    }
}
