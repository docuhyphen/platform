package com.docuhyphen.app.api.resource.informationrequest.lifecycle

import com.docuhyphen.app.api.model.InformationRequestLineageDtoMapper
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.lifecycle.operations.InformationRequestNoAuthCarryForwardResourceOperations
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLineageQueryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthCarryForwardResource @Inject constructor(
    private val lineageQueryService: InformationRequestLineageQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthCarryForwardResourceOperations
{
    override fun list(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(
                readAccessService,
                id,
                accessLinkToken,
                sessionToken
            ) { requestId, access ->
                Response.ok(
                    lineageQueryService.carryForwards(requestId, access).map(InformationRequestLineageDtoMapper::toDto)
                        .toTypedArray(),
                ).build()
            }
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
