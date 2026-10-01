package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestReviewFinding
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCommandResult
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewFindingCommand
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewFindingRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandActorRef
import com.docuhyphen.app.api.service.command.CommandMutationResult
import com.docuhyphen.app.api.service.command.CommandReceiptDecision
import com.docuhyphen.app.api.service.command.CommandReceiptRequest
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.CommandResultReference
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
class InformationRequestReviewFindingService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val access: InformationRequestReviewAccess,
    private val loader: InformationRequestReviewLoader,
    private val reviewRepository: InformationRequestReviewRepository,
    private val findingRepository: InformationRequestReviewFindingRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
)
{
    @Transactional
    fun record(command: RecordInformationRequestReviewFindingCommand): InformationRequestReviewCommandResult
    {
        val locked = gate.lock(command.requestId)
        val reasonCode = command.reasonCode.trim().lowercase()
        val narrative = command.narrative.trim()
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
                    command.evidenceVersionId ?: "",
                    reasonCode,
                    sha256Hex(narrative),
                    command.severity,
                    command.visibility,
                    command.correctionScope,
                    command.retestsFindingId ?: "",
                    command.retestResult ?: "",
                ).joinToString("|"),
            ),
        )
        return when (
            val decision = commandReceiptService.runOnce(receipt) {
                val result = recordFinding(locked, command, reasonCode, narrative)
                CommandMutationResult(
                    result,
                    CommandResultReference(
                        resourceType = ResourceType.INFORMATION_REQUEST_REVIEW_FINDING,
                        resourceId = requireNotNull(result.finding).id,
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
                gate.authorizeRequest(
                    command.access,
                    listOf(Action.INFORMATION_REQUEST_REVIEW, Action.INFORMATION_REQUEST_MANAGE_REVIEWS),
                    command.requestId,
                )
                val finding = findingRepository.findById(decision.result.resourceId)
                    ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Finding not found")
                val review = loader.requireReview(command.requestId, finding.reviewId)
                InformationRequestReviewCommandResult(
                    request = locked.request,
                    review = review,
                    reviewETag = loader.reviewETag(review),
                    responseETag = InformationRequestETag.responsesOf(locked.request),
                    finding = finding,
                )
            }
        }
    }

    private fun recordFinding(
        locked: LockedInformationRequest,
        command: RecordInformationRequestReviewFindingCommand,
        reasonCode: String,
        narrative: String,
    ): InformationRequestReviewCommandResult
    {
        gate.requireMutation(locked, InformationRequestMutation.RECORD_FINDING)
        gate.requireContinuationEntitlement(locked)
        val review = loader.requireReview(command.requestId, command.reviewId)
        access.requireOpen(review)
        val assignment = access.callerAssignments(review, command.access).firstOrNull()
        val reviewing = assignment != null && access.permitsReview(command.access, command.requestId)
        if (!reviewing && !access.permitsManage(command.access, command.requestId))
        {
            throw ForbiddenException("Only an assigned reviewer or a request administrator may record a finding")
        }
        if (!REASON_CODE.matches(reasonCode))
        {
            throw InformationRequestCommandRequestException("A finding reason code is a lowercase machine code")
        }
        if (narrative.isEmpty()) throw InformationRequestCommandRequestException("A finding states what was found")
        val snapshot = loader.snapshot(review)
        val item = snapshot.item(command.submissionItemId)
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Submission item not found")
        if (reviewing && !access.permitsItemReview(command.access, item.informationRequestRequirementId))
        {
            throw ForbiddenException("Access denied to review this Information Request Requirement")
        }
        if (command.correctionScope == InformationRequestFindingCorrectionScope.EVIDENCE_VERSION && command.evidenceVersionId == null)
        {
            throw InformationRequestCommandRequestException("A finding that returns a file names the evidence version it returns")
        }
        command.evidenceVersionId?.let { versionId ->
            if (snapshot.submission.evidence.none { it.itemId == item.id && it.evidenceVersionId == versionId })
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_EVIDENCE_NOT_SUBMITTED,
                    "The named evidence version was not submitted for that item",
                )
            }
        }
        if ((command.retestsFindingId == null) != (command.retestResult == null))
        {
            throw InformationRequestCommandRequestException("A retest names both the finding it retests and its result")
        }
        command.retestsFindingId?.let { retested ->
            val prior = review.priorReviewId?.let { findingRepository.findForReview(it) }.orEmpty()
            if (prior.none { it.id == retested })
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_RETEST_INVALID,
                    "A retest names a finding of the review this review follows",
                )
            }
        }
        val now = Timestamp.from(clock.instant())
        val finding = findingRepository.save(
            InformationRequestReviewFinding().apply {
                reviewId = review.id
                informationRequestId = review.informationRequestId
                packageId = review.packageId
                submissionItemId = item.id
                requirementId = item.informationRequestRequirementId
                evidenceVersionId = command.evidenceVersionId
                assignmentId = assignment?.id?.takeIf { reviewing }
                this.reasonCode = reasonCode
                this.narrative = narrative
                severity = command.severity
                visibility = command.visibility
                correctionScope = command.correctionScope
                retestsFindingId = command.retestsFindingId
                retestResult = command.retestResult
                recordedByPrincipalKind = command.access.principal.kind
                recordedByPrincipalId = command.access.principal.id
                recordedAt = now
                sequenceNumber = findingRepository.nextSequenceNumber(review.id)
            },
        )
        review.reviewRevision += 1
        reviewRepository.update(review)
        val request = locked.request
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.RECORD_FINDING,
                actor = command.access.principal,
                reasonCode = reasonCode,
                idempotencyKey = "information_request.review_finding|${finding.id}|${command.idempotencyKey}",
                details = mapOf(
                    "reviewId" to review.id.toString(),
                    "findingId" to finding.id.toString(),
                    "submissionItemId" to item.id.toString(),
                    "findingSeverity" to finding.severity.name,
                    "findingVisibility" to finding.visibility.name,
                    "correctionScope" to finding.correctionScope.name,
                ),
            ),
        )
        return InformationRequestReviewCommandResult(
            request = request,
            review = review,
            reviewETag = loader.reviewETag(review),
            responseETag = InformationRequestETag.responsesOf(request),
            assignment = assignment,
            finding = finding,
        )
    }

    private fun sha256Hex(material: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object
    {
        const val OPERATION = "record-information-request-review-finding"
        val REASON_CODE = Regex("^[a-z0-9][a-z0-9._-]{0,127}$")
    }
}
