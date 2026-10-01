package com.docuhyphen.app.api.resource.informationrequest.review.handler

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.InformationRequestReviewDtoMapper
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.resource.command.CommandPreconditionHeader
import com.docuhyphen.app.api.resource.informationrequest.InformationRequestCommandHttp
import com.docuhyphen.app.api.resource.model.*
import com.docuhyphen.app.api.service.informationrequest.review.*
import jakarta.ws.rs.core.Response
import java.util.*

class InformationRequestReviewRequestHandler(
    private val assignments: InformationRequestReviewAssignmentService,
    private val decisions: InformationRequestReviewDecisionService,
    private val findings: InformationRequestReviewFindingService,
    private val comments: InformationRequestReviewCommentService,
    private val cycles: InformationRequestReviewCycleService,
    private val queries: InformationRequestReviewQueryService,
)
{
    fun list(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(
            queries.reviews(requestId, access).map { InformationRequestReviewDtoMapper.toDto(it, access.principal) }
                .toTypedArray()
        )
            .build()

    fun detail(requestId: UUID, reviewId: UUID, access: RequestAccessContext): Response
    {
        val dto = InformationRequestReviewDtoMapper.toDto(queries.review(requestId, reviewId, access), access.principal)
        return Response.ok(dto).header("ETag", dto.review.reviewETag).build()
    }

    fun results(requestId: UUID, access: RequestAccessContext): Response =
        Response.ok(
            queries.results(requestId, access).map { InformationRequestReviewDtoMapper.toDto(it, access.principal) }
                .toTypedArray()
        )
            .build()

    fun queue(access: RequestAccessContext): Response =
        Response.ok(queries.queue(access).map(InformationRequestReviewDtoMapper::toDto).toTypedArray()).build()

    @Suppress("LongParameterList")
    fun assign(
        requestId: UUID,
        reviewId: UUID,
        request: AssignInformationRequestReviewerRequest?,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val body = request
            ?: throw InformationRequestCommandRequestException("A reviewer assignment names its stage and reviewer")
        val result = assignments.assign(
            AssignInformationRequestReviewerCommand(
                requestId = requestId,
                reviewId = reviewId,
                stageKey = body.stageKey,
                reviewerPartyId = body.reviewerPartyId,
                dueAt = body.dueAt?.toInstant(),
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return commandResponse(Response.Status.CREATED, result)
    }

    @Suppress("LongParameterList")
    fun change(
        requestId: UUID,
        reviewId: UUID,
        assignmentId: UUID,
        change: InformationRequestReviewAssignmentChange,
        request: ChangeInformationRequestReviewAssignmentRequest?,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val result = assignments.change(
            ChangeInformationRequestReviewAssignmentCommand(
                requestId = requestId,
                reviewId = reviewId,
                assignmentId = assignmentId,
                change = change,
                reasonCode = request?.reasonCode,
                narrative = request?.narrative,
                delegatePartyId = request?.delegatePartyId,
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return commandResponse(Response.Status.OK, result)
    }

    fun saveWorksheet(
        requestId: UUID,
        reviewId: UUID,
        assignmentId: UUID,
        request: SaveInformationRequestReviewWorksheetRequest?,
        access: RequestAccessContext,
        ifMatch: String?,
    ): Response
    {
        val entries = request?.entries.orEmpty()
        if (entries.isEmpty()) throw InformationRequestCommandRequestException("A worksheet change names at least one item")
        val result = decisions.saveDraft(
            SaveInformationRequestReviewDraftCommand(
                requestId = requestId,
                reviewId = reviewId,
                assignmentId = assignmentId,
                patches = entries.map {
                    if (!it.clear && it.outcome == null)
                    {
                        throw InformationRequestCommandRequestException("A worksheet entry states an outcome or clears the entry")
                    }
                    InformationRequestReviewDraftPatch(it.submissionItemId, it.outcome, it.narrative, it.clear)
                },
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
            ),
        )
        return Response.ok(InformationRequestReviewDtoMapper.toDto(result)).header("ETag", result.draftETag).build()
    }

    fun recordWorksheet(
        requestId: UUID,
        reviewId: UUID,
        assignmentId: UUID,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val result = decisions.recordDecisions(
            RecordInformationRequestReviewDecisionsCommand(
                requestId = requestId,
                reviewId = reviewId,
                assignmentId = assignmentId,
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return commandResponse(Response.Status.OK, result)
    }

    @Suppress("LongParameterList")
    fun override(
        requestId: UUID,
        reviewId: UUID,
        request: OverrideInformationRequestReviewItemRequest?,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val body = request
            ?: throw InformationRequestCommandRequestException("An override names its stage, item, outcome, and reason")
        val result = decisions.override(
            OverrideInformationRequestReviewItemCommand(
                requestId = requestId,
                reviewId = reviewId,
                stageKey = body.stageKey,
                submissionItemId = body.submissionItemId,
                outcome = body.outcome,
                narrative = body.narrative,
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return commandResponse(Response.Status.CREATED, result)
    }

    fun finding(
        requestId: UUID,
        reviewId: UUID,
        request: RecordInformationRequestReviewFindingRequest?,
        access: RequestAccessContext,
        idempotencyKey: String?,
    ): Response
    {
        val body =
            request ?: throw InformationRequestCommandRequestException("A finding names its item, reason, and severity")
        val result = findings.record(
            RecordInformationRequestReviewFindingCommand(
                requestId = requestId,
                reviewId = reviewId,
                submissionItemId = body.submissionItemId,
                evidenceVersionId = body.evidenceVersionId,
                reasonCode = body.reasonCode,
                narrative = body.narrative,
                severity = body.severity,
                visibility = body.visibility,
                correctionScope = body.correctionScope,
                retestsFindingId = body.retestsFindingId,
                retestResult = body.retestResult,
                access = access,
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return commandResponse(Response.Status.CREATED, result)
    }

    fun comment(
        requestId: UUID,
        reviewId: UUID,
        request: RecordInformationRequestReviewCommentRequest?,
        access: RequestAccessContext,
        idempotencyKey: String?,
    ): Response
    {
        val body =
            request ?: throw InformationRequestCommandRequestException("A comment names its item and states its text")
        val result = comments.record(
            RecordInformationRequestReviewCommentCommand(
                requestId = requestId,
                reviewId = reviewId,
                submissionItemId = body.submissionItemId,
                findingId = body.findingId,
                replyToCommentId = body.replyToCommentId,
                visibility = body.visibility,
                body = body.body,
                access = access,
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return commandResponse(Response.Status.CREATED, result)
    }

    @Suppress("LongParameterList")
    fun reopen(
        requestId: UUID,
        reviewId: UUID,
        kind: InformationRequestReviewKind,
        request: ReopenInformationRequestReviewRequest?,
        access: RequestAccessContext,
        ifMatch: String?,
        idempotencyKey: String?,
    ): Response
    {
        val body =
            request ?: throw InformationRequestCommandRequestException("A reconsideration or appeal states its reason")
        val result = cycles.reopen(
            ReopenInformationRequestReviewCommand(
                requestId = requestId,
                reviewId = reviewId,
                kind = kind,
                reason = body.reason,
                access = access,
                precondition = CommandPreconditionHeader.required(ifMatch),
                idempotencyKey = InformationRequestCommandHttp.idempotencyKey(idempotencyKey),
            ),
        )
        return commandResponse(Response.Status.CREATED, result)
    }

    private fun commandResponse(status: Response.Status, result: InformationRequestReviewCommandResult): Response
    {
        val (packageNumber, stageKey) = queries.packagePosition(result.review)
        return Response.status(status)
            .entity(InformationRequestReviewDtoMapper.toDto(result, packageNumber, stageKey))
            .header("ETag", result.reviewETag)
            .build()
    }
}
