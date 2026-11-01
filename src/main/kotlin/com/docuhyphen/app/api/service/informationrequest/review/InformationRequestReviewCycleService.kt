package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.review.ReopenInformationRequestReviewCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.canonicalForm
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.clock.InformationRequestClockHold
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.noauth.InformationRequestNoAuthReadAccessService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionContentCollector
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestReviewCycleService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val loader: InformationRequestReviewLoader,
    private val opening: InformationRequestReviewOpeningService,
    private val reopenPolicy: InformationRequestReviewReopenPolicy,
    private val reviewRepository: InformationRequestReviewRepository,
    private val correctionRepository: InformationRequestCorrectionRepository,
    private val requestRepository: InformationRequestRepository,
    private val commandReceiptService: CommandReceiptService,
    private val clock: Clock,
    private val entityManager: EntityManager,
    private val noAuthAccess: InformationRequestNoAuthReadAccessService,
    private val clockHold: InformationRequestClockHold,
    private val contentCollector: InformationRequestSubmissionContentCollector,
)
{
    @Transactional
    fun reopen(requested: ReopenInformationRequestReviewCommand): InformationRequestReviewCommandResult
    {
        require(requested.kind == InformationRequestReviewKind.RECONSIDERATION || requested.kind == InformationRequestReviewKind.APPEAL) {
            "Only a reconsideration or an appeal reopens a review"
        }
        gate.requireVisibleTarget(requested.access, requested.requestId)
        val locked = gate.lock(requested.requestId)
        val command = requested.copy(access = noAuthAccess.requireCurrent(requested.requestId, requested.access))
        val reason = command.reason.trim()
        val receipt = CommandReceiptRequest(
            resource = ResourceRef(ResourceType.INFORMATION_REQUEST_REVIEW, command.reviewId),
            operation = OPERATION,
            actor = CommandActorRef.principal(command.access.principal),
            idempotencyKey = command.idempotencyKey,
            requestFingerprint = CommandRequestFingerprint.of(
                OPERATION,
                command.reviewId,
                command.kind,
                reason,
                command.precondition.canonicalForm(),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = reopenReview(locked, command, reason)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        resourceType = ResourceType.INFORMATION_REQUEST_REVIEW,
                        resourceId = result.review.id,
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
                val review = loader.requireReview(command.requestId, decision.result.resourceId)
                result(locked, review)
            }
        }
    }

    private fun reopenReview(
        locked: LockedInformationRequest,
        command: ReopenInformationRequestReviewCommand,
        reason: String,
    ): InformationRequestReviewCommandResult
    {
        gate.authorizeRequest(
            command.access,
            listOf(
                if (command.kind == InformationRequestReviewKind.APPEAL) Action.INFORMATION_REQUEST_APPEAL_REVIEW
                else Action.INFORMATION_REQUEST_MANAGE_REVIEWS,
            ),
            command.requestId,
        )
        gate.requireMutation(locked, InformationRequestMutation.START_REVIEW)
        gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
        InformationRequestReviewInputLimits.requireText(reason, "A reconsideration or appeal reason")
        if (reason.isEmpty()) throw InformationRequestCommandRequestException("A reconsideration or appeal states its reason")
        val prior = loader.requireReview(command.requestId, command.reviewId)
        command.precondition.requireSatisfiedBy(loader.reviewETag(prior))
        if (!reopenPolicy.reopenable(prior))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_NOT_REOPENABLE,
                "Only the latest settled review of a current submission that was rejected or returned can be reopened",
            )
        }
        val now = Timestamp.from(clock.instant())
        val correction = correctionRepository.findForRequest(command.requestId).firstOrNull { it.reviewId == prior.id }
        val supersededCorrectionId = correction?.takeIf { it.state == InformationRequestCorrectionState.OPEN }?.id
        if (supersededCorrectionId != null) {
            val submitted = loader.snapshot(prior).submission.submissionPackage
            if (contentCollector.collect(
                    locked.request,
                    submitted.stageKey
                ).contentHash != submitted.contentHashSha256
            ) {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_NOT_REOPENABLE,
                    "Submit the pending corrections before opening another review of this submission"
                )
            }
        }
        correction?.takeIf { it.state == InformationRequestCorrectionState.OPEN }?.let {
            it.state = InformationRequestCorrectionState.SUPERSEDED
            it.closedAt = now
            correctionRepository.update(it)
        }
        val review = reviewRepository.save(
            InformationRequestReview().apply {
                informationRequestId = prior.informationRequestId
                packageId = prior.packageId
                reviewNumber = reviewRepository.nextReviewNumber(prior.informationRequestId)
                kind = command.kind
                priorReviewId = prior.id
                templateVersionId = prior.templateVersionId
                state = InformationRequestReviewState.PENDING
                reviewRevision = 1
                openingReason = reason
                openedByPrincipalKind = command.access.principal.kind
                openedByPrincipalId = command.access.principal.id
                openedAt = now
            },
        )
        entityManager.flush()
        val request = locked.request
        request.responseRevision += 1
        request.aggregateRevision += 1
        request.updatedAt = now
        requestRepository.update(request)
        opening.recordStart(
            locked, review, InformationRequestAuditActor.of(command.access), command.idempotencyKey,
            supersededCorrectionId?.let { mapOf("supersededCorrectionId" to it.toString()) }.orEmpty()
        )
        clockHold.sync(locked)
        return result(locked, review)
    }

    private fun result(locked: LockedInformationRequest, review: InformationRequestReview) =
        InformationRequestReviewCommandResult(
            request = locked.request,
            review = review,
            reviewETag = loader.reviewETag(review),
            responseETag = InformationRequestETag.responsesOf(locked.request),
        )

    private companion object
    {
        const val OPERATION = "reopen-information-request-review"
    }
}
