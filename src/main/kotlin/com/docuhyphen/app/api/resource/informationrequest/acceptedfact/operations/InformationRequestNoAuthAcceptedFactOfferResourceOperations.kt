package com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response

@Path("no-auth/information-requests/{id}/accepted-fact-offers")
@Produces(APPLICATION_JSON)
interface InformationRequestNoAuthAcceptedFactOfferResourceOperations
{
    @GET
    fun offers(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
}
