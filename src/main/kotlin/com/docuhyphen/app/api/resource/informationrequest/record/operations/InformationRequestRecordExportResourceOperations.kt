package com.docuhyphen.app.api.resource.informationrequest.record.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.model.CreateInformationRequestRecordExportRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/record-exports")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestRecordExportResourceOperations
{
    @POST
    fun create(
        @PathParam("id") id: String,
        request: CreateInformationRequestRecordExportRequest?,
        @HeaderParam(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @GET
    fun list(@PathParam("id") id: String): Response

    @GET
    @Path("/{exportId}")
    fun get(@PathParam("id") id: String, @PathParam("exportId") exportId: String): Response
}
