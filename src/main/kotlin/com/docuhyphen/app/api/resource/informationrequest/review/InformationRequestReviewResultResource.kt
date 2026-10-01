package com.docuhyphen.app.api.resource.informationrequest.review

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.review.handler.InformationRequestReviewRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestReviewResultResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.review.*
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestReviewResultResource @Inject constructor(
    assignments: InformationRequestReviewAssignmentService,
    decisions: InformationRequestReviewDecisionService,
    findings: InformationRequestReviewFindingService,
    comments: InformationRequestReviewCommentService,
    cycles: InformationRequestReviewCycleService,
    queries: InformationRequestReviewQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestReviewResultResourceOperations
{
    private val handler =
        InformationRequestReviewRequestHandler(assignments, decisions, findings, comments, cycles, queries)

    override fun results(id: String): Response
    {
        return try
        {
            handler.results(
                InformationRequestCommandHttp.uuid(id, "information request id"),
                accessContextFactory.currentAuthenticated()
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review results lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestReviewResultResource::class.java)
    }
}
