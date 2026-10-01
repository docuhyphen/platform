package com.docuhyphen.app.api.resource.informationrequest.amendment

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.amendment.handler.InformationRequestAmendmentRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.amendment.operations.InformationRequestNoAuthAmendmentResourceOperations
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthAmendmentResource @Inject constructor(
    amendmentService: InformationRequestAmendmentService,
    queryService: InformationRequestAmendmentQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthAmendmentResourceOperations
{
    private val handler = InformationRequestAmendmentRequestHandler(amendmentService, queryService)

    override fun list(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.list(requestId, access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request amendment list failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthAmendmentResource::class.java)
    }
}
