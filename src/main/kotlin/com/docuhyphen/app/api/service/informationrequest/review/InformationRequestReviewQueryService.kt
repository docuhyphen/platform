package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReadableReview
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestRespondentReviewResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewCorrectionView
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewQueueEntry
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.*
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestReviewQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val gate: InformationRequestMutationGate,
    private val access: InformationRequestReviewAccess,
    private val loader: InformationRequestReviewLoader,
    private val lockService: InformationRequestSubmissionLockService,
    private val reviewRepository: InformationRequestReviewRepository,
    private val assignmentRepository: InformationRequestReviewAssignmentRepository,
    private val draftRepository: InformationRequestReviewDraftItemRepository,
    private val findingRepository: InformationRequestReviewFindingRepository,
    private val commentRepository: InformationRequestReviewCommentRepository,
    private val correctionRepository: InformationRequestCorrectionRepository,
    private val correctionItemRepository: InformationRequestCorrectionItemRepository,
    private val correctionEvidenceRepository: InformationRequestCorrectionEvidenceRepository,
    private val remediationRepository: InformationRequestReviewRemediationRepository,
    private val requestRepository: InformationRequestRepository,
    private val groupMemberRepository: PrincipalGroupMemberRepository,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
)
{
    fun reviews(requestId: UUID, requestAccess: RequestAccessContext): List<InformationRequestReadableReview>
    {
        queryService.findById(requestId, requestAccess)
        requireReviewReader(requestAccess, requestId)
        return reviewRepository.findForRequest(requestId).map { readable(it, requestAccess) }
    }

    fun review(requestId: UUID, reviewId: UUID, requestAccess: RequestAccessContext): InformationRequestReadableReview
    {
        queryService.findById(requestId, requestAccess)
        requireReviewReader(requestAccess, requestId)
        return readable(loader.requireReview(requestId, reviewId), requestAccess)
    }

    fun results(requestId: UUID, requestAccess: RequestAccessContext): List<InformationRequestRespondentReviewResult>
    {
        val request = queryService.findById(requestId, requestAccess)
        val reviews = reviewRepository.findForRequest(requestId)
        if (reviews.isEmpty()) return emptyList()
        val current = lockService.activePackages(requestId).map { it.id }.toSet()
        val corrections = correctionRepository.findForRequest(requestId)
        val findings = findingRepository.findForReviews(reviews.map { it.id }).groupBy { it.reviewId }
        val comments = commentRepository.findForReviews(reviews.map { it.id }).groupBy { it.reviewId }
        val remediations = remediationRepository.findForRequest(requestId)
        val canAppeal = gate.permitsRequest(requestAccess, Action.INFORMATION_REQUEST_APPEAL_REVIEW, requestId)
        val canComment = gate.permitsRequest(requestAccess, Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW, requestId)
        val latestByPackage =
            reviews.groupBy { it.packageId }.mapValues { entry -> entry.value.maxOf { it.reviewNumber } }
        return reviews.map { review ->
            val view = loader.snapshot(review).submission
            val visible = view.items.map { it.informationRequestRequirementId }
                .filter { gate.permitsRequirement(requestAccess, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it) }
                .toSet()
            val correction = corrections.firstOrNull { it.reviewId == review.id }
            val latest = latestByPackage[review.packageId] == review.reviewNumber
            val reopenable = latest && review.packageId in current && (
                    review.state == InformationRequestReviewState.REJECTED ||
                            (review.state == InformationRequestReviewState.CHANGES_REQUESTED && correction?.state == InformationRequestCorrectionState.OPEN)
                    )
            InformationRequestRespondentReviewResult(
                review = review,
                packageNumber = view.submissionPackage.packageNumber,
                stageKey = view.submissionPackage.stageKey,
                visibleFindings = if (review.state.settled)
                    findings[review.id].orEmpty().filter {
                        it.visibility == InformationRequestReviewVisibility.RESPONDENT_VISIBLE && it.requirementId in visible
                    }
                else emptyList(),
                visibleComments = comments[review.id].orEmpty().filter {
                    it.visibility == InformationRequestReviewVisibility.RESPONDENT_VISIBLE && it.requirementId in visible && review.state.settled
                },
                correction = correction?.let(::correctionView),
                visibleRequirementIds = visible,
                remediations = remediations.filter { remediation ->
                    findings[review.id].orEmpty().any { it.id == remediation.findingId }
                },
                canAppeal = canAppeal && reopenable && !request.state.isTerminal,
                canComment = canComment && review.state.settled && !request.state.isTerminal,
            )
        }
    }

    fun packagePosition(review: InformationRequestReview): Pair<Int, String?>
    {
        val view = loader.snapshot(review).submission.submissionPackage
        return view.packageNumber to view.stageKey
    }

    fun queue(requestAccess: RequestAccessContext): List<InformationRequestReviewQueueEntry>
    {
        val principal = requestAccess.principal
        val direct = assignmentRepository.findActiveForPrincipal(principal.kind, principal.id)
        val throughGroups = groupMemberRepository.findGroupsForPrincipal(principal.kind, principal.id)
            .flatMap { membership ->
                assignmentRepository.findActiveForPrincipal(
                    PrincipalKind.PRINCIPAL_GROUP,
                    membership.principalGroupId
                )
            }
        val assignments = (direct + throughGroups)
            .distinctBy { it.id }
            .filter { it.state == InformationRequestReviewAssignmentState.ACTIVE && it.decidedAt == null }
        val reviews = reviewRepository.findForIds(assignments.map { it.reviewId }.toSet()).associateBy { it.id }
        return assignments.mapNotNull { assignment ->
            val review = reviews[assignment.reviewId]?.takeIf { !it.state.settled } ?: return@mapNotNull null
            val request = requestRepository.findById(review.informationRequestId)?.takeIf { !it.state.isTerminal }
                ?: return@mapNotNull null
            if (!access.permitsReview(requestAccess, request.id)) return@mapNotNull null
            val snapshot = loader.snapshot(review)
            InformationRequestReviewQueueEntry(
                assignment = assignment,
                review = review,
                request = request,
                packageNumber = snapshot.submission.submissionPackage.packageNumber,
                stageKey = snapshot.submission.submissionPackage.stageKey,
                itemCount = snapshot.coverage[assignment.stageKey].orEmpty().size,
            )
        }.sortedWith(compareBy(nullsLast()) { it.assignment.dueAt })
    }

    private fun readable(
        review: InformationRequestReview,
        requestAccess: RequestAccessContext
    ): InformationRequestReadableReview
    {
        val snapshot = loader.snapshot(review)
        val contentVisible = if (access.permitsReview(requestAccess, review.informationRequestId))
            snapshot.submission.items
                .filter { access.permitsItemReview(requestAccess, it.informationRequestRequirementId) }
                .map { it.id }
                .toSet()
        else emptySet()
        val fieldValues = snapshot.submission.items
            .filter { it.id in contentVisible }
            .mapNotNull { item ->
                item.fieldValueRevisionId?.let { id ->
                    fieldValueRevisions.valueOf(id)?.let { id to it }
                }
            }
            .toMap()
        val callerAssignments = snapshot.assignments
            .filter { it.state == InformationRequestReviewAssignmentState.ACTIVE && access.actsAs(it, requestAccess) }
        return InformationRequestReadableReview(
            snapshot = snapshot,
            contentVisibleItemIds = contentVisible,
            fieldValues = fieldValues,
            comments = commentRepository.findForReviews(listOf(review.id)),
            correction = correctionRepository.findForRequest(review.informationRequestId)
                .firstOrNull { it.reviewId == review.id }
                ?.let(::correctionView),
            remediations = remediationRepository.findForRequest(review.informationRequestId)
                .filter { remediation -> snapshot.findings.any { it.id == remediation.findingId } },
            callerAssignmentIds = callerAssignments.map { it.id }.toSet(),
            drafts = callerAssignments.associate { it.id to draftRepository.findForAssignment(it.id) },
            canManage = access.permitsManage(requestAccess, review.informationRequestId),
        )
    }

    private fun correctionView(correction: InformationRequestCorrection) = InformationRequestReviewCorrectionView(
        correction = correction,
        items = correctionItemRepository.findForCorrections(listOf(correction.id)),
        evidence = correctionEvidenceRepository.findForCorrections(listOf(correction.id)),
    )

    private fun requireReviewReader(requestAccess: RequestAccessContext, requestId: UUID)
    {
        if (!access.permitsReview(requestAccess, requestId) && !access.permitsManage(requestAccess, requestId))
        {
            throw ForbiddenException("Access denied to the reviews of this Information Request")
        }
    }
}
