package com.docuhyphen.app.api.resource.informationrequest.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.CreateInformationRequestGroupOccurrenceRequest
import com.docuhyphen.app.api.resource.model.PatchInformationRequestResponsesRequest
import com.docuhyphen.app.api.resource.model.ReorderInformationRequestGroupOccurrencesRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("no-auth/information-requests/{id}")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestNoAuthRequestResourceOperations
{
    @GET
    fun get(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response

    @GET
    @Path("/parties")
    fun parties(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response

    @GET
    @Path("/response-workspace")
    fun responseWorkspace(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response

    @PATCH
    @Path("/responses")
    fun patchResponses(
        @PathParam("id") id: String,
        request: PatchInformationRequestResponsesRequest,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response

    @POST
    @Path("/group-occurrences")
    fun addGroupOccurrence(
        @PathParam("id") id: String,
        request: CreateInformationRequestGroupOccurrenceRequest,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response

    @DELETE
    @Path("/group-occurrences/{occurrenceId}")
    fun removeGroupOccurrence(
        @PathParam("id") id: String,
        @PathParam("occurrenceId") occurrenceId: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response

    @PATCH
    @Path("/group-occurrences/order")
    fun reorderGroupOccurrences(
        @PathParam("id") id: String,
        request: ReorderInformationRequestGroupOccurrencesRequest,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
        @HeaderParam("X-Request-Session-Token") sessionToken: String? = null,
    ): Response
}
