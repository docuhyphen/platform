package com.docuhyphen.app.api.resource.contactdetails.operations

import com.docuhyphen.app.api.resource.model.CompleteAddOrUpdatePhoneNumberRequest
import com.docuhyphen.app.api.resource.model.InitiateAddOrUpdatePhoneNumberRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/contact-details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface PhoneContactDetailsResourceOperations {
    @POST
    @Path("/{contactDetailsId}/phone/addition-initiation")
    fun initiatePhoneNumberAddition(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: InitiateAddOrUpdatePhoneNumberRequest,
    ): Response

    @POST
    @Path("/{contactDetailsId}/phone/addition-completion")
    fun completePhoneNumberAddition(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: CompleteAddOrUpdatePhoneNumberRequest,
    ): Response

    @POST
    @Path("/{contactDetailsId}/phone/initiate-update")
    fun initiatePhoneNumberUpdate(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: InitiateAddOrUpdatePhoneNumberRequest,
    ): Response

    @POST
    @Path("/{contactDetailsId}/phone/complete-update")
    fun completePhoneNumberUpdate(
        @PathParam("contactDetailsId") contactDetailsId: String,
        request: CompleteAddOrUpdatePhoneNumberRequest,
    ): Response
}
