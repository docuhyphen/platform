package com.docuhyphen.app.api.resource.informationrequest.review

import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.review.handler.InformationRequestReviewRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestNoAuthReviewResultResourceOperations
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewAssignmentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCommentService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewCycleService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewDecisionService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewFindingService
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestNoAuthReviewResultResource @Inject constructor(
    assignments: InformationRequestReviewAssignmentService,
    decisions: InformationRequestReviewDecisionService,
    findings: InformationRequestReviewFindingService,
    comments: InformationRequestReviewCommentService,
    cycles: InformationRequestReviewCycleService,
    queries: InformationRequestReviewQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthReviewResultResourceOperations
{
    private val handler = InformationRequestReviewRequestHandler(assignments, decisions, findings, comments, cycles, queries)

    override fun results(
        id: String,
        accessLinkToken: String?,
        sessionToken: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.results(requestId, access)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request review results lookup failed", exception)
        }
    }

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthReviewResultResource::class.java)
    }
}
