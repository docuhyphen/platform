package com.docuhyphen.app.api.resource.informationrequest.occurrence.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/group-occurrences")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestGroupOccurrenceResourceOperations
{
    @POST
    fun add(
        @PathParam("id") id: String,
        request: CreateInformationRequestGroupOccurrenceRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @DELETE
    @Path("/{occurrenceId}")
    fun remove(
        @PathParam("id") id: String,
        @PathParam("occurrenceId") occurrenceId: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @PATCH
    @Path("/order")
    fun reorder(
        @PathParam("id") id: String,
        request: ReorderInformationRequestGroupOccurrencesRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
