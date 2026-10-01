package com.docuhyphen.app.api.resource.informationrequest.submission.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.SubmitInformationRequestPackageRequest
import com.docuhyphen.app.api.resource.model.WithdrawInformationRequestPackageRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/submissions")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestSubmissionResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @GET
    @Path("/{packageId}")
    fun detail(@PathParam("id") id: String, @PathParam("packageId") packageId: String): Response

    @POST
    fun submit(
        @PathParam("id") id: String,
        request: SubmitInformationRequestPackageRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{packageId}/withdrawal")
    fun withdraw(
        @PathParam("id") id: String,
        @PathParam("packageId") packageId: String,
        request: WithdrawInformationRequestPackageRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
