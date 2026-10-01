package com.docuhyphen.app.api.resource.informationrequest.noauth.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.model.VerifyInformationRequestContactProofRequest
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("no-auth/information-request-access-links")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
interface InformationRequestNoAuthAccessResourceOperations
{
    @POST
    @Path("/challenges")
    fun issueContactProofChallenge(
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
    ): Response

    @POST
    @Path("/sessions")
    fun verifyContactProofChallenge(
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        request: VerifyInformationRequestContactProofRequest,
    ): Response
}
