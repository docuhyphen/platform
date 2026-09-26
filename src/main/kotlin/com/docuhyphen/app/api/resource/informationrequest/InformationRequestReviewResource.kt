package com.docuhyphen.app.api.resource.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewAssignmentChange
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp.IDEMPOTENCY_KEY_HEADER
import com.docuhyphen.app.api.resource.model.AssignInformationRequestReviewerRequest
import com.docuhyphen.app.api.resource.model.ChangeInformationRequestReviewAssignmentRequest
import com.docuhyphen.app.api.resource.model.OverrideInformationRequestReviewItemRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestReviewCommentRequest
import com.docuhyphen.app.api.resource.model.RecordInformationRequestReviewFindingRequest
import com.docuhyphen.app.api.resource.model.ReopenInformationRequestReviewRequest
import com.docuhyphen.app.api.resource.model.SaveInformationRequestReviewWorksheetRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewAssignmentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewCommentService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewCycleService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewDecisionService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewFindingService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestReviewQueryService
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders.IF_MATCH
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

@Path("/information-requests/{id}/reviews")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class InformationRequestReviewResource @Inject constructor(
    assignments: InformationRequestReviewAssignmentService,
    decisions: InformationRequestReviewDecisionService,
    findings: InformationRequestReviewFindingService,
    comments: InformationRequestReviewCommentService,
    cycles: InformationRequestReviewCycleService,
    queries: InformationRequestReviewQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
)
{
    private val endpoint = InformationRequestReviewEndpoint(assignments, decisions, findings, comments, cycles, queries)

    @GET
    fun list(@PathParam("id") id: String): Response
    {
        return try
        {
            endpoint.list(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review list failed", exception)
        }
    }

    @GET
    @Path("/{reviewId}")
    fun detail(@PathParam("id") id: String, @PathParam("reviewId") reviewId: String): Response
    {
        return try
        {
            endpoint.detail(requestId(id), reviewId(reviewId), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review lookup failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/assignments")
    fun assign(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: AssignInformationRequestReviewerRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.assign(requestId(id), reviewId(reviewId), request, accessContextFactory.currentAuthenticated(), ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request reviewer assignment failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/assignments/{assignmentId}/recusal")
    fun recuse(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.change(
                requestId(id),
                reviewId(reviewId),
                assignmentId(assignmentId),
                InformationRequestReviewAssignmentChange.RECUSAL,
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request reviewer recusal failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/assignments/{assignmentId}/delegation")
    fun delegate(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.change(
                requestId(id),
                reviewId(reviewId),
                assignmentId(assignmentId),
                InformationRequestReviewAssignmentChange.DELEGATION,
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review delegation failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/assignments/{assignmentId}/revocation")
    fun revoke(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.change(
                requestId(id),
                reviewId(reviewId),
                assignmentId(assignmentId),
                InformationRequestReviewAssignmentChange.REVOCATION,
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review assignment revocation failed", exception)
        }
    }

    @PATCH
    @Path("/{reviewId}/assignments/{assignmentId}/worksheet")
    fun saveWorksheet(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        request: SaveInformationRequestReviewWorksheetRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
    ): Response
    {
        return try
        {
            endpoint.saveWorksheet(
                requestId(id),
                reviewId(reviewId),
                assignmentId(assignmentId),
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review worksheet save failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/assignments/{assignmentId}/decisions")
    fun recordWorksheet(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        @PathParam("assignmentId") assignmentId: String,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.recordWorksheet(
                requestId(id),
                reviewId(reviewId),
                assignmentId(assignmentId),
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review decision recording failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/overrides")
    fun override(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: OverrideInformationRequestReviewItemRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.override(requestId(id), reviewId(reviewId), request, accessContextFactory.currentAuthenticated(), ifMatch, idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review override failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/findings")
    fun finding(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: RecordInformationRequestReviewFindingRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.finding(requestId(id), reviewId(reviewId), request, accessContextFactory.currentAuthenticated(), idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review finding failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/comments")
    fun comment(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: RecordInformationRequestReviewCommentRequest?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.comment(requestId(id), reviewId(reviewId), request, accessContextFactory.currentAuthenticated(), idempotencyKey)
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review comment failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/reconsiderations")
    fun reconsider(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: ReopenInformationRequestReviewRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.reopen(
                requestId(id),
                reviewId(reviewId),
                InformationRequestReviewKind.RECONSIDERATION,
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review reconsideration failed", exception)
        }
    }

    @POST
    @Path("/{reviewId}/appeals")
    fun appeal(
        @PathParam("id") id: String,
        @PathParam("reviewId") reviewId: String,
        request: ReopenInformationRequestReviewRequest?,
        @HeaderParam(IF_MATCH) ifMatch: String?,
        @HeaderParam(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String?,
    ): Response
    {
        return try
        {
            endpoint.reopen(
                requestId(id),
                reviewId(reviewId),
                InformationRequestReviewKind.APPEAL,
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey,
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review appeal failed", exception)
        }
    }

    private fun requestId(raw: String) = InformationRequestCommandHttp.uuid(raw, "information request id")

    private fun reviewId(raw: String) = InformationRequestCommandHttp.uuid(raw, "review id")

    private fun assignmentId(raw: String) = InformationRequestCommandHttp.uuid(raw, "review assignment id")

    private companion object
    {
        val logger = LoggerFactory.getLogger(InformationRequestReviewResource::class.java)
    }
}
