package com.docuhyphen.app.api.resource.informationrequest.noauth.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.IssueInformationRequestAccessLinkRequest
import com.docuhyphen.app.api.resource.model.ReplaceInformationRequestAccessLinkRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/access-links")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestAccessLinkResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @POST
    fun issue(
        @PathParam("id") id: String,
        request: IssueInformationRequestAccessLinkRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{shareLinkId}/rotation")
    fun rotate(
        @PathParam("id") id: String,
        @PathParam("shareLinkId") shareLinkId: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{shareLinkId}/replacement")
    fun replace(
        @PathParam("id") id: String,
        @PathParam("shareLinkId") shareLinkId: String,
        request: ReplaceInformationRequestAccessLinkRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{shareLinkId}/revocation")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("shareLinkId") shareLinkId: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
