package com.docuhyphen.app.api.resource.informationrequest.amendment

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.amendment.handler.InformationRequestAmendmentRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.amendment.operations.InformationRequestAmendmentResourceOperations
import com.docuhyphen.app.api.resource.model.AmendInformationRequestRequest
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentQueryService
import com.docuhyphen.app.api.service.informationrequest.amendment.InformationRequestAmendmentService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestAmendmentResource @Inject constructor(
    amendmentService: InformationRequestAmendmentService,
    queryService: InformationRequestAmendmentQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestAmendmentResourceOperations
{
    private val handler = InformationRequestAmendmentRequestHandler(amendmentService, queryService)

    override fun list(id: String): Response
    {
        return try
        {
            handler.list(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request amendment list failed", exception)
        }
    }

    override fun amend(
        id: String,
        request: AmendInformationRequestRequest,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.amend(requestId(id), request, accessContextFactory.currentAuthenticated(), ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request amendment failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestAmendmentResource::class.java)
    }
}
