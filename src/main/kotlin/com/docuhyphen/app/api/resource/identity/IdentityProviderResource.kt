package com.docuhyphen.app.api.resource.identity

import com.docuhyphen.app.api.resource.identity.operations.IdentityProviderResourceOperations
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.auth.ExternalProviderAlreadyLinkedException
import com.docuhyphen.app.api.service.identity.CurrentPasswordRejectedException
import com.docuhyphen.app.api.service.identity.IdentityProviderRequestException
import com.docuhyphen.app.api.service.identity.ReauthenticationRequiredException
import com.docuhyphen.app.api.service.identity.UserIdentityProviderService
import jakarta.inject.Inject
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class IdentityProviderResource @Inject constructor(
    private val userIdentityProviderService: UserIdentityProviderService,
) : IdentityProviderResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(IdentityProviderResource::class.java)
        private const val STEP_UP_REQUIRED_REASON_CODE = "STEP_UP_REQUIRED"
    }

    override fun getLinkedProviders(): Response
    {
        return try
        {
            val dtos = userIdentityProviderService.linkedProviders()
            Response.ok(object : GenericEntity<List<IdentityProviderLinkDto>>(dtos)
            {}).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching identity providers", exception)
            Response.status(INTERNAL_SERVER_ERROR)
                .entity(ResponseError("Failed to fetch identity providers"))
                .build()
        }
    }

    override fun initiateLinking(payload: LinkProviderInitiateRequest): Response
    {
        return try
        {
            val authUrl = userIdentityProviderService.initiateLinking(payload.provider)
            Response.ok(LinkProviderInitiateResponse(authUrl)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IdentityProviderRequestException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ExternalProviderAlreadyLinkedException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error initiating provider linking", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to initiate provider linking"))
                        .build()
                }
            }
        }
    }

    override fun unlinkProvider(providerName: String): Response
    {
        return try
        {
            userIdentityProviderService.unlinkProvider(providerName)
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is ReauthenticationRequiredException ->
                    Response.status(UNAUTHORIZED)
                        .entity(ResponseError(exception.message, reasonCode = STEP_UP_REQUIRED_REASON_CODE))
                        .build()

                is IllegalStateException ->
                    Response.status(CONFLICT).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error unlinking provider $providerName", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to unlink provider"))
                        .build()
                }
            }
        }
    }

    override fun setupPassword(payload: SetupPasswordRequest): Response
    {
        return try
        {
            userIdentityProviderService.setupPassword(
                password = payload.password,
                confirmationPassword = payload.confirmationPassword,
                currentPassword = payload.currentPassword,
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is IdentityProviderRequestException ->
                    Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is ReauthenticationRequiredException ->
                    Response.status(UNAUTHORIZED)
                        .entity(ResponseError(exception.message, reasonCode = STEP_UP_REQUIRED_REASON_CODE))
                        .build()

                is CurrentPasswordRejectedException ->
                    Response.status(UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error setting up password", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Failed to set up password"))
                        .build()
                }
            }
        }
    }
}
