package com.docuhyphen.app.api.resource.informationrequest.externalsource.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.DecideInformationRequestImportedValueRequest
import com.docuhyphen.app.api.resource.model.ProposeInformationRequestImportedValueRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/imported-values")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestImportedValueResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @POST
    fun propose(
        @PathParam("id") id: String,
        request: ProposeInformationRequestImportedValueRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{valueId}/decisions")
    fun decide(
        @PathParam("id") id: String,
        @PathParam("valueId") valueId: String,
        request: DecideInformationRequestImportedValueRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
