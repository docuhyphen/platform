package com.docuhyphen.app.api.resource.contactdetails

import com.docuhyphen.app.api.resource.contactdetails.operations.PhoneContactDetailsResourceOperations
import com.docuhyphen.app.api.resource.model.CompleteAddOrUpdatePhoneNumberRequest
import com.docuhyphen.app.api.resource.model.InitiateAddOrUpdatePhoneNumberRequest
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.contactdetails.PhoneContactDetailsService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.*
import org.slf4j.LoggerFactory

class PhoneContactDetailsResource @Inject constructor(
    private val phoneContactDetailsService: PhoneContactDetailsService,
) : PhoneContactDetailsResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(PhoneContactDetailsResource::class.java)
    }

    override fun initiatePhoneNumberAddition(
        contactDetailsId: String,
        request: InitiateAddOrUpdatePhoneNumberRequest,
    ): Response
    {
        return try
        {
            phoneContactDetailsService.initiatePhoneNumberAddition(contactDetailsId, request.phoneNumber)
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
                    logger.error("Error initiating phone number addition", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while initiating phone number addition"))
                        .build()
                }
            }
        }
    }

    override fun completePhoneNumberAddition(
        contactDetailsId: String,
        request: CompleteAddOrUpdatePhoneNumberRequest,
    ): Response
    {
        return try
        {
            phoneContactDetailsService.completePhoneNumberAddition(
                contactDetailsId,
                request.phoneNumber,
                request.verificationCode,
            )
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
                    logger.error("Error completing phone number addition", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while completing phone number addition"))
                        .build()
                }
            }
        }
    }

    override fun initiatePhoneNumberUpdate(
        contactDetailsId: String,
        request: InitiateAddOrUpdatePhoneNumberRequest,
    ): Response
    {
        return try
        {
            phoneContactDetailsService.initiatePhoneNumberUpdate(contactDetailsId, request.phoneNumber)
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
                    logger.error("Error initiating phone number update", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while initiating phone number update"))
                        .build()
                }
            }
        }
    }

    override fun completePhoneNumberUpdate(
        contactDetailsId: String,
        request: CompleteAddOrUpdatePhoneNumberRequest,
    ): Response
    {
        return try
        {
            phoneContactDetailsService.completePhoneNumberUpdate(
                contactDetailsId,
                request.phoneNumber,
                request.verificationCode,
            )
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
                    logger.error("Error completing phone number update", exception)
                    Response.status(INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("An error occurred while completing phone number update"))
                        .build()
                }
            }
        }
    }
}
