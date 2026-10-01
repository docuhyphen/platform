package com.docuhyphen.app.api.resource.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.review.handler.InformationRequestReviewRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestNoAuthReviewResourceOperations
import com.docuhyphen.app.api.resource.model.RecordInformationRequestReviewCommentRequest
import com.docuhyphen.app.api.resource.model.ReopenInformationRequestReviewRequest
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

class InformationRequestNoAuthReviewResource @Inject constructor(
    assignments: InformationRequestReviewAssignmentService,
    decisions: InformationRequestReviewDecisionService,
    findings: InformationRequestReviewFindingService,
    comments: InformationRequestReviewCommentService,
    cycles: InformationRequestReviewCycleService,
    queries: InformationRequestReviewQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
) : InformationRequestNoAuthReviewResourceOperations
{
    private val handler = InformationRequestReviewRequestHandler(assignments, decisions, findings, comments, cycles, queries)

    override fun comment(
        id: String,
        reviewId: String,
        request: RecordInformationRequestReviewCommentRequest?,
        accessLinkToken: String?,
        sessionToken: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.comment(requestId, reviewId(reviewId), request, access, idempotencyKey)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request review comment failed", exception)
        }
    }

    @Suppress("LongParameterList")
    override fun appeal(
        id: String,
        reviewId: String,
        request: ReopenInformationRequestReviewRequest?,
        accessLinkToken: String?,
        sessionToken: String?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                handler.reopen(requestId, reviewId(reviewId), InformationRequestReviewKind.APPEAL, request, access, ifMatch, idempotencyKey)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request review appeal failed", exception)
        }
    }

    private fun reviewId(raw: String) = InformationRequestCommandHttp.uuid(raw, "review id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestNoAuthReviewResource::class.java)
    }
}
