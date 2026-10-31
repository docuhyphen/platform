package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.handler.InformationRequestCarryForwardRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestNoAuthCarryForwardResourceOperations
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthCarryForwardResource @Inject constructor(
    lineageQueryService: InformationRequestLineageQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthCarryForwardResourceOperations
{
    private val carryForwards = InformationRequestCarryForwardRequestHandler(lineageQueryService)

    override fun list(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthRead(
                readAccessService,
                id,
                accessLinkToken,
                sessionToken
            ) { requestId, access -> carryForwards.list(requestId, access) }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(
                logger,
                "No-auth Information Request carry-forward lookup failed",
                exception
            )
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthCarryForwardResource::class.java)
    }
}
