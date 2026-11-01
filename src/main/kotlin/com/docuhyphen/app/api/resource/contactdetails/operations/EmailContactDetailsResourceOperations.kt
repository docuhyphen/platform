package com.docuhyphen.app.api.resource.contactdetails.operations

import com.docuhyphen.app.api.resource.model.CompleteAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.InitiateAddOrUpdateEmailRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/contact-details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface EmailContactDetailsResourceOperations {
    @POST
    @Path("/{contactDetailsId}/email/addition-initiation")
    fun initiateEmailAddition(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: InitiateAddOrUpdateEmailRequest,
    ): Response

    @POST
    @Path("/{contactDetailsId}/email/addition-completion")
    fun completeEmailAddition(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: CompleteAddOrUpdateEmailRequest,
    ): Response

    @POST
    @Path("/{contactDetailsId}/email/initiate-update")
    fun initiateEmailUpdate(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: InitiateAddOrUpdateEmailRequest,
    ): Response

    @POST
    @Path("/{contactDetailsId}/email/complete-update")
    fun completeEmailUpdate(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: CompleteAddOrUpdateEmailRequest,
    ): Response
}
