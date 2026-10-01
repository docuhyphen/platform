package com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.PromoteInformationRequestAcceptedFactRequest
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestAcceptedFactRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/accepted-facts")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestAcceptedFactResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @POST
    fun promote(
        @PathParam("id") id: String,
        request: PromoteInformationRequestAcceptedFactRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{factId}/revocation")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("factId") factId: String,
        request: RevokeInformationRequestAcceptedFactRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
