package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestBusinessDecisionService
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/accepted-fact-offers")
@Produces(APPLICATION_JSON)
class InformationRequestAcceptedFactOfferResource @Inject constructor(
    facts: InformationRequestAcceptedFactService,
    factQueries: InformationRequestAcceptedFactQueryService,
    decisions: InformationRequestBusinessDecisionService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestAcceptedFactEndpoint(facts, factQueries, decisions)

    @GET
    fun offers(@PathParam("id") id: String): Response
    {
        return try
        {
            endpoint.offers(InformationRequestCommandHttp.uuid(id, "information request id"), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request accepted fact offer lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAcceptedFactOfferResource::class.java)
    }
}
