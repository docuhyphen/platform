package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestCarryForwardResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestCarryForwardResource @Inject constructor(
    private val lineageQueryService: InformationRequestLineageQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestCarryForwardResourceOperations
{
    override fun list(id: String): Response
    {
        return try
        {
            val offers = lineageQueryService.carryForwards(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                accessContextFactory.currentAuthenticated(),
            )
            Response.ok(offers.map(InformationRequestLineageDtoMapper::toDto).toTypedArray()).build()
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
