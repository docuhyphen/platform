package com.docuhyphen.app.api.resource.informationrequest.submission

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.submission.handler.InformationRequestSubmissionRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.submission.operations.InformationRequestSubmissionPreviewResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestSubmissionPreviewResource @Inject constructor(
    submissionService: InformationRequestSubmissionService,
    queryService: InformationRequestSubmissionQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestSubmissionPreviewResourceOperations
{
    private val handler = InformationRequestSubmissionRequestHandler(submissionService, queryService)

    override fun preview(id: String, stageKey: String?): Response
    {
        return try
        {
            handler.preview(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                stageKey,
                accessContextFactory.currentAuthenticated(),
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request submission preview failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestSubmissionPreviewResource::class.java)
    }
}
