package com.docuhyphen.app.api.resource.contactdetails

import com.docuhyphen.app.api.resource.contactdetails.operations.EmailContactDetailsResourceOperations
import com.docuhyphen.app.api.resource.model.CompleteAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.InitiateAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.contactdetails.EmailContactDetailsService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class EmailContactDetailsResource @Inject constructor(
    private val emailContactDetailsService: EmailContactDetailsService,
) : EmailContactDetailsResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(EmailContactDetailsResource::class.java)
    }

    override fun initiateEmailAddition(
        contactDetailsId: String,
        request: InitiateAddOrUpdateEmailRequest,
    ): Response
    {
        return try
        {
            emailContactDetailsService.initiateEmailAddition(contactDetailsId, request.email)
            Response.ok().build()
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
                    logger.error("Error initiating email addition", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while initiating email addition"))
                        .build()
                }
            }
        }
    }

    override fun completeEmailAddition(
        contactDetailsId: String,
        request: CompleteAddOrUpdateEmailRequest,
    ): Response
    {
        return try
        {
            emailContactDetailsService.completeEmailAddition(contactDetailsId, request.email, request.verificationCode)
            Response.ok().build()
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
                    logger.error("Error completing email addition", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while completing email addition"))
                        .build()
                }
            }
        }
    }

    override fun initiateEmailUpdate(
        contactDetailsId: String,
        request: InitiateAddOrUpdateEmailRequest,
    ): Response
    {
        return try
        {
            emailContactDetailsService.initiateEmailUpdate(contactDetailsId, request.email)
            Response.ok().build()
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
                    logger.error("Error initiating email update", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while initiating email update"))
                        .build()
                }
            }
        }
    }

    override fun completeEmailUpdate(
        contactDetailsId: String,
        request: CompleteAddOrUpdateEmailRequest,
    ): Response
    {
        return try
        {
            emailContactDetailsService.completeEmailUpdate(contactDetailsId, request.email, request.verificationCode)
            Response.ok().build()
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
                    logger.error("Error completing email update", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while completing email update"))
                        .build()
                }
            }
        }
    }
}
