package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.exchange.ExchangeLookupService
import com.docuhyphen.app.api.service.fields.FieldValueRevisionQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.InformationRequestQueryService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestTitleReader
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceQueryService
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionStandingService
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestCallerPartyResolver
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestReviewQueryService @Inject constructor(
    private val queryService: InformationRequestQueryService,
    private val gate: InformationRequestMutationGate,
    private val access: InformationRequestReviewAccess,
    private val loader: InformationRequestReviewLoader,
    private val reopenPolicy: InformationRequestReviewReopenPolicy,
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
    private val callerParties: InformationRequestCallerPartyResolver,
    private val standingService: InformationRequestExecutionStandingService,
    private val fieldValueRevisions: FieldValueRevisionQueryService,
    private val evidenceQueries: InformationRequestEvidenceQueryService,
    private val delegateCandidates: InformationRequestReviewDelegateCandidates,
    private val dispositions: InformationRequestTemplateBindingDispositionRepository,
    private val titles: InformationRequestTitleReader,
    private val exchangeLookup: ExchangeLookupService,
)
{
    fun reviews(requestId: UUID, requestAccess: RequestAccessContext): List<InformationRequestReadableReview>
    {
        val request = queryService.findById(requestId, requestAccess)
        access.requireReviewReader(requestAccess, requestId)
        return reviewRepository.findForRequest(requestId).map { readable(request, it, requestAccess) }
    }

    fun review(requestId: UUID, reviewId: UUID, requestAccess: RequestAccessContext): InformationRequestReadableReview
    {
        val request = queryService.findById(requestId, requestAccess)
        access.requireReviewReader(requestAccess, requestId)
        return readable(request, loader.requireReview(requestId, reviewId), requestAccess)
    }

    fun results(requestId: UUID, requestAccess: RequestAccessContext): List<InformationRequestRespondentReviewResult>
    {
        val request = queryService.findById(requestId, requestAccess)
        val reviews = reviewRepository.findForRequest(requestId)
        if (reviews.isEmpty()) return emptyList()
        val reopenFacts = reopenPolicy.factsFor(requestId)
        val findings = findingRepository.findForReviews(reviews.map { it.id }).groupBy { it.reviewId }
        val comments = commentRepository.findForReviews(reviews.map { it.id }).groupBy { it.reviewId }
        val remediations = remediationRepository.findForRequest(requestId)
        val canAppeal = gate.permitsRequest(requestAccess, Action.INFORMATION_REQUEST_APPEAL_REVIEW, requestId)
        val canComment = gate.permitsRequest(requestAccess, Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW, requestId)
        return reviews.map { review ->
            val snapshot = loader.snapshot(review)
            val view = snapshot.submission
            val visible = view.items.map { it.informationRequestRequirementId }
                .filter { gate.permitsRequirement(requestAccess, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW, it) }
                .toSet()
            val coveredRequirements = snapshot.reviewedItems.map { it.informationRequestRequirementId }
            val correction = reopenFacts.correctionByReview[review.id]
            val reopenable = reopenPolicy.reopenable(review, reopenFacts)
            val visibleFindings = if (review.state.decided)
                findings[review.id].orEmpty().filter {
                    it.visibility == InformationRequestReviewVisibility.RESPONDENT_VISIBLE && it.requirementId in visible
                }
            else emptyList()
            val visibleFindingIds = visibleFindings.map { it.id }.toSet()
            InformationRequestRespondentReviewResult(
                review = review,
                packageNumber = view.submissionPackage.packageNumber,
                stageKey = view.submissionPackage.stageKey,
                visibleFindings = visibleFindings,
                visibleComments = comments[review.id].orEmpty().filter {
                    it.visibility == InformationRequestReviewVisibility.RESPONDENT_VISIBLE && it.requirementId in visible && review.state.decided
                },
                correction = correction?.let(::correctionView),
                visibleRequirementIds = visible,
                remediations = remediations.filter { it.findingId in visibleFindingIds },
                canAppeal = canAppeal && reopenable && !request.state.isTerminal,
                canComment = canComment && review.state.decided && !request.state.isTerminal,
                openingReasonVisible = openedBy(review, requestAccess) ||
                        (review.kind == InformationRequestReviewKind.APPEAL && coversOnly(
                            coveredRequirements,
                            visible
                        )),
                visibleItems = view.items.filter { it.informationRequestRequirementId in visible }
                    .map { InformationRequestReviewResultItem(it.id, it.informationRequestRequirementId) },
            )
        }
    }

    fun packagePosition(review: InformationRequestReview): Pair<Int, String?>
    {
        val view = loader.snapshot(review).submission.submissionPackage
        return view.packageNumber to view.stageKey
    }

    fun commandOpeningReasonVisible(review: InformationRequestReview, requestAccess: RequestAccessContext): Boolean
    {
        if (openedBy(review, requestAccess)) return true
        if (!access.permitsReviewReading(requestAccess, review.informationRequestId)) return false
        val items = loader.snapshot(review).reviewedItems
        return items.isNotEmpty() && items.all {
            access.permitsItemReading(requestAccess, it.informationRequestRequirementId)
        }
    }

    fun queue(
        requestAccess: RequestAccessContext,
        requestIds: Set<UUID>? = null
    ): List<InformationRequestReviewQueueEntry> {
        val reviewerPartyIds = callerParties.activePartiesActedFor(requestAccess.principal)
            .filter { it.roleKey == InformationRequestShareRoleKey.REVIEWER }
            .map { it.id }
        val assignments = assignmentRepository.findActiveForReviewerParties(reviewerPartyIds)
            .filter {
                it.decidedAt == null && (requestIds == null || it.informationRequestId in requestIds) && access.actsAs(
                    it,
                    requestAccess
                )
            }
        val reviews = reviewRepository.findForIds(assignments.map { it.reviewId }.toSet()).associateBy { it.id }
        val pending = assignments.mapNotNull { assignment ->
            val review = reviews[assignment.reviewId]?.takeIf { !it.state.settled } ?: return@mapNotNull null
            val request = requestRepository.findById(review.informationRequestId)?.takeIf { !it.state.isTerminal }
                ?: return@mapNotNull null
            if (!access.permitsReview(requestAccess, request.id)) return@mapNotNull null
            Triple(assignment, review, request)
        }
        val standings = standingService.standingsOf(pending.map { it.third }.distinctBy { it.id })
        val requestTitles = titles.titlesOf(pending.map { it.third }.distinctBy { it.id })
        val exchangeNames =
            pending.map { it.third.exchangeId }.distinct().associateWith { exchangeLookup.findExchange(it)?.name }
        return pending.mapNotNull { (assignment, review, request) ->
            val snapshot = loader.snapshot(review)
            if (snapshot.standing.stage(assignment.stageKey)?.state != InformationRequestReviewStageState.OPEN) return@mapNotNull null
            InformationRequestReviewQueueEntry(
                assignment = assignment,
                review = review,
                request = request,
                packageNumber = snapshot.submission.submissionPackage.packageNumber,
                requestTitle = requestTitles.getValue(request.id),
                exchangeName = exchangeNames[request.exchangeId],
                reviewStageTitle = requireNotNull(snapshot.plan.stage(assignment.stageKey)).title,
                stageKey = snapshot.submission.submissionPackage.stageKey,
                itemCount = snapshot.coverage[assignment.stageKey].orEmpty().size,
                executionStanding = standings.getValue(request.id).forParticipant(),
            )
        }
            .sortedWith(compareBy<InformationRequestReviewQueueEntry, java.sql.Timestamp?>(nullsLast()) { it.assignment.dueAt }
                .thenBy { it.assignment.assignedAt }.thenBy { it.assignment.id })
    }

    private fun readable(
        request: InformationRequest,
        review: InformationRequestReview,
        requestAccess: RequestAccessContext,
    ): InformationRequestReadableReview
    {
        val snapshot = loader.snapshot(review)
        val readsItems = access.permitsReviewReading(requestAccess, review.informationRequestId)
        val contentVisible = if (readsItems)
            snapshot.submission.items
                .filter { access.permitsItemReading(requestAccess, it.informationRequestRequirementId) }
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
        val canManage = access.permitsManage(requestAccess, review.informationRequestId)
        val callerAssignments = snapshot.assignments
            .filter { !request.state.isTerminal && !review.state.settled }
            .filter { it.state == InformationRequestReviewAssignmentState.ACTIVE && access.actsAs(it, requestAccess) }
        val reviewableRequirements = snapshot.submission.items
            .filter { it.id in contentVisible }
            .map { it.informationRequestRequirementId }
            .toSet()
        val earlierFindings = loader.earlierFindings(review).filter { it.requirementId in reviewableRequirements }
        val earlierFindingIds = earlierFindings.map { it.id }.toSet()
        val managesRecords = gate.permitsRequest(requestAccess, Action.INFORMATION_REQUEST_VIEW_OPERATIONS, request.id)
        fun ownRecord(kind: PrincipalKind, principalId: UUID, requirementId: UUID): Boolean =
            managesRecords && kind == requestAccess.principal.kind && principalId == requestAccess.principal.id &&
                    access.verifiedForItem(requestAccess, request.id, requirementId)

        val findings = snapshot.findings.filter {
            it.submissionItemId in contentVisible || ownRecord(
                it.recordedByPrincipalKind,
                it.recordedByPrincipalId,
                it.requirementId
            )
        }
        val findingIds = findings.map { it.id }.toSet()
        val remediations = remediationRepository.findForRequest(review.informationRequestId)
        val callerAssignmentIds = callerAssignments.map { it.id }.toSet()
        val delegatesOwn =
            callerAssignmentIds.isNotEmpty() && access.permitsReview(requestAccess, review.informationRequestId)
        val delegable = if (request.state.isTerminal) emptyList()
        else snapshot.assignments.filter { canManage || (delegatesOwn && it.id in callerAssignmentIds) }
        return InformationRequestReadableReview(
            snapshot = snapshot,
            contentVisibleItemIds = contentVisible,
            fieldValues = fieldValues,
            evidenceVersions = evidenceQueries.versionRecords(
                snapshot.submission.evidence.filter { it.itemId in contentVisible }.map { it.evidenceVersionId },
            ),
            findings = findings,
            comments = commentRepository.findForReviews(listOf(review.id)).filter {
                it.submissionItemId in contentVisible || ownRecord(
                    it.authorPrincipalKind,
                    it.authorPrincipalId,
                    it.requirementId
                )
            },
            correction = correctionRepository.findForRequest(review.informationRequestId)
                .firstOrNull { it.reviewId == review.id }
                ?.let(::correctionView),
            remediations = remediations.filter { it.findingId in findingIds },
            earlierFindings = earlierFindings,
            earlierRemediations = remediations.filter { it.findingId in earlierFindingIds },
            callerAssignmentIds = callerAssignmentIds,
            drafts = callerAssignments.associate { assignment ->
                assignment.id to draftRepository.findForAssignment(assignment.id)
                    .filter { it.submissionItemId in contentVisible }
            },
            canManage = canManage,
            canComment = gate.permitsMutation(
                request,
                InformationRequestMutation.RECORD_REVIEW_COMMENT,
                InformationRequestContinuationAction.REVIEW
            ) &&
                    (canManage || (access.permitsReview(requestAccess, request.id) && access.callerAssignments(
                        review,
                        requestAccess
                    ).isNotEmpty()) ||
                            (review.state.decided && gate.permitsRequest(
                                requestAccess,
                                Action.INFORMATION_REQUEST_COMMENT_ON_REVIEW,
                                request.id
                            ))),
            waiverPermittedRequirementIds = dispositions.findForVersion(review.templateVersionId)
                .filter { it.disposition == InformationRequestResponseDisposition.WAIVED }.map { it.templateBindingId }
                .toSet()
                .let { bindings ->
                    snapshot.submission.items.filter { it.templateBindingId in bindings }
                        .map { it.informationRequestRequirementId }.toSet()
                },
            canReconsider = canManage && !request.state.isTerminal && reopenPolicy.reopenable(review),
            canRequestSupplement = SUPPLEMENT_ACTIONS.any { gate.permitsRequest(requestAccess, it, request.id) } &&
                    gate.permitsMutation(
                        request,
                        InformationRequestMutation.CREATE_SUCCESSOR,
                        InformationRequestContinuationAction.SUPERSEDE,
                    ),
            requestETag = InformationRequestETag.aggregateOf(request),
            openingReasonVisible = openedBy(review, requestAccess) ||
                    coversOnly(snapshot.reviewedItems.map { it.id }, contentVisible),
            itemsAwaitingStepUp = if (readsItems)
                snapshot.reviewedItems.count {
                    it.id !in contentVisible && access.awaitsReadingVerification(
                        requestAccess,
                        it.informationRequestRequirementId
                    )
                }
            else 0,
            delegationCandidates = delegable
                .associate { it.id to delegateCandidates.candidatesFor(snapshot, it) }
                .filterValues { it.isNotEmpty() },
        )
    }

    private fun openedBy(review: InformationRequestReview, requestAccess: RequestAccessContext): Boolean =
        review.openedByPrincipalKind == requestAccess.principal.kind && review.openedByPrincipalId == requestAccess.principal.id

    private fun coversOnly(covered: Collection<UUID>, readable: Set<UUID>): Boolean =
        covered.isNotEmpty() && readable.containsAll(covered)

    private fun correctionView(correction: InformationRequestCorrection): InformationRequestReviewCorrectionView {
        val returned = correctionEvidenceRepository.findForCorrections(listOf(correction.id))
        val versions = evidenceQueries.versionRecords(returned.map { it.evidenceVersionId })
        return InformationRequestReviewCorrectionView(
            correction = correction,
            items = correctionItemRepository.findForCorrections(listOf(correction.id)),
            evidence = returned.map { file ->
                val version = versions.getValue(file.evidenceVersionId)
                InformationRequestReturnedEvidence(
                    correctionItemId = file.correctionItemId,
                    evidenceArtifactId = file.evidenceArtifactId,
                    evidenceVersionId = file.evidenceVersionId,
                    versionNumber = version.versionNumber,
                    declaredFileName = version.declaredFileName,
                )
            },
        )
    }

    private companion object {
        val SUPPLEMENT_ACTIONS =
            listOf(Action.INFORMATION_REQUEST_SUPERSEDE, Action.INFORMATION_REQUEST_REQUEST_SUPPLEMENT)
    }
}
