package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("no-auth/information-requests/{id}/accepted-fact-offers")
@Produces(APPLICATION_JSON)
class InformationRequestNoAuthAcceptedFactOfferResource @Inject constructor(
    facts: InformationRequestAcceptedFactService,
    factQueries: InformationRequestAcceptedFactQueryService,
    decisions: InformationRequestBusinessDecisionService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
)
{
    private val endpoint = InformationRequestAcceptedFactEndpoint(facts, factQueries, decisions)

    @GET
    fun offers(
        @PathParam("id") id: String,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.offers(requestId, access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request accepted fact offer lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthAcceptedFactOfferResource::class.java)
    }
}
