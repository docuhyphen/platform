package com.docuhyphen.app.api.resource.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewAssignmentChange
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.informationrequest.review.handler.InformationRequestReviewRequestHandler
import com.docuhyphen.app.api.resource.informationrequest.review.operations.InformationRequestReviewResourceOperations
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestAccessContextFactory
import com.docuhyphen.app.api.service.informationrequest.review.*
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class InformationRequestReviewResource @Inject constructor(
    assignments: InformationRequestReviewAssignmentService,
    decisions: InformationRequestReviewDecisionService,
    findings: InformationRequestReviewFindingService,
    comments: InformationRequestReviewCommentService,
    cycles: InformationRequestReviewCycleService,
    queries: InformationRequestReviewQueryService,
    private val accessContextFactory: InformationRequestAccessContextFactory,
) : InformationRequestReviewResourceOperations
{
    private val handler =
        InformationRequestReviewRequestHandler(assignments, decisions, findings, comments, cycles, queries)

    override fun list(id: String): Response
    {
        return try
        {
            handler.list(requestId(id), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review list failed", exception)
        }
    }

    override fun detail(id: String, reviewId: String): Response
    {
        return try
        {
            handler.detail(requestId(id), reviewId(reviewId), accessContextFactory.currentAuthenticated())
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review lookup failed", exception)
        }
    }

    override fun assign(
        id: String,
        reviewId: String,
        request: AssignInformationRequestReviewerRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.assign(
                requestId(id),
                reviewId(reviewId),
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request reviewer assignment failed", exception)
        }
    }

    override fun recuse(
        id: String,
        reviewId: String,
        assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.change(
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

    override fun delegate(
        id: String,
        reviewId: String,
        assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.change(
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

    override fun revoke(
        id: String,
        reviewId: String,
        assignmentId: String,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.change(
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
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request review assignment revocation failed",
                exception
            )
        }
    }

    override fun saveWorksheet(
        id: String,
        reviewId: String,
        assignmentId: String,
        request: SaveInformationRequestReviewWorksheetRequest?,
        ifMatch: String?,
    ): Response
    {
        return try
        {
            handler.saveWorksheet(
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

    override fun recordWorksheet(
        id: String,
        reviewId: String,
        assignmentId: String,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.recordWorksheet(
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
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request review decision recording failed",
                exception
            )
        }
    }

    override fun override(
        id: String,
        reviewId: String,
        request: OverrideInformationRequestReviewItemRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.override(
                requestId(id),
                reviewId(reviewId),
                request,
                accessContextFactory.currentAuthenticated(),
                ifMatch,
                idempotencyKey
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review override failed", exception)
        }
    }

    override fun finding(
        id: String,
        reviewId: String,
        request: RecordInformationRequestReviewFindingRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.finding(
                requestId(id),
                reviewId(reviewId),
                request,
                accessContextFactory.currentAuthenticated(),
                idempotencyKey
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review finding failed", exception)
        }
    }

    override fun comment(
        id: String,
        reviewId: String,
        request: RecordInformationRequestReviewCommentRequest?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.comment(
                requestId(id),
                reviewId(reviewId),
                request,
                accessContextFactory.currentAuthenticated(),
                idempotencyKey
            )
        }
        catch (exception: Exception)
        {
            InformationRequestCommandHttp.refused(logger, "Information Request review comment failed", exception)
        }
    }

    override fun reconsider(
        id: String,
        reviewId: String,
        request: ReopenInformationRequestReviewRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.reopen(
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
            InformationRequestCommandHttp.refused(
                logger,
                "Information Request review reconsideration failed",
                exception
            )
        }
    }

    override fun appeal(
        id: String,
        reviewId: String,
        request: ReopenInformationRequestReviewRequest?,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        return try
        {
            handler.reopen(
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
