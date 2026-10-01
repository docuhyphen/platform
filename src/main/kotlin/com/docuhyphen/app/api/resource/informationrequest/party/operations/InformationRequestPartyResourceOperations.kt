package com.docuhyphen.app.api.resource.informationrequest.party.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.model.AssignInformationRequestPartyRequest
import com.docuhyphen.app.api.resource.model.ReassignInformationRequestPartyRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/parties")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestPartyResourceOperations
{
    @GET
    fun list(@PathParam("id") id: String): Response

    @POST
    fun assign(
        @PathParam("id") id: String,
        request: AssignInformationRequestPartyRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{partyId}/reassignment")
    fun reassign(
        @PathParam("id") id: String,
        @PathParam("partyId") partyId: String,
        request: ReassignInformationRequestPartyRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{partyId}/revocation")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("partyId") partyId: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
