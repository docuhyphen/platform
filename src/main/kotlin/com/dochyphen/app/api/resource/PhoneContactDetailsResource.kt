package com.dochyphen.app.api.resource

import com.dochyphen.app.api.resource.model.CompletePhoneNumberRequest
import com.dochyphen.app.api.resource.model.InitiatePhoneNumberRequest
import com.dochyphen.app.api.service.PhoneContactDetailsService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Path("/contact-details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class PhoneContactDetailsResource @Inject constructor(
    private val phoneContactDetailsService: PhoneContactDetailsService
) : BaseResource()
{
    override val logger: Logger
        get() = LoggerFactory.getLogger(PhoneContactDetailsResource::class.java)

    @POST
    @Path("/{contactDetailsId}/phone/addition-initiation")
    fun initiatePhoneNumberAddition(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: InitiatePhoneNumberRequest
    ): Response
    {
        return try
        {
            phoneContactDetailsService.initiatePhoneNumberAddition(
                contactDetailsId,
                request.phoneNumber
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error initiating phone number addition", exception)
            handleException(exception, "An error occurred while initiating phone number addition")
        }
    }

    @POST
    @Path("/{contactDetailsId}/phone/addition-completion")
    fun completePhoneNumberAddition(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: CompletePhoneNumberRequest
    ): Response
    {
        return try
        {
            phoneContactDetailsService.completePhoneNumberAddition(
                contactDetailsId,
                request.phoneNumber,
                request.verificationCode
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error completing phone number addition", exception)
            handleException(exception, "An error occurred while completing phone number addition")
        }
    }

    @POST
    @Path("/{contactDetailsId}/phone/initiate-update")
    fun initiatePhoneNumberUpdate(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: InitiatePhoneNumberRequest
    ): Response
    {
        return try
        {
            phoneContactDetailsService.initiatePhoneNumberUpdate(
                contactDetailsId,
                request.phoneNumber
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error initiating phone number update", exception)
            handleException(exception, "An error occurred while initiating phone number update")
        }
    }

    @POST
    @Path("/{contactDetailsId}/phone/complete-update")
    fun completePhoneNumberUpdate(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: CompletePhoneNumberRequest
    ): Response
    {
        return try
        {
            phoneContactDetailsService.completePhoneNumberUpdate(
                contactDetailsId,
                request.phoneNumber,
                request.verificationCode
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error completing phone number update", exception)
            handleException(exception, "An error occurred while completing phone number update")
        }
    }
}