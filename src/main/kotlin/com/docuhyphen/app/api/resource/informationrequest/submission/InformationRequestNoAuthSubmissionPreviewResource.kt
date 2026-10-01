package com.docuhyphen.app.api.resource.informationrequest.submission

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.submission.handler.InformationRequestSubmissionRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.submission.operations.InformationRequestNoAuthSubmissionPreviewResourceOperations
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthSubmissionPreviewResource @Inject constructor(
    submissionService: InformationRequestSubmissionService,
    queryService: InformationRequestSubmissionQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthSubmissionPreviewResourceOperations
{
    private val handler = InformationRequestSubmissionRequestHandler(submissionService, queryService)

    override fun preview(
        id: String,
        stageKey: String?,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.preview(requestId, stageKey, access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request submission preview failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthSubmissionPreviewResource::class.java)
    }
}
