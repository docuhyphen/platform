package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.review.*
import java.util.*

object InformationRequestReviewAggregator
{
    fun evaluate(
        ordering: InformationRequestReviewStageOrdering,
        stages: List<InformationRequestReviewStageInput>,
        reviewedItemIds: List<UUID>,
        assignments: List<InformationRequestReviewAssignmentFact>,
        decisions: List<InformationRequestReviewDecisionFact>,
    ): InformationRequestReviewResult
    {
        val ordered = stages.sortedBy { it.plan.position }
        val results = mutableListOf<InformationRequestReviewStageResult>()
        var blocked = false
        var returned = false
        for (stage in ordered)
        {
            if (ordering == InformationRequestReviewStageOrdering.SEQUENTIAL && (blocked || returned))
            {
                results += waiting(stage)
                continue
            }
            val result = stage(stage, assignments, decisions)
            results += result
            if (result.state != InformationRequestReviewStageState.SETTLED) blocked = true
            else if (result.items.any { it.outcome?.passing == false }) returned = true
        }

        val itemOutcomes = finalOutcomes(reviewedItemIds, results)
        val settled = when (ordering)
        {
            InformationRequestReviewStageOrdering.SEQUENTIAL -> returned || !blocked
            InformationRequestReviewStageOrdering.PARALLEL -> results.all { it.state == InformationRequestReviewStageState.SETTLED }
        }
        return InformationRequestReviewResult(
            stages = results,
            settledState = if (settled) packageState(itemOutcomes.values) else null,
            itemOutcomes = if (settled) itemOutcomes else itemOutcomes.filterKeys { false },
        )
    }

    fun packageState(outcomes: Collection<InformationRequestReviewOutcome>): InformationRequestReviewState =
        when (outcomes.maxByOrNull { it.severity })
        {
            null, InformationRequestReviewOutcome.SATISFIED -> InformationRequestReviewState.SATISFIED
            InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION,
            InformationRequestReviewOutcome.WAIVED,
                -> InformationRequestReviewState.SATISFIED_WITH_EXCEPTION

            InformationRequestReviewOutcome.CHANGES_REQUIRED -> InformationRequestReviewState.CHANGES_REQUESTED
            InformationRequestReviewOutcome.REJECTED -> InformationRequestReviewState.REJECTED
        }

    fun mostSevere(outcomes: Collection<InformationRequestReviewOutcome>): InformationRequestReviewOutcome? =
        outcomes.maxWithOrNull(compareBy<InformationRequestReviewOutcome> { it.severity }.thenBy { it.ordinal })

    private fun stage(
        input: InformationRequestReviewStageInput,
        assignments: List<InformationRequestReviewAssignmentFact>,
        decisions: List<InformationRequestReviewDecisionFact>,
    ): InformationRequestReviewStageResult
    {
        val stageKey = input.plan.stageKey
        val stageAssignments = assignments.filter { it.stageKey == stageKey }
        val stageDecisions = decisions.filter { it.stageKey == stageKey }.sortedBy { it.sequenceNumber }
        val items = input.coveredItemIds.map { itemId ->
            item(input, itemId, stageAssignments, stageDecisions.filter { it.itemId == itemId })
        }
        val state = if (items.all { it.standing == InformationRequestReviewItemStanding.DECIDED })
            InformationRequestReviewStageState.SETTLED
        else
            InformationRequestReviewStageState.OPEN
        return InformationRequestReviewStageResult(stageKey, state, items)
    }

    private fun item(
        input: InformationRequestReviewStageInput,
        itemId: UUID,
        assignments: List<InformationRequestReviewAssignmentFact>,
        decisions: List<InformationRequestReviewDecisionFact>,
    ): InformationRequestReviewItemResult
    {
        decisions.lastOrNull { it.kind == InformationRequestReviewDecisionKind.OVERRIDE }
            ?.let { return decided(itemId, it.outcome) }
        decisions.lastOrNull { it.kind == InformationRequestReviewDecisionKind.CARRIED }
            ?.let { return decided(itemId, it.outcome) }

        val reviewerDecisions = decisions
            .filter { it.kind == InformationRequestReviewDecisionKind.REVIEWER && it.assignmentId != null }
            .distinctBy { it.assignmentId }
        val decidedAssignments = reviewerDecisions.mapNotNull { it.assignmentId }.toSet()
        val eligible = assignments.filter {
            it.state == InformationRequestReviewAssignmentState.ACTIVE || it.id in decidedAssignments
        }
        val counted = reviewerDecisions.filter { decision -> eligible.any { it.id == decision.assignmentId } }
        val plan = input.plan
        if (eligible.size < plan.minimumReviewerCount)
        {
            return InformationRequestReviewItemResult(itemId, InformationRequestReviewItemStanding.UNDERSTAFFED, null)
        }
        val everyoneDecided = counted.size == eligible.size
        val outcomes = counted.map { it.outcome }

        return when (plan.aggregation)
        {
            InformationRequestReviewAggregation.ANY ->
                counted.firstOrNull()?.let { decided(itemId, it.outcome) } ?: pending(itemId)

            InformationRequestReviewAggregation.ALL ->
                if (everyoneDecided && outcomes.isNotEmpty()) decided(itemId, mostSevere(outcomes)!!)
                else pending(
                    itemId
                )

            InformationRequestReviewAggregation.QUORUM ->
            {
                val quorum = plan.quorumCount ?: plan.minimumReviewerCount
                val reaching = outcomes.groupingBy { it }.eachCount().filterValues { it >= quorum }.keys
                when
                {
                    reaching.isNotEmpty() -> decided(itemId, mostSevere(reaching)!!)
                    everyoneDecided && outcomes.isNotEmpty() -> tie(input, itemId, outcomes)
                    else -> pending(itemId)
                }
            }

            InformationRequestReviewAggregation.CONSENSUS -> when
            {
                !everyoneDecided || outcomes.isEmpty() -> pending(itemId)
                outcomes.distinct().size == 1 -> decided(itemId, outcomes.first())
                else -> tie(input, itemId, outcomes)
            }
        }
    }

    private fun tie(
        input: InformationRequestReviewStageInput,
        itemId: UUID,
        outcomes: List<InformationRequestReviewOutcome>,
    ): InformationRequestReviewItemResult =
        when (input.plan.tieResolution)
        {
            InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME -> decided(itemId, mostSevere(outcomes)!!)
            InformationRequestReviewTieResolution.REQUIRE_OVERRIDE ->
                InformationRequestReviewItemResult(itemId, InformationRequestReviewItemStanding.TIED, null)
        }

    private fun finalOutcomes(
        reviewedItemIds: List<UUID>,
        results: List<InformationRequestReviewStageResult>,
    ): Map<UUID, InformationRequestReviewOutcome>
    {
        val decidedByItem = results
            .filter { it.state == InformationRequestReviewStageState.SETTLED }
            .flatMap { it.items }
            .groupBy({ it.itemId }, { it.outcome!! })
        return reviewedItemIds.associateWith { itemId ->
            mostSevere(decidedByItem[itemId].orEmpty()) ?: InformationRequestReviewOutcome.SATISFIED
        }
    }

    private fun waiting(input: InformationRequestReviewStageInput) = InformationRequestReviewStageResult(
        stageKey = input.plan.stageKey,
        state = InformationRequestReviewStageState.WAITING,
        items = input.coveredItemIds.map {
            InformationRequestReviewItemResult(
                it,
                InformationRequestReviewItemStanding.PENDING,
                null
            )
        },
    )

    private fun decided(itemId: UUID, outcome: InformationRequestReviewOutcome) =
        InformationRequestReviewItemResult(itemId, InformationRequestReviewItemStanding.DECIDED, outcome)

    private fun pending(itemId: UUID) =
        InformationRequestReviewItemResult(itemId, InformationRequestReviewItemStanding.PENDING, null)
}
