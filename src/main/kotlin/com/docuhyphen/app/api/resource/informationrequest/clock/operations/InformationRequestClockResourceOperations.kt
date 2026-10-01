package com.docuhyphen.app.api.resource.informationrequest.clock.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestClockRequest
import com.docuhyphen.app.api.resource.model.StartInformationRequestClockRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/clocks")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestClockResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @POST
    fun start(
        @PathParam("id") id: String,
        request: StartInformationRequestClockRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{clockId}/pauses")
    fun pause(
        @PathParam("id") id: String,
        @PathParam("clockId") clockId: String,
        request: ChangeInformationRequestClockRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{clockId}/resumptions")
    fun resume(
        @PathParam("id") id: String,
        @PathParam("clockId") clockId: String,
        request: ChangeInformationRequestClockRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{clockId}/extensions")
    fun extend(
        @PathParam("id") id: String,
        @PathParam("clockId") clockId: String,
        request: ChangeInformationRequestClockRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
