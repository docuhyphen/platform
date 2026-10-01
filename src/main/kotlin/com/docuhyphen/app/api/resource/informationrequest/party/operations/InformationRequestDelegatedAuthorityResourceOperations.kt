package com.docuhyphen.app.api.resource.informationrequest.party.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.GrantInformationRequestDelegatedAuthorityRequest
import com.docuhyphen.app.api.resource.model.RevokeInformationRequestDelegatedAuthorityRequest
import jakarta.ws.rs.*
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("/information-requests/{id}/delegated-authorities")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestDelegatedAuthorityResourceOperations
{
    @POST
    fun grant(
        @PathParam("id") id: String,
        request: GrantInformationRequestDelegatedAuthorityRequest,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response

    @POST
    @Path("/{authorityId}/revocations")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("authorityId") authorityId: String,
        request: RevokeInformationRequestDelegatedAuthorityRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
}
