package com.docuhyphen.app.api.resource.user.operations

import com.docuhyphen.app.api.resource.model.CompleteAddOrUpdateEmailRequest
import com.docuhyphen.app.api.resource.model.ConfirmOldEmailForUpdateRequest
import com.docuhyphen.app.api.resource.model.InitiateAddOrUpdateEmailRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/app-user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface AppUserEmailUpdateResourceOperations {
    @PUT
    @Path("email/update-initiation")
    fun initiateEmailUpdate(addOrUpdateEmailRequest: InitiateAddOrUpdateEmailRequest): Response

    @POST
    @Path("email/update-confirm-old")
    fun confirmOldEmailForUpdate(request: ConfirmOldEmailForUpdateRequest): Response

    @POST
    @Path("email/update-completion")
    fun completeEmailUpdate(request: CompleteAddOrUpdateEmailRequest): Response
}
