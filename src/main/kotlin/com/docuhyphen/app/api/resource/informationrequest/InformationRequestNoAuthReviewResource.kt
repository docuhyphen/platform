package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.ACCESS_LINK_TOKEN_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.SESSION_TOKEN_HEADER
import com.docuhyphen.app.api.resource.model.RecordInformationRequestReviewCommentRequest
import com.docuhyphen.app.api.resource.model.ReopenInformationRequestReviewRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewCommentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewCycleService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewDecisionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewFindingService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("no-auth/information-requests/{id}/reviews")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestNoAuthReviewResource @Inject constructor(
    assignments: InformationRequestReviewAssignmentService,
    decisions: InformationRequestReviewDecisionService,
    findings: InformationRequestReviewFindingService,
    comments: InformationRequestReviewCommentService,
    cycles: InformationRequestReviewCycleService,
    queries: InformationRequestReviewQueryService,
    private val readAccessService: InformationRequestNoAuthReadAccessService,
)
{
    private val endpoint = InformationRequestReviewEndpoint(assignments, decisions, findings, comments, cycles, queries)

    @POST
    @Path("/{reviewId}/comments")
    fun comment(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: RecordInformationRequestReviewCommentRequest?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.comment(requestId, reviewId(reviewId), request, access, idempotencyKey)
            }
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "No-auth Information Request review comment failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/appeals")
    @Suppress("LongParameterList")
    fun appeal(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: ReopenInformationRequestReviewRequest?,
        @HeaderParam(ACCESS_LINK_TOKEN_HEADER) accessLinkToken: String?,
        @HeaderParam(SESSION_TOKEN_HEADER) sessionToken: String?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            InformationRequestCommandHttp.withNoAuthAccess(readAccessService, id, accessLinkToken, sessionToken) { requestId, access ->
                endpoint.reopen(requestId, reviewId(reviewId), InformationRequestReviewKind.APPEAL, request, access, ifMatch, idempotencyKey)
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
