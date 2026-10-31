package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.InformationRequestCommandRequestException
import com.docuhyphen.app.api.model.command.*
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAuditActor
import com.docuhyphen.app.api.model.informationrequest.InformationRequestItemProblem
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestTransitionHistoryCommand
import com.docuhyphen.app.api.model.informationrequest.review.*
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewAssignmentRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDecisionRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewDraftItemRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.command.CommandReceiptService
import com.docuhyphen.app.api.service.command.CommandRequestFingerprint
import com.docuhyphen.app.api.service.command.canonicalForm
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.access.InformationRequestRequirementDisclosure
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestTransitionHistoryService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.sql.Timestamp
import java.time.Clock

@ApplicationScoped
class InformationRequestReviewDecisionService @Inject constructor(
    private val gate: InformationRequestMutationGate,
    private val access: InformationRequestReviewAccess,
    private val loader: InformationRequestReviewLoader,
    private val separation: InformationRequestReviewSeparationPolicy,
    private val settlement: InformationRequestReviewSettlement,
    private val reviewRepository: InformationRequestReviewRepository,
    private val assignmentRepository: InformationRequestReviewAssignmentRepository,
    private val draftRepository: InformationRequestReviewDraftItemRepository,
    private val decisionRepository: InformationRequestReviewDecisionRepository,
    private val dispositionRepository: InformationRequestTemplateBindingDispositionRepository,
    private val commandReceiptService: CommandReceiptService,
    private val transitionHistory: InformationRequestTransitionHistoryService,
    private val clock: Clock,
    private val entityManager: EntityManager,
    private val disclosure: InformationRequestRequirementDisclosure,
)
{
    @Transactional
    fun saveDraft(command: SaveInformationRequestReviewDraftCommand): InformationRequestReviewDraftResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_REVIEW), command.requestId)
        gate.requireMutation(locked, InformationRequestMutation.SAVE_REVIEW_DRAFT)
        gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
        val review = loader.requireReview(command.requestId, command.reviewId)
        access.requireOpen(review)
        val assignment = access.requireAssignment(review, command.assignmentId)
        access.requireActsAs(assignment, command.access)
        access.requireActive(assignment)
        command.precondition.requireSatisfiedBy(loader.draftETag(assignment))
        val snapshot = loader.snapshot(review)
        requireStageOpen(snapshot, assignment.stageKey)
        val covered = openItems(snapshot, assignment.stageKey).toSet()
        InformationRequestReviewInputLimits.requireWorksheet(command.patches)
        val existing = draftRepository.findForAssignment(assignment.id).associateBy { it.submissionItemId }
        val now = Timestamp.from(clock.instant())
        command.patches.forEach { patch ->
            if (patch.submissionItemId !in covered)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_ITEM_NOT_COVERED,
                    "This review stage does not review that item",
                )
            }
            val item = requireNotNull(snapshot.item(patch.submissionItemId))
            requireItemReview(command.access, item)
            val current = existing[patch.submissionItemId]
            when
            {
                patch.clear -> current?.let(draftRepository::delete)
                else ->
                {
                    val outcome = patch.outcome
                        ?: throw InformationRequestCommandRequestException("A worksheet entry states an outcome")
                    val entry = current ?: InformationRequestReviewDraftItem().apply {
                        assignmentId = assignment.id
                        reviewId = review.id
                        submissionItemId = item.id
                    }
                    entry.outcome = outcome
                    entry.narrative = patch.narrative?.trim()?.ifBlank { null }
                    entry.updatedAt = now
                    if (current == null) draftRepository.save(entry) else draftRepository.update(entry)
                }
            }
        }
        assignment.draftRevision += 1
        assignmentRepository.update(assignment)
        entityManager.flush()
        record(
            locked,
            InformationRequestMutation.SAVE_REVIEW_DRAFT,
            InformationRequestAuditActor.of(command.access),
            "information_request.review_draft|${assignment.id}|${assignment.draftRevision}",
            mapOf(
                "reviewId" to review.id.toString(),
                "assignmentId" to assignment.id.toString(),
                "worksheetEntryCount" to command.patches.size.toString(),
            ),
        )
        return InformationRequestReviewDraftResult(
            assignment = assignment,
            items = draftRepository.findForAssignment(assignment.id).filter { entry ->
                snapshot.item(entry.submissionItemId)
                    ?.let { access.permitsItemReview(command.access, it.informationRequestRequirementId) } == true
            },
            draftETag = loader.draftETag(assignment),
        )
    }

    @Transactional
    fun recordDecisions(command: RecordInformationRequestReviewDecisionsCommand): InformationRequestReviewCommandResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val receipt = receipt(
            ResourceRef(ResourceType.INFORMATION_REQUEST_REVIEW_ASSIGNMENT, command.assignmentId),
            RECORD_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(command.precondition.canonicalForm()),
        )
        return run(
            locked,
            command.access,
            receipt,
            { review -> access.requireAssignment(review, command.assignmentId) }) {
            gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_REVIEW), command.requestId)
            gate.requireMutation(locked, InformationRequestMutation.RECORD_REVIEW_DECISION)
            gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
            val review = loader.requireReview(command.requestId, command.reviewId)
            access.requireOpen(review)
            val assignment = access.requireAssignment(review, command.assignmentId)
            access.requireActsAs(assignment, command.access)
            access.requireActive(assignment)
            command.precondition.requireSatisfiedBy(loader.draftETag(assignment))
            val snapshot = loader.snapshot(review)
            val stage = requireStageOpen(snapshot, assignment.stageKey)
            separation.requireMayReview(snapshot, stage, setOf(command.access.principal))
            requireFirstDecisionInStage(snapshot, stage.stageKey, assignment.id, command.access)
            val covered = openItems(snapshot, stage.stageKey)
            val drafts = draftRepository.findForAssignment(assignment.id).associateBy { it.submissionItemId }
            covered.firstOrNull { it !in drafts }?.let { missing ->
                val item = requireNotNull(snapshot.item(missing))
                requireItemReview(command.access, item)
                refuseItem(
                    item, InformationRequestErrorCatalog.REVIEW_WORKSHEET_INCOMPLETE,
                    "Every item this stage reviews needs an outcome before the worksheet is recorded"
                )
            }
            val now = Timestamp.from(clock.instant())
            var sequence = decisionRepository.nextSequenceNumber(review.id)
            covered.forEach { itemId ->
                val item = requireNotNull(snapshot.item(itemId))
                val entry = drafts.getValue(itemId)
                requireItemReview(command.access, item)
                requireOutcomeSupported(snapshot, item, entry.outcome, entry.narrative, assignment)
                decisionRepository.save(
                    decision(
                        review,
                        item,
                        stage,
                        entry.outcome,
                        entry.narrative,
                        command.access.principal,
                        now,
                        sequence++
                    ).apply {
                        kind = InformationRequestReviewDecisionKind.REVIEWER
                        assignmentId = assignment.id
                    },
                )
                entityManager.flush()
            }
            assignment.decidedAt = now
            assignmentRepository.update(assignment)
            review.reviewRevision += 1
            reviewRepository.update(review)
            record(
                locked,
                InformationRequestMutation.RECORD_REVIEW_DECISION,
                InformationRequestAuditActor.of(command.access),
                "information_request.review_decision|${assignment.id}|${command.idempotencyKey}",
                mapOf(
                    "reviewId" to review.id.toString(),
                    "assignmentId" to assignment.id.toString(),
                    "stageKey" to stage.stageKey,
                    "decisionCount" to covered.size.toString(),
                ),
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
    fun override(command: OverrideInformationRequestReviewItemCommand): InformationRequestReviewCommandResult
    {
        gate.requireVisibleTarget(command.access, command.requestId)
        val locked = gate.lock(command.requestId)
        val receipt = receipt(
            ResourceRef(ResourceType.INFORMATION_REQUEST_REVIEW, command.reviewId),
            OVERRIDE_OPERATION,
            command.access,
            command.idempotencyKey,
            listOf(
                command.stageKey.trim(),
                command.submissionItemId,
                command.outcome,
                command.narrative.trim(),
                command.precondition.canonicalForm(),
            ),
        )
        return run(locked, command.access, receipt, { null }) {
            gate.authorizeRequest(command.access, listOf(Action.INFORMATION_REQUEST_MANAGE_REVIEWS), command.requestId)
            gate.requireMutation(locked, InformationRequestMutation.RECORD_REVIEW_DECISION)
            gate.requireContinuation(locked, InformationRequestContinuationAction.REVIEW)
            val review = loader.requireReview(command.requestId, command.reviewId)
            access.requireOpen(review)
            command.precondition.requireSatisfiedBy(loader.reviewETag(review))
            InformationRequestReviewInputLimits.requireText(command.narrative, "An override narrative")
            val snapshot = loader.snapshot(review)
            val stage = requireStageOpen(snapshot, command.stageKey.trim())
            if (!stage.overridePermitted)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_OVERRIDE_NOT_PERMITTED,
                    "This review stage does not permit an authorized override",
                )
            }
            if (command.submissionItemId !in snapshot.coverage[stage.stageKey].orEmpty())
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_ITEM_NOT_COVERED,
                    "This review stage does not review that item",
                )
            }
            val standing =
                snapshot.standing.stage(stage.stageKey)?.items?.firstOrNull { it.itemId == command.submissionItemId }
            if (standing?.standing == InformationRequestReviewItemStanding.DECIDED)
            {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_ALREADY_DECIDED,
                    "This item is already decided in that stage",
                )
            }
            separation.requireMayReview(snapshot, stage, setOf(command.access.principal))
            val narrative = command.narrative.trim().ifBlank {
                throw InformationRequestLifecycleException(
                    InformationRequestErrorCatalog.REVIEW_NARRATIVE_REQUIRED,
                    "An override states its reason",
                )
            }
            val item = requireNotNull(snapshot.item(command.submissionItemId))
            disclosure.requireRecentVerification(
                command.access,
                command.requestId,
                item.informationRequestRequirementId
            )
            requireOutcomeSupported(snapshot, item, command.outcome, narrative, null)
            val now = Timestamp.from(clock.instant())
            decisionRepository.save(
                decision(
                    review,
                    item,
                    stage,
                    command.outcome,
                    narrative,
                    command.access.principal,
                    now,
                    decisionRepository.nextSequenceNumber(review.id),
                ).apply { kind = InformationRequestReviewDecisionKind.OVERRIDE },
            )
            entityManager.flush()
            review.reviewRevision += 1
            reviewRepository.update(review)
            record(
                locked,
                InformationRequestMutation.RECORD_REVIEW_DECISION,
                InformationRequestAuditActor.of(command.access),
                "information_request.review_override|${review.id}|${command.idempotencyKey}",
                mapOf(
                    "reviewId" to review.id.toString(),
                    "stageKey" to stage.stageKey,
                    "submissionItemId" to item.id.toString(),
                    "override" to "true",
                    "reviewOutcome" to command.outcome.name,
                ),
            )
            settlement.settleIfDecided(
                locked,
                review,
                InformationRequestAuditActor.of(command.access),
                command.idempotencyKey
            )
            result(locked, review, null)
        }
    }

    private fun openItems(snapshot: InformationRequestReviewSnapshot, stageKey: String): List<java.util.UUID>
    {
        val settledElsewhere = snapshot.decisions
            .filter { it.stageKey == stageKey && it.kind != InformationRequestReviewDecisionKind.REVIEWER }
            .map { it.submissionItemId }
            .toSet()
        return snapshot.coverage[stageKey].orEmpty().filterNot { it in settledElsewhere }
    }

    private fun requireStageOpen(
        snapshot: InformationRequestReviewSnapshot,
        stageKey: String
    ): InformationRequestReviewStagePlan
    {
        val stage = snapshot.plan.stage(stageKey)
            ?: throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_STAGE_UNKNOWN,
                "This review has no stage $stageKey",
            )
        if (snapshot.standing.stage(stageKey)?.state != InformationRequestReviewStageState.OPEN)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_STAGE_NOT_OPEN,
                "Review stage $stageKey is not open",
            )
        }
        return stage
    }

    private fun requireFirstDecisionInStage(
        snapshot: InformationRequestReviewSnapshot,
        stageKey: String,
        assignmentId: java.util.UUID,
        requestAccess: RequestAccessContext,
    )
    {
        val decidedElsewhere = snapshot.decisions.any {
            it.stageKey == stageKey &&
                    it.kind == InformationRequestReviewDecisionKind.REVIEWER &&
                    it.assignmentId != assignmentId &&
                    it.decidedByPrincipalKind == requestAccess.principal.kind &&
                    it.decidedByPrincipalId == requestAccess.principal.id
        }
        if (decidedElsewhere)
        {
            throw InformationRequestLifecycleException(
                InformationRequestErrorCatalog.REVIEW_ALREADY_DECIDED,
                "This reviewer already decided this stage through another assignment",
            )
        }
    }

    private fun requireItemReview(requestAccess: RequestAccessContext, item: InformationRequestSubmissionItem) =
        access.requireItemReview(requestAccess, item.informationRequestRequirementId)

    private fun refuseItem(item: InformationRequestSubmissionItem, code: String, message: String): Nothing =
        throw InformationRequestItemRefusalException(
            code, message,
            listOf(
                InformationRequestItemProblem(
                    code,
                    message,
                    item.informationRequestRequirementId,
                    item.occurrencePath
                )
            )
        )
    private fun requireOutcomeSupported(
        snapshot: InformationRequestReviewSnapshot,
        item: InformationRequestSubmissionItem,
        outcome: InformationRequestReviewOutcome,
        narrative: String?,
        assignment: InformationRequestReviewAssignment?,
    )
    {
        when (outcome)
        {
            InformationRequestReviewOutcome.CHANGES_REQUIRED,
            InformationRequestReviewOutcome.REJECTED,
                -> if (assignment != null && snapshot.findings.none { it.submissionItemId == item.id && it.assignmentId == assignment.id })
            {
                refuseItem(
                    item,
                    InformationRequestErrorCatalog.REVIEW_FINDING_REQUIRED,
                    "Returning or rejecting an item needs a finding that says why",
                )
            }

            InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION,
            InformationRequestReviewOutcome.WAIVED,
                -> if (narrative.isNullOrBlank())
            {
                refuseItem(
                    item,
                    InformationRequestErrorCatalog.REVIEW_NARRATIVE_REQUIRED,
                    "Accepting an item with an exception or a waiver states why",
                )
            }

            InformationRequestReviewOutcome.SATISFIED -> Unit
        }
        if (outcome == InformationRequestReviewOutcome.WAIVED &&
            dispositionRepository.findForBinding(item.templateBindingId)
                .none { it.disposition == InformationRequestResponseDisposition.WAIVED }
        )
        {
            refuseItem(
                item,
                InformationRequestErrorCatalog.REVIEW_OUTCOME_NOT_PERMITTED,
                "This requirement does not permit a waiver",
            )
        }
    }

    @Suppress("LongParameterList")
    private fun decision(
        review: InformationRequestReview,
        item: InformationRequestSubmissionItem,
        stage: InformationRequestReviewStagePlan,
        outcome: InformationRequestReviewOutcome,
        narrative: String?,
        actor: PrincipalRef,
        now: Timestamp,
        sequence: Int,
    ) = InformationRequestReviewDecision().apply {
        reviewId = review.id
        informationRequestId = review.informationRequestId
        packageId = review.packageId
        submissionItemId = item.id
        requirementId = item.informationRequestRequirementId
        templateReviewStageId = stage.id
        stageKey = stage.stageKey
        this.outcome = outcome
        this.narrative = narrative
        decidedByPrincipalKind = actor.kind
        decidedByPrincipalId = actor.id
        decidedAt = now
        sequenceNumber = sequence
    }

    private fun record(
        locked: LockedInformationRequest,
        mutation: InformationRequestMutation,
        actor: InformationRequestAuditActor,
        idempotencyKey: String,
        details: Map<String, String>,
    )
    {
        val request = locked.request
        transitionHistory.record(
            InformationRequestTransitionHistoryCommand(
                request = request,
                fromState = request.state,
                toState = request.state,
                mutation = mutation,
                actor = actor,
                auditIdempotencyKey = idempotencyKey,
                details = details,
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
        replayedAssignment: (InformationRequestReview) -> InformationRequestReviewAssignment?,
        mutation: () -> InformationRequestReviewCommandResult,
    ): InformationRequestReviewCommandResult =
        when (val decision = commandReceiptService.runOnce(receipt) {
            val result = mutation()
            CommandMutationResult(
                result,
                CommandResultReference(
                    resourceType = ResourceType.INFORMATION_REQUEST_REVIEW,
                    resourceId = result.review.id,
                    revision = result.review.reviewRevision,
                    etag = result.reviewETag,
                ),
            )
        })
        {
            is CommandReceiptDecision.Recorded -> decision.response
            is CommandReceiptDecision.Replayed ->
            {
                require(decision.result.resourceType == ResourceType.INFORMATION_REQUEST_REVIEW) {
                    "Command receipt does not reference a review"
                }
                access.requireReviewReader(requestAccess, locked.request.id)
                val review = loader.requireReview(locked.request.id, decision.result.resourceId)
                result(locked, review, replayedAssignment(review))
            }
        }

    private fun result(
        locked: LockedInformationRequest,
        review: InformationRequestReview,
        assignment: InformationRequestReviewAssignment?,
    ) = InformationRequestReviewCommandResult(
        request = locked.request,
        review = review,
        reviewETag = loader.reviewETag(review),
        responseETag = InformationRequestETag.responsesOf(locked.request),
        assignment = assignment,
        draftETag = assignment?.let(loader::draftETag),
    )

    private companion object
    {
        const val RECORD_OPERATION = "record-information-request-review-decisions"
        const val OVERRIDE_OPERATION = "override-information-request-review-item"
    }
}
