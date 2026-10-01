package com.docuhyphen.app.api.resource.informationrequest.acceptedfact

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.handler.InformationRequestAcceptedFactRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestNoAuthAcceptedFactOfferResourceOperations
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthAcceptedFactOfferResource @Inject constructor(
    facts: InformationRequestAcceptedFactService,
    factQueries: InformationRequestAcceptedFactQueryService,
    decisions: InformationRequestBusinessDecisionService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthAcceptedFactOfferResourceOperations
{
    private val handler = InformationRequestAcceptedFactRequestHandler(facts, factQueries, decisions)

    override fun offers(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.offers(requestId, access)
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
