package com.docuhyphen.app.api.resource.informationrequest.review

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.review.handler.InformationRequestReviewRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestReviewQueueResourceOperations
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewAssignmentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCommentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCycleService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewDecisionService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewFindingService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestReviewQueueResource @Inject constructor(
    assignments: InformationRequestReviewAssignmentService,
    decisions: InformationRequestReviewDecisionService,
    findings: InformationRequestReviewFindingService,
    comments: InformationRequestReviewCommentService,
    cycles: InformationRequestReviewCycleService,
    queries: InformationRequestReviewQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestReviewQueueResourceOperations
{
    private val handler = InformationRequestReviewRequestHandler(assignments, decisions, findings, comments, cycles, queries)

    override fun queue(): Response
    {
        return try
        {
            handler.queue(accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request reviewer queue lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestReviewQueueResource::class.java)
    }
}
