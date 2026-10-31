package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewSnapshot
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStageState
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementAuthorizationContextProvider
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementDisclosure
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.party.InformationRequestPartyQueryService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class InformationRequestReviewAccess @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val partyQueries: InformationRequestPartyQueryService,
    private val assignmentRepository: InformationRequestReviewAssignmentRepository,
    private val reviewRepository: InformationRequestReviewRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
    private val disclosure: InformationRequestRequirementDisclosure,
)
{
    fun requireOpen(review: InformationRequestReview)
    {
        if (review.state.settled)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_SETTLED,
                "This review has settled and records nothing further",
            )
        }
    }

    fun requireAssignment(review: InformationRequestReview, assignmentId: UUID): InformationRequestReviewAssignment =
        assignmentRepository.findById(assignmentId)?.takeIf { it.reviewId == review.id }
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Review assignment not found"
            )

    fun requireActive(assignment: InformationRequestReviewAssignment)
    {
        if (assignment.state != InformationRequestReviewAssignmentState.ACTIVE)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_ASSIGNMENT_INACTIVE,
                "This review assignment is no longer active",
            )
        }
        if (assignment.decidedAt != null)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_ALREADY_DECIDED,
                "This review assignment has already recorded its decisions",
            )
        }
    }

    fun reviewsItem(
        snapshot: InformationRequestReviewSnapshot,
        assignment: InformationRequestReviewAssignment,
        itemId: UUID,
    ): Boolean =
        assignment.state == InformationRequestReviewAssignmentState.ACTIVE &&
                assignment.decidedAt == null &&
                snapshot.standing.stage(assignment.stageKey)?.state == InformationRequestReviewStageState.OPEN &&
                itemId in snapshot.coverage[assignment.stageKey].orEmpty()

    fun requireReviewsItem(
        snapshot: InformationRequestReviewSnapshot,
        assignment: InformationRequestReviewAssignment,
        itemId: UUID,
    ) {
        requireActive(assignment)
        if (snapshot.standing.stage(assignment.stageKey)?.state != InformationRequestReviewStageState.OPEN) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_STAGE_NOT_OPEN,
                "Review stage ${assignment.stageKey} is not open",
            )
        }
        if (itemId !in snapshot.coverage[assignment.stageKey].orEmpty()) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_ITEM_NOT_COVERED,
                "This review stage does not review that item",
            )
        }
    }

    fun reviewerParty(requestId: UUID, partyId: UUID): InformationRequestParty {
        val party = partyQueries.findParty(partyId)?.takeIf {
            it.informationRequestId == requestId &&
                    it.active &&
                    it.roleKey == InformationRequestShareRoleKey.REVIEWER &&
                    it.principalKind != null &&
                    it.principalId != null
        } ?: throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.REVIEWER_NOT_ELIGIBLE,
            "The named party is not an active reviewer of this Information Request",
        )
        if (party.principalKind == PrincipalKind.PARTICIPANT) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEWER_SIGN_IN_REQUIRED,
                "The named reviewer was added by email without an account and cannot review",
            )
        }
        return party
    }

    fun principalsOf(party: InformationRequestParty): Set<PrincipalRef> = requirementContext.principalsActingFor(party)

    fun actsAs(assignment: InformationRequestReviewAssignment, access: RequestAccessContext): Boolean
    {
        val party = partyQueries.findParty(assignment.reviewerPartyId) ?: return false
        return party.active && access.principal in principalsOf(party)
    }

    fun requireActsAs(assignment: InformationRequestReviewAssignment, access: RequestAccessContext)
    {
        if (!actsAs(assignment, access))
        {
            throw ForbiddenException("Only the assigned reviewer may act on this review assignment")
        }
    }

    fun callerAssignments(
        review: InformationRequestReview,
        access: RequestAccessContext
    ): List<InformationRequestReviewAssignment> =
        assignmentRepository.findForReview(review.id)
            .filter { it.state == InformationRequestReviewAssignmentState.ACTIVE && actsAs(it, access) }

    fun permitsReview(access: RequestAccessContext, requestId: UUID): Boolean =
        gate.permitsRequest(access, Action.INFORMATION_REQUEST_REVIEW, requestId)

    fun permitsManage(access: RequestAccessContext, requestId: UUID): Boolean =
        gate.permitsRequest(access, Action.INFORMATION_REQUEST_MANAGE_REVIEWS, requestId)

    fun permitsItemReview(access: RequestAccessContext, requirementId: UUID): Boolean =
        gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW, requirementId)

    fun awaitsVerification(access: RequestAccessContext, requirementId: UUID): Boolean =
        !access.authorization.mfaSatisfied &&
                !permitsItemReview(access, requirementId) &&
                permitsItemReview(
                    access.copy(authorization = access.authorization.copy(mfaSatisfied = true)),
                    requirementId
                )

    fun permitsReviewReading(access: RequestAccessContext, requestId: UUID): Boolean =
        gate.permitsRequest(access, Action.INFORMATION_REQUEST_VIEW_REVIEWS, requestId)

    fun permitsItemReading(access: RequestAccessContext, requirementId: UUID): Boolean =
        gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_VIEW_REVIEW, requirementId)

    fun readsReviewedItem(
        access: RequestAccessContext,
        requestId: UUID,
        packageId: UUID,
        requirementId: UUID
    ): Boolean =
        permitsReviewReading(access, requestId) &&
                permitsItemReading(access, requirementId) &&
                reviewRepository.existsForPackage(requestId, packageId)

    fun verifiedForItem(access: RequestAccessContext, requestId: UUID, requirementId: UUID): Boolean =
        disclosure.verifiedFor(access, requestId, requirementId)

    fun awaitsReadingVerification(access: RequestAccessContext, requirementId: UUID): Boolean =
        !access.authorization.mfaSatisfied &&
                !permitsItemReading(access, requirementId) &&
                permitsItemReading(
                    access.copy(authorization = access.authorization.copy(mfaSatisfied = true)),
                    requirementId
                )

    fun requireReviewReader(access: RequestAccessContext, requestId: UUID) {
        if (READER_ACTIONS.none { gate.permitsRequest(access, it, requestId) }) {
            throw ForbiddenException("Access denied to the reviews of this Information Request")
        }
    }

    fun requireItemReview(access: RequestAccessContext, requirementId: UUID) {
        if (permitsItemReview(access, requirementId)) return
        if (awaitsVerification(access, requirementId)) disclosure.refuseUnverified(access)
        throw ForbiddenException("Access denied to review this Information Request Requirement")
    }

    private companion object {
        val READER_ACTIONS = listOf(
            Action.INFORMATION_REQUEST_VIEW_REVIEWS,
            Action.INFORMATION_REQUEST_VIEW_OPERATIONS,
        )
    }
}
