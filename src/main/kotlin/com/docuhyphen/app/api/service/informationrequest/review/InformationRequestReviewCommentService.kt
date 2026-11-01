package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewCommentRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementDisclosure
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestReviewCommentService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val access: InformationRequestReviewAccess,
    private val loader: InformationRequestReviewLoader,
    private val commentRepository: InformationRequestReviewCommentRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
    private val disclosure: InformationRequestRequirementDisclosure,
    private val noAuthAccess: InformationRequestNoAuthReadAccessService,
)
{
    @Transactional
    fun record(requested: RecordInformationRequestReviewCommentCommand): InformationRequestReviewCommandResult
    {
        gate.requireVisibleTarget(requested.access, requested.requestId)
        val locked = gate.lock(requested.requestId)
        val command = requested.copy(access = noAuthAccess.requireCurrent(requested.requestId, requested.access))
        val body = command.body.trim()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_REVIEW, command.reviewId),
            operation = OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(
                OPERATION,
                command.reviewId,
                command.submissionItemId,
                command.findingId,
                command.replyToCommentId,
                command.visibility,
                body,
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = recordComment(locked, command, body)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        resourceType = ResourceType.INFORMATION_REQUEST_REVIEW_COMMENT,
                        resourceId = requireNotNull(result.comment).id,
                        revision = result.review.reviewRevision,
                        etag = result.reviewETag,
                    ),
                )
            }
        )
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_VIEW), command.requestId)
                val comment = commentRepository.findById(decision.result.resourceId)
                    ?: throw InformationRequestLifecycleException(
                        InformationRequestErrorCatalog.NOT_FOUND,
                        "Comment not found"
                    )
                val review = loader.requireReview(command.requestId, comment.reviewId)
                InformationRequestReviewCommandResult(
                    request = locked.request,
                    review = review,
                    reviewETag = loader.reviewETag(review),
                    responseETag = InformationRequestETag.responsesOf(locked.request),
                    comment = comment,
                )
            }
        }
    }

    private fun recordComment(
        locked: LockedInformationRequest,
        command: RecordInformationRequestReviewCommentCommand,
        body: String,
    ): InformationRequestReviewCommandResult
    {
        requireMayComment(command)
        gate.requireMutation(locked, InformationRequestMutation.RECORD_REVIEW_COMMENT)
        gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
        if (body.isEmpty() || body.length > MAXIMUM_BODY_LENGTH)
        {
            throw InformationRequestCommandRequestException("A comment holds between 1 and $MAXIMUM_BODY_LENGTH characters")
        }
        val review = loader.requireReview(command.requestId, command.reviewId)
        val snapshot = loader.snapshot(review)
        val role = roleOf(command, review.id)
        val item = if (role == InformationRequestReviewCommentRole.RESPONDENT)
            respondentItem(command, snapshot.item(command.submissionItemId))
        else snapshot.item(command.submissionItemId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Submission item not found"
            )
        val visibility = if (role == InformationRequestReviewCommentRole.RESPONDENT)
            InformationRequestReviewVisibility.RESPONDENT_VISIBLE
        else command.visibility
        if (role == InformationRequestReviewCommentRole.RESPONDENT && !review.state.decided) {
            refuse("A respondent comments on a review once it has been decided")
        }
        if (role == InformationRequestReviewCommentRole.REVIEWER &&
            !access.permitsItemReview(command.access, item.informationRequestRequirementId)
        ) {
            disclosure.requireRecentVerification(
                command.access,
                command.requestId,
                item.informationRequestRequirementId
            )
            throw ForbiddenException("Access denied to review this Information Request Requirement")
        }
        if (role == InformationRequestReviewCommentRole.ADMINISTRATOR) {
            disclosure.requireRecentVerification(
                command.access,
                command.requestId,
                item.informationRequestRequirementId
            )
        }
        command.findingId?.let { findingId ->
            snapshot.findings.firstOrNull {
                it.id == findingId && it.submissionItemId == item.id && answerableBy(role, it.visibility)
            } ?: refuse("A comment answers a finding on its own item")
        }
        command.replyToCommentId?.let { replyId ->
            commentRepository.findForReviews(listOf(review.id)).firstOrNull {
                it.id == replyId && it.submissionItemId == item.id && answerableBy(role, it.visibility)
            } ?: refuse("A reply answers a comment on the same review item")
        }
        val now = Timestamp.from(clock.instant())
        val comment = commentRepository.save(
            InformationRequestReviewComment().apply {
                informationRequestId = review.informationRequestId
                reviewId = review.id
                packageId = review.packageId
                submissionItemId = item.id
                requirementId = item.informationRequestRequirementId
                findingId = command.findingId
                replyToCommentId = command.replyToCommentId
                authorRole = role
                this.visibility = visibility
                this.body = body
                authorPrincipalKind = command.access.principal.kind
                authorPrincipalId = command.access.principal.id
                authorSessionRef = command.access.authorization.sessionRef?.takeIf { it.isNotBlank() }
                createdAt = now
                sequenceNumber = commentRepository.nextSequenceNumber(review.id)
            },
        )
        val request = locked.request
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.RECORD_REVIEW_COMMENT,
                actor = InformationRequestAuditActor.of(command.access),
                auditIdempotencyKey = "information_request.review_comment|${comment.id}|${command.idempotencyKey}",
                details = mapOf(
                    "reviewId" to review.id.toString(),
                    "commentId" to comment.id.toString(),
                    "submissionItemId" to item.id.toString(),
                    "commentRole" to role.name,
                    "commentVisibility" to visibility.name,
                ),
            ),
        )
        return InformationRequestReviewCommandResult(
            request = request,
            review = review,
            reviewETag = loader.reviewETag(review),
            responseETag = InformationRequestETag.responsesOf(request),
            comment = comment,
        )
    }

    private fun respondentItem(
        command: RecordInformationRequestReviewCommentCommand,
        item: InformationRequestSubmissionItem?,
    ): InformationRequestSubmissionItem {
        val requirementId = item?.informationRequestRequirementId
        if (requirementId != null &&
            gate.permitsRequirement(command.access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, requirementId)
        ) {
            return item
        }
        if (requirementId != null &&
            disclosure.awaitsVerification(command.access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, requirementId)
        ) {
            disclosure.refuseUnverified(command.access)
        }
        throw ForbiddenException("Access denied to this Information Request Requirement")
    }

    private fun answerableBy(
        role: InformationRequestReviewCommentRole,
        visibility: InformationRequestReviewVisibility
    ) =
        role != InformationRequestReviewCommentRole.RESPONDENT ||
                visibility == InformationRequestReviewVisibility.RESPONDENT_VISIBLE

    private fun roleOf(
        command: RecordInformationRequestReviewCommentCommand,
        reviewId: java.util.UUID
    ): InformationRequestReviewCommentRole
    {
        val review = loader.requireReview(command.requestId, reviewId)
        return when
        {
            access.permitsReview(command.access, command.requestId) && access.callerAssignments(review, command.access)
                .isNotEmpty() ->
                InformationRequestReviewCommentRole.REVIEWER

            access.permitsManage(command.access, command.requestId) -> InformationRequestReviewCommentRole.ADMINISTRATOR
            gate.permitsRequest(command.access, Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW, command.requestId) ->
                InformationRequestReviewCommentRole.RESPONDENT

            else -> throw ForbiddenException("Access denied to comment on this review")
        }
    }

    private fun requireMayComment(command: RecordInformationRequestReviewCommentCommand) {
        if (!access.permitsReview(command.access, command.requestId) &&
            !access.permitsManage(command.access, command.requestId) &&
            !gate.permitsRequest(command.access, Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW, command.requestId)
        ) {
            throw ForbiddenException("Access denied to comment on this review")
        }
    }

    private fun refuse(message: String): Nothing =
        throw InformationRequestLifecycleException(InformationRequestErrorCatalog.REVIEW_COMMENT_NOT_PERMITTED, message)

    private companion object
    {
        const val OPERATION = "record-information-request-review-comment"
        const val MAXIMUM_BODY_LENGTH = 4000
    }
}
