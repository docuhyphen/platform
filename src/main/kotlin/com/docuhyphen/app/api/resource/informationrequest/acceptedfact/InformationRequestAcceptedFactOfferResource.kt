package com.docuhyphen.app.api.resource.informationrequest.acceptedfact

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.handler.InformationRequestAcceptedFactRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestAcceptedFactOfferResourceOperations
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestAcceptedFactOfferResource @Inject constructor(
    facts: InformationRequestAcceptedFactService,
    factQueries: InformationRequestAcceptedFactQueryService,
    decisions: InformationRequestBusinessDecisionService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestAcceptedFactOfferResourceOperations
{
    private val handler = InformationRequestAcceptedFactRequestHandler(facts, factQueries, decisions)

    override fun offers(id: String): Response
    {
        return try
        {
            handler.offers(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                accessContextFactory.currentAuthenticated()
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request accepted fact offer lookup failed",
                exception
            )
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAcceptedFactOfferResource::class.java)
    }
}
