package com.docuhyphen.app.api.resource.informationrequest.externalsource.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.RequestInformationRequestConnectorExchangeRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/connector-exchanges")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestConnectorExchangeResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @POST
    fun request(
        @PathParam("id") id: String,
        request: RequestInformationRequestConnectorExchangeRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
