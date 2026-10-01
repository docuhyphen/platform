package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestReviewComment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewCommentRole
import com.docuhyphen.app.api.model.entity.InformationRequestReviewVisibility
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewCommentRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.*
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
)
{
    @Transactional
    fun record(command: RecordInformationRequestReviewCommentCommand): InformationRequestReviewCommandResult
    {
        val locked = gate.lock(command.requestId)
        val body = command.body.trim()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_REVIEW, command.reviewId),
            operation = OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.sha256Hex(
                listOf(
                    OPERATION,
                    command.reviewId,
                    command.submissionItemId,
                    command.findingId ?: "",
                    command.replyToCommentId ?: "",
                    command.visibility,
                    sha256Hex(body),
                ).joinToString("|"),
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
        gate.requireMutation(locked, InformationRequestMutation.RECORD_REVIEW_COMMENT)
        gate.requireContinuationEntitlement(locked)
        if (body.isEmpty() || body.length > MAXIMUM_BODY_LENGTH)
        {
            throw InformationRequestCommandRequestException("A comment holds between 1 and $MAXIMUM_BODY_LENGTH characters")
        }
        val review = loader.requireReview(command.requestId, command.reviewId)
        val snapshot = loader.snapshot(review)
        val item = snapshot.item(command.submissionItemId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Submission item not found"
            )
        val role = roleOf(command, review.id)
        val visibility = if (role == InformationRequestReviewCommentRole.RESPONDENT)
            InformationRequestReviewVisibility.RESPONDENT_VISIBLE
        else command.visibility
        if (role == InformationRequestReviewCommentRole.RESPONDENT)
        {
            if (!review.state.settled)
            {
                refuse("A respondent comments on a review once it has settled")
            }
            if (!gate.permitsRequirement(
                    command.access,
                    Action.INFORMATION_REQUEST_REQUIREMENT_VIEW,
                    item.informationRequestRequirementId
                )
            )
            {
                throw ForbiddenException("Access denied to this Information Request Requirement")
            }
        }
        command.findingId?.let { findingId ->
            val finding = snapshot.findings.firstOrNull { it.id == findingId && it.submissionItemId == item.id }
                ?: refuse("A comment answers a finding on its own item")
            if (role == InformationRequestReviewCommentRole.RESPONDENT &&
                finding.visibility != InformationRequestReviewVisibility.RESPONDENT_VISIBLE
            )
            {
                refuse("A respondent can answer only a finding shown to respondents")
            }
        }
        command.replyToCommentId?.let { replyId ->
            val earlier = commentRepository.findForReviews(listOf(review.id))
                .firstOrNull { it.id == replyId && it.submissionItemId == item.id }
                ?: refuse("A reply answers a comment on the same review item")
            if (role == InformationRequestReviewCommentRole.RESPONDENT &&
                earlier.visibility != InformationRequestReviewVisibility.RESPONDENT_VISIBLE
            )
            {
                refuse("A respondent can answer only a comment shown to respondents")
            }
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
                actor = command.access.principal,
                idempotencyKey = "information_request.review_comment|${comment.id}|${command.idempotencyKey}",
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

    private fun refuse(message: String): Nothing =
        throw InformationRequestLifecycleException(InformationRequestErrorCatalog.REVIEW_COMMENT_NOT_PERMITTED, message)

    private fun sha256Hex(material: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object
    {
        const val OPERATION = "record-information-request-review-comment"
        const val MAXIMUM_BODY_LENGTH = 4000
    }
}
