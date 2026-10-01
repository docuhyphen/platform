package com.docuhyphen.app.api.resource.informationrequest.acceptedfact

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.handler.InformationRequestAcceptedFactRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.acceptedfact.operations.InformationRequestBusinessDecisionResourceOperations
import com.docuhyphen.app.api.resource.model.RecordInformationRequestBusinessDecisionRequest
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactQueryService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestAcceptedFactService
import com.docuhyphen.app.api.service.informationrequest.acceptedfact.InformationRequestBusinessDecisionService
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestBusinessDecisionResource @Inject constructor(
    facts: InformationRequestAcceptedFactService,
    factQueries: InformationRequestAcceptedFactQueryService,
    decisions: InformationRequestBusinessDecisionService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestBusinessDecisionResourceOperations
{
    private val handler = InformationRequestAcceptedFactRequestHandler(facts, factQueries, decisions)

    override fun list(id: String): Response
    {
        return try
        {
            handler.decisions(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request business decision list failed", exception)
        }
    }

    override fun record(
        id: String,
        request: RecordInformationRequestBusinessDecisionRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.recordDecision(requestId(id), request, accessContextFactory.currentAuthenticated(), idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request business decision recording failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestBusinessDecisionResource::class.java)
    }
}
