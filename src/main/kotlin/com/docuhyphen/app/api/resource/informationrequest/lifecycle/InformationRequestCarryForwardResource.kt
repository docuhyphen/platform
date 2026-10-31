package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.handler.InformationRequestCarryForwardRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestCarryForwardResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestCarryForwardResource @Inject constructor(
    lineageQueryService: InformationRequestLineageQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestCarryForwardResourceOperations
{
    private val carryForwards = InformationRequestCarryForwardRequestHandler(lineageQueryService)

    override fun list(id: String): Response
    {
        return try
        {
            carryForwards.list(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                accessContextFactory.currentAuthenticated(),
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request carry-forward lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestCarryForwardResource::class.java)
    }
}
