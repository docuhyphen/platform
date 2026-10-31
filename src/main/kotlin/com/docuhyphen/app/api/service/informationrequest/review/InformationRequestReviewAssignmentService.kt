package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.canonicalForm
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestReviewAssignmentService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val access: InformationRequestReviewAccess,
    private val loader: InformationRequestReviewLoader,
    private val separation: InformationRequestReviewSeparationPolicy,
    private val settlement: InformationRequestReviewSettlement,
    private val reviewRepository: InformationRequestReviewRepository,
    private val assignmentRepository: InformationRequestReviewAssignmentRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
    private val entityManager: EntityManager,
)
{
    @Transactional
    fun assign(command: AssignInformationRequestReviewerCommand): InformationRequestReviewCommandResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val receipt = receipt(
            ResourceRef(ResourceType.INFORMATION_REQUEST_REVIEW, command.reviewId),
            ASSIGN_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(
                command.stageKey.trim(),
                command.reviewerPartyId,
                command.dueAt,
                command.precondition.canonicalForm()
            ),
        )
        return run(locked, command.access, receipt) {
            gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_REVIEWS), command.requestId)
            gate.requireMutation(locked, InformationRequestMutation.ASSIGN_REVIEWER)
            gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
            val review = loader.requireReview(command.requestId, command.reviewId)
            access.requireOpen(review)
            command.precondition.requireSatisfiedBy(loader.reviewETag(review))
            val snapshot = loader.snapshot(review)
            val stage = snapshot.plan.stage(command.stageKey.trim())
                ?: throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_STAGE_UNKNOWN,
                    "This review has no stage ${command.stageKey}",
                )
            requireStageUnsettled(snapshot, stage.stageKey)
            val party = access.reviewerParty(command.requestId, command.reviewerPartyId)
            requireNotAssigned(snapshot.assignments, stage.stageKey, party)
            separation.requireMayReview(snapshot, stage, access.principalsOf(party))
            val now = Timestamp.from(clock.instant())
            val dueAt = command.dueAt?.let { due ->
                if (!due.isAfter(clock.instant()))
                {
                    throw InformationRequestCommandRequestException("A review due instant must be in the future")
                }
                Timestamp.from(due)
            }
            val assignment = assignmentRepository.save(
                InformationRequestReviewAssignment().apply {
                    reviewId = review.id
                    informationRequestId = review.informationRequestId
                    templateReviewStageId = stage.id
                    stageKey = stage.stageKey
                    reviewerPartyId = party.id
                    reviewerPrincipalKind = requireNotNull(party.principalKind)
                    reviewerPrincipalId = requireNotNull(party.principalId)
                    this.dueAt = dueAt
                    state = InformationRequestReviewAssignmentState.ACTIVE
                    draftRevision = 1
                    assignedByPrincipalKind = command.access.principal.kind
                    assignedByPrincipalId = command.access.principal.id
                    assignedAt = now
                },
            )
            advance(review, InformationRequestReviewState.IN_REVIEW)
            record(
                locked,
                review,
                assignment,
                InformationRequestAuditActor.of(command.access),
                command.idempotencyKey,
                "ASSIGNED",
                null
            )
            settlement.settleIfDecided(
                locked,
                review,
                InformationRequestAuditActor.of(command.access),
                command.idempotencyKey
            )
            result(locked, review, assignment)
        }
    }

    @Transactional
    fun change(command: ChangeInformationRequestReviewAssignmentCommand): InformationRequestReviewCommandResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val receipt = receipt(
            ResourceRef(ResourceType.INFORMATION_REQUEST_REVIEW_ASSIGNMENT, command.assignmentId),
            CHANGE_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(
                command.change,
                command.reasonCode?.trim().orEmpty(),
                command.delegatePartyId,
                command.narrative?.trim().orEmpty(),
                command.precondition.canonicalForm(),
            ),
        )
        return run(locked, command.access, receipt) {
            gate.authorizeRequest(
                command.access,
                listOf(Action.INFORMATION_REQUEST_REVIEW, Action.INFORMATION_REQUEST_MANAGE_REVIEWS),
                command.requestId,
            )
            gate.requireMutation(locked, InformationRequestMutation.ASSIGN_REVIEWER)
            gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
            val review = loader.requireReview(command.requestId, command.reviewId)
            access.requireOpen(review)
            val assignment = access.requireAssignment(review, command.assignmentId)
            authorizeChange(command, assignment)
            command.precondition.requireSatisfiedBy(loader.reviewETag(review))
            access.requireActive(assignment)
            InformationRequestReviewInputLimits.requireText(
                command.reasonCode,
                "An assignment reason code",
                InformationRequestReviewInputLimits.MAXIMUM_REASON_LENGTH
            )
            InformationRequestReviewInputLimits.requireText(command.narrative, "An assignment change narrative")
            val reason = requireReason(command)
            val now = Timestamp.from(clock.instant())
            val delegate = when (command.change)
            {
                InformationRequestReviewAssignmentChange.RECUSAL ->
                {
                    close(assignment, InformationRequestReviewAssignmentState.RECUSED, reason, command, now)
                    null
                }

                InformationRequestReviewAssignmentChange.DELEGATION -> delegate(
                    review,
                    assignment,
                    command,
                    reason,
                    now
                )

                InformationRequestReviewAssignmentChange.REVOCATION -> {
                    close(assignment, InformationRequestReviewAssignmentState.REVOKED, reason, command, now)
                    null
                }
            }
            review.reviewRevision += 1
            reviewRepository.update(review)
            record(
                locked,
                review,
                assignment,
                InformationRequestAuditActor.of(command.access),
                command.idempotencyKey,
                command.change.name,
                reason,
                delegate?.let {
                    mapOf(
                        "delegateAssignmentId" to it.id.toString(),
                        "delegatePartyId" to it.reviewerPartyId.toString()
                    )
                }.orEmpty(),
            )
            settlement.settleIfDecided(
                locked,
                review,
                InformationRequestAuditActor.of(command.access),
                command.idempotencyKey
            )
            result(locked, review, delegate ?: assignment)
        }
    }

    private fun authorizeChange(
        command: ChangeInformationRequestReviewAssignmentCommand,
        assignment: InformationRequestReviewAssignment
    )
    {
        val manages = access.permitsManage(command.access, command.requestId)
        when (command.change)
        {
            InformationRequestReviewAssignmentChange.REVOCATION ->
                if (!manages) throw ForbiddenException("Only a request administrator may revoke a review assignment")

            InformationRequestReviewAssignmentChange.RECUSAL ->
            {
                gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_REVIEW), command.requestId)
                access.requireActsAs(assignment, command.access)
            }

            InformationRequestReviewAssignmentChange.DELEGATION ->
                if (!manages)
                {
                    gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_REVIEW), command.requestId)
                    access.requireActsAs(assignment, command.access)
                }
        }
    }

    private fun requireReason(command: ChangeInformationRequestReviewAssignmentCommand): String =
        command.reasonCode?.trim()?.ifBlank { null }
            ?: throw InformationRequestCommandRequestException(
                when (command.change) {
                    InformationRequestReviewAssignmentChange.RECUSAL -> "A recusal states its reason"
                    InformationRequestReviewAssignmentChange.DELEGATION -> "A delegation states its reason"
                    InformationRequestReviewAssignmentChange.REVOCATION -> "A revocation states its reason"
                },
            )

    private fun delegate(
        review: InformationRequestReview,
        assignment: InformationRequestReviewAssignment,
        command: ChangeInformationRequestReviewAssignmentCommand,
        reason: String,
        now: Timestamp,
    ): InformationRequestReviewAssignment
    {
        val delegatePartyId = command.delegatePartyId
            ?: throw InformationRequestCommandRequestException("A delegation names the reviewer party it hands the assignment to")
        val snapshot = loader.snapshot(review)
        val stage = snapshot.plan.stage(assignment.stageKey)
            ?: throw IllegalStateException("Review assignment names a stage its Template Version no longer states")
        requireStageUnsettled(snapshot, stage.stageKey)
        val party = access.reviewerParty(review.informationRequestId, delegatePartyId)
        requireNotAssigned(snapshot.assignments, stage.stageKey, party)
        separation.requireMayReview(snapshot, stage, access.principalsOf(party))
        close(assignment, InformationRequestReviewAssignmentState.DELEGATED, reason, command, now)
        entityManager.flush()
        return assignmentRepository.save(
            InformationRequestReviewAssignment().apply {
                reviewId = review.id
                informationRequestId = review.informationRequestId
                templateReviewStageId = assignment.templateReviewStageId
                stageKey = assignment.stageKey
                reviewerPartyId = party.id
                reviewerPrincipalKind = requireNotNull(party.principalKind)
                reviewerPrincipalId = requireNotNull(party.principalId)
                delegatedFromAssignmentId = assignment.id
                dueAt = assignment.dueAt
                state = InformationRequestReviewAssignmentState.ACTIVE
                draftRevision = 1
                assignedByPrincipalKind = command.access.principal.kind
                assignedByPrincipalId = command.access.principal.id
                assignedAt = now
            },
        )
    }

    private fun requireStageUnsettled(snapshot: InformationRequestReviewSnapshot, stageKey: String) {
        if (snapshot.standing.stage(stageKey)?.state == InformationRequestReviewStageState.SETTLED) {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_STAGE_NOT_OPEN,
                "Review stage $stageKey has settled",
            )
        }
    }

    private fun close(
        assignment: InformationRequestReviewAssignment,
        state: InformationRequestReviewAssignmentState,
        reasonCode: String?,
        command: ChangeInformationRequestReviewAssignmentCommand,
        now: Timestamp,
    )
    {
        assignment.state = state
        assignment.changeReasonCode = reasonCode
        assignment.changeNarrative = command.narrative?.trim()?.ifBlank { null }
        assignment.changedByPrincipalKind = command.access.principal.kind
        assignment.changedByPrincipalId = command.access.principal.id
        assignment.changedAt = now
        assignmentRepository.update(assignment)
    }

    private fun requireNotAssigned(
        assignments: List<InformationRequestReviewAssignment>,
        stageKey: String,
        party: InformationRequestParty,
    )
    {
        if (InformationRequestReviewStageMembership.holds(assignments, stageKey, party))
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEWER_ALREADY_ASSIGNED,
                "This reviewer is already assigned to that review stage",
            )
        }
    }

    private fun advance(review: InformationRequestReview, state: InformationRequestReviewState)
    {
        if (review.state == InformationRequestReviewState.PENDING) review.state = state
        review.reviewRevision += 1
        reviewRepository.update(review)
    }

    @Suppress("LongParameterList")
    private fun record(
        locked: LockedInformationRequest,
        review: InformationRequestReview,
        assignment: InformationRequestReviewAssignment,
        actor: InformationRequestAuditActor,
        idempotencyKey: String,
        change: String,
        reasonCode: String?,
        additionalDetails: Map<String, String> = emptyMap(),
    )
    {
        val request = locked.request
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = InformationRequestMutation.ASSIGN_REVIEWER,
                actor = actor,
                reasonCode = reasonCode,
                partyId = assignment.reviewerPartyId,
                auditIdempotencyKey = "information_request.review_assignment|${assignment.id}|$change|$idempotencyKey",
                details = mapOf(
                    "reviewId" to review.id.toString(),
                    "assignmentId" to assignment.id.toString(),
                    "stageKey" to assignment.stageKey,
                    "assignmentChange" to change,
                ) + additionalDetails,
            ),
        )
    }

    private fun receipt(
        resource: ResourceRef,
        operation: String,
        requestAccess: RequestAccessContext,
        idempotencyKey: String,
        material: List<Any?>,
    ) = CommandReceiptRequest(
        resource = resource,
        operation = operation,
        actor = CommandActorRef.principal(requestAccess.principal),
        idempotencyKey = idempotencyKey,
        requestFingerprint = CommandRequestFingerprint.of(operation, resource.id, *material.toTypedArray()),
    )

    private fun run(
        locked: LockedInformationRequest,
        requestAccess: RequestAccessContext,
        receipt: CommandReceiptRequest,
        mutation: () -> InformationRequestReviewCommandResult,
    ): InformationRequestReviewCommandResult =
        when (val decision = commandReceiptService.runOnce(receipt) {
            val result = mutation()
            CommandMutationResult(
                result,
                CommandResultReference(
                    resourceType = ResourceType.INFORMATION_REQUEST_REVIEW_ASSIGNMENT,
                    resourceId = requireNotNull(result.assignment).id,
                    revision = result.review.reviewRevision,
                    etag = result.reviewETag,
                ),
            )
        })
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed -> replay(locked, requestAccess, decision.result)
        }

    private fun replay(
        locked: LockedInformationRequest,
        requestAccess: RequestAccessContext,
        recorded: CommandResultReference,
    ): InformationRequestReviewCommandResult
    {
        require(recorded.resourceType == ResourceType.INFORMATION_REQUEST_REVIEW_ASSIGNMENT) {
            "Command receipt does not reference a review assignment"
        }
        access.requireReviewReader(requestAccess, locked.request.id)
        val assignment = assignmentRepository.findById(recorded.resourceId)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.NOT_FOUND,
                "Review assignment not found"
            )
        val review = loader.requireReview(locked.request.id, assignment.reviewId)
        return result(locked, review, assignment)
    }

    private fun result(
        locked: LockedInformationRequest,
        review: InformationRequestReview,
        assignment: InformationRequestReviewAssignment,
    ) = InformationRequestReviewCommandResult(
        request = locked.request,
        review = review,
        reviewETag = loader.reviewETag(review),
        responseETag = InformationRequestETag.responsesOf(locked.request),
        assignment = assignment,
        draftETag = loader.draftETag(assignment),
    )

    private companion object
    {
        const val ASSIGN_OPERATION = "assign-information-request-reviewer"
        const val CHANGE_OPERATION = "change-information-request-review-assignment"
    }
}
