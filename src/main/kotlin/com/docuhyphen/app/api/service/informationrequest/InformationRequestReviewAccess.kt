package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestReview
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignment
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestPartyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewAssignmentRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.UUID

@ApplicationScoped
class InformationRequestReviewAccess @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val partyRepository: InformationRequestPartyRepository,
    private val assignmentRepository: InformationRequestReviewAssignmentRepository,
    private val requirementContext: InformationRequestRequirementAuthorizationContextProvider,
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
            ?: throw InformationRequestLifecycleException(InformationRequestErrorCatalog.NOT_FOUND, "Review assignment not found")

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

    fun reviewerParty(requestId: UUID, partyId: UUID): InformationRequestParty =
        partyRepository.findById(partyId)?.takeIf {
            it.informationRequestId == requestId &&
                it.active &&
                it.roleKey == InformationRequestShareRoleKey.REVIEWER &&
                it.principalKind != null &&
                it.principalId != null
        } ?: throw InformationRequestLifecycleException(
            InformationRequestErrorCatalog.REVIEWER_NOT_ELIGIBLE,
            "The named party is not an active reviewer of this Information Request",
        )

    fun principalsOf(party: InformationRequestParty): Set<PrincipalRef> = requirementContext.principalsActingFor(party)

    fun actsAs(assignment: InformationRequestReviewAssignment, access: RequestAccessContext): Boolean
    {
        val party = partyRepository.findById(assignment.reviewerPartyId) ?: return false
        return party.active && access.principal in principalsOf(party)
    }

    fun requireActsAs(assignment: InformationRequestReviewAssignment, access: RequestAccessContext)
    {
        if (!actsAs(assignment, access))
        {
            throw ForbiddenException("Only the assigned reviewer may act on this review assignment")
        }
    }

    fun callerAssignments(review: InformationRequestReview, access: RequestAccessContext): List<InformationRequestReviewAssignment> =
        assignmentRepository.findForReview(review.id).filter { it.state == InformationRequestReviewAssignmentState.ACTIVE && actsAs(it, access) }

    fun permitsReview(access: RequestAccessContext, requestId: UUID): Boolean =
        gate.permitsRequest(access, Action.INFORMATION_REQUEST_REVIEW, requestId)

    fun permitsManage(access: RequestAccessContext, requestId: UUID): Boolean =
        gate.permitsRequest(access, Action.INFORMATION_REQUEST_MANAGE_REVIEWS, requestId)

    fun permitsItemReview(access: RequestAccessContext, requirementId: UUID): Boolean =
        gate.permitsRequirement(access, Action.INFORMATION_REQUEST_REQUIREMENT_REVIEW, requirementId)
}
