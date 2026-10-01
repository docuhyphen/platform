package com.docuhyphen.app.api.resource.informationrequest.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.CancelInformationRequestRequest
import com.docuhyphen.app.api.resource.model.CreateInformationRequestDraftRequest
import com.docuhyphen.app.api.resource.model.SupersedeInformationRequestRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestResourceOperations
{
    @GET
    fun list(@QueryParam("exchangeId") exchangeIdParam: String?): Response

    @GET
    @Path("/{id}")
    fun get(@PathParam("id") id: String): Response

    @GET
    @Path("/{id}/response-workspace")
    fun responseWorkspace(@PathParam("id") id: String): Response

    @POST
    fun create(
        request: CreateInformationRequestDraftRequest,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{id}/issuance")
    fun issue(
        @PathParam("id") id: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{id}/cancellation")
    fun cancel(
        @PathParam("id") id: String,
        request: CancelInformationRequestRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{id}/supersession")
    fun supersede(
        @PathParam("id") id: String,
        request: SupersedeInformationRequestRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
