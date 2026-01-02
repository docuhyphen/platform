package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.resource.model.CompleteAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.InitiateAddOrUpdateEmailRequest
import com.docuhyphen.app.api.service.contactdetails.EmailContactDetailsService
import jakarta.inject.Inject
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Path("/contact-details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class EmailContactDetailsResource @Inject constructor(
    private val emailContactDetailsService: EmailContactDetailsService
): BaseResource()
{
    override val logger: Logger
        get() = LoggerFactory.getLogger(EmailContactDetailsResource::class.java)

    @POST
    @Path("/{contactDetailsId}/email/addition-initiation")
    fun initiateEmailAddition(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: InitiateAddOrUpdateEmailRequest
    ): Response
    {
        return try
        {
            emailContactDetailsService.initiateEmailAddition(
                contactDetailsId,
                request.email
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error initiating email addition", exception)
            handleException(exception, "An error occurred while initiating email addition")
        }
    }

    @POST
    @Path("/{contactDetailsId}/email/addition-completion")
    fun completeEmailAddition(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: CompleteAddOrUpdateEmailRequest
    ): Response
    {
        return try
        {
            emailContactDetailsService.completeEmailAddition(
                contactDetailsId,
                request.email,
                request.verificationCode
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error completing email addition", exception)
            handleException(exception, "An error occurred while completing email addition")
        }
    }

    @POST
    @Path("/{contactDetailsId}/email/initiate-update")
    fun initiateEmailUpdate(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: InitiateAddOrUpdateEmailRequest
    ): Response
    {
        return try
        {
            emailContactDetailsService.initiateEmailUpdate(
                contactDetailsId,
                request.email
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error initiating email update", exception)
            handleException(exception, "An error occurred while initiating email update")
        }
    }

    @POST
    @Path("/{contactDetailsId}/email/complete-update")
    fun completeEmailUpdate(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: CompleteAddOrUpdateEmailRequest
    ): Response
    {
        return try
        {
            emailContactDetailsService.completeEmailUpdate(
                contactDetailsId,
                request.email,
                request.verificationCode
            )
            Response.ok().build()
        }
        catch (exception: Exception)
        {
            logger.error("Error completing email update", exception)
            handleException(exception, "An error occurred while completing email update")
        }
    }
}