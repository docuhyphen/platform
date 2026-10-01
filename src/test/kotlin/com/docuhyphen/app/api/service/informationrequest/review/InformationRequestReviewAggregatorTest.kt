package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.model.entity.InformationRequestReviewAggregation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome.CHANGES_REQUIRED
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome.REJECTED
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome.SATISFIED
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome.WAIVED
import com.docuhyphen.app.api.model.entity.InformationRequestReviewStageOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewTieResolution
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewAssignmentFact
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewDecisionFact
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewItemStanding
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewResult
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStageInput
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStagePlan
import com.docuhyphen.app.api.model.informationrequest.review.InformationRequestReviewStageState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class InformationRequestReviewAggregatorTest
{
    private val item = UUID.randomUUID()
    private val otherItem = UUID.randomUUID()
    private val firstReviewer = UUID.randomUUID()
    private val secondReviewer = UUID.randomUUID()
    private val thirdReviewer = UUID.randomUUID()
    private var sequence = 0

    @Test
    fun `any takes the earliest decision and waits until one exists`()
    {
        val plan = stage("check", InformationRequestReviewAggregation.ANY)
        val assignments = active("check", firstReviewer, secondReviewer)
        assertEquals(InformationRequestReviewItemStanding.PENDING, only(plan, assignments, emptyList()).standing)

        val decisions = listOf(decided("check", firstReviewer, CHANGES_REQUIRED), decided("check", secondReviewer, SATISFIED))
        val result = evaluate(listOf(plan), assignments, decisions)
        assertEquals(CHANGES_REQUIRED, result.itemOutcomes[item])
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, result.settledState)
    }

    @Test
    fun `all waits for every eligible reviewer and takes the most severe outcome`()
    {
        val plan = stage("check", InformationRequestReviewAggregation.ALL, minimum = 2)
        val assignments = active("check", firstReviewer, secondReviewer)
        val oneDecided = listOf(decided("check", firstReviewer, SATISFIED))
        assertEquals(InformationRequestReviewItemStanding.PENDING, only(plan, assignments, oneDecided).standing)

        val bothDecided = oneDecided + decided("check", secondReviewer, SATISFIED_WITH_EXCEPTION)
        val result = evaluate(listOf(plan), assignments, bothDecided)
        assertEquals(SATISFIED_WITH_EXCEPTION, result.itemOutcomes[item])
        assertEquals(InformationRequestReviewState.SATISFIED_WITH_EXCEPTION, result.settledState)
    }

    @Test
    fun `a recused reviewer leaves the count and too few eligible reviewers keeps the item pending`()
    {
        val plan = stage("check", InformationRequestReviewAggregation.ALL, minimum = 2)
        val recused = listOf(
            assignment("check", firstReviewer),
            assignment("check", secondReviewer),
            assignment("check", thirdReviewer, InformationRequestReviewAssignmentState.RECUSED),
        )
        val decisions = listOf(decided("check", firstReviewer, SATISFIED), decided("check", secondReviewer, SATISFIED))
        assertEquals(SATISFIED, evaluate(listOf(plan), recused, decisions).itemOutcomes[item])

        val understaffed = listOf(
            assignment("check", firstReviewer),
            assignment("check", secondReviewer, InformationRequestReviewAssignmentState.RECUSED),
        )
        val pending = only(plan, understaffed, listOf(decided("check", firstReviewer, SATISFIED)))
        assertEquals(InformationRequestReviewItemStanding.UNDERSTAFFED, pending.standing)
        assertNull(evaluate(listOf(plan), understaffed, listOf(decided("check", firstReviewer, SATISFIED))).settledState)
    }

    @Test
    fun `a delegated assignment is replaced by the delegate it names`()
    {
        val plan = stage("check", InformationRequestReviewAggregation.ALL, minimum = 1)
        val assignments = listOf(
            assignment("check", firstReviewer, InformationRequestReviewAssignmentState.DELEGATED),
            assignment("check", secondReviewer),
        )
        assertEquals(InformationRequestReviewItemStanding.PENDING, only(plan, assignments, emptyList()).standing)
        val result = evaluate(listOf(plan), assignments, listOf(decided("check", secondReviewer, SATISFIED)))
        assertEquals(InformationRequestReviewState.SATISFIED, result.settledState)
    }

    @Test
    fun `quorum takes the outcome that reaches it and ties when every reviewer decided without one`()
    {
        val plan = stage("check", InformationRequestReviewAggregation.QUORUM, quorum = 2, minimum = 3)
        val assignments = active("check", firstReviewer, secondReviewer, thirdReviewer)
        val reached = listOf(decided("check", firstReviewer, SATISFIED), decided("check", secondReviewer, SATISFIED))
        assertEquals(SATISFIED, evaluate(listOf(plan), assignments, reached).itemOutcomes[item])

        val split = listOf(
            decided("check", firstReviewer, SATISFIED),
            decided("check", secondReviewer, CHANGES_REQUIRED),
        )
        assertEquals(InformationRequestReviewItemStanding.PENDING, only(plan, assignments, split).standing)

        val tied = split + decided("check", thirdReviewer, REJECTED)
        assertEquals(REJECTED, evaluate(listOf(plan), assignments, tied).itemOutcomes[item])
    }

    @Test
    fun `a tie that needs an override stays tied until an authorized override decides it`()
    {
        val plan = stage(
            "check",
            InformationRequestReviewAggregation.CONSENSUS,
            minimum = 2,
            tie = InformationRequestReviewTieResolution.REQUIRE_OVERRIDE,
            override = true,
        )
        val assignments = active("check", firstReviewer, secondReviewer)
        val disagreeing = listOf(decided("check", firstReviewer, SATISFIED), decided("check", secondReviewer, CHANGES_REQUIRED))
        assertEquals(InformationRequestReviewItemStanding.TIED, only(plan, assignments, disagreeing).standing)
        assertNull(evaluate(listOf(plan), assignments, disagreeing).settledState)

        val overridden = disagreeing + overridden("check", SATISFIED_WITH_EXCEPTION)
        val result = evaluate(listOf(plan), assignments, overridden)
        assertEquals(SATISFIED_WITH_EXCEPTION, result.itemOutcomes[item])
        assertEquals(InformationRequestReviewState.SATISFIED_WITH_EXCEPTION, result.settledState)
    }

    @Test
    fun `consensus settles when every eligible reviewer agrees and resolves disagreement by the stated rule`()
    {
        val plan = stage("check", InformationRequestReviewAggregation.CONSENSUS, minimum = 2)
        val assignments = active("check", firstReviewer, secondReviewer)
        val agreeing = listOf(decided("check", firstReviewer, SATISFIED), decided("check", secondReviewer, SATISFIED))
        assertEquals(SATISFIED, evaluate(listOf(plan), assignments, agreeing).itemOutcomes[item])

        val disagreeing = listOf(decided("check", firstReviewer, WAIVED), decided("check", secondReviewer, REJECTED))
        assertEquals(REJECTED, evaluate(listOf(plan), assignments, disagreeing).itemOutcomes[item])
    }

    @Test
    fun `a carried outcome decides an unchanged item without a new decision`()
    {
        val plan = stage("check", InformationRequestReviewAggregation.ALL, minimum = 1)
        val carried = listOf(carried("check", item, SATISFIED), decided("check", firstReviewer, SATISFIED, otherItem))
        val result = evaluate(listOf(plan), active("check", firstReviewer), carried, listOf(item, otherItem))
        assertEquals(mapOf(item to SATISFIED, otherItem to SATISFIED), result.itemOutcomes)
        assertEquals(InformationRequestReviewState.SATISFIED, result.settledState)
    }

    @Test
    fun `sequential stages open in order and a returned item settles the review before later stages run`()
    {
        val first = stage("first-check", InformationRequestReviewAggregation.ANY, position = 1)
        val final = stage("final-check", InformationRequestReviewAggregation.ANY, position = 2)
        val assignments = active("first-check", firstReviewer) + active("final-check", secondReviewer)

        val waiting = evaluate(listOf(first, final), assignments, emptyList())
        assertEquals(InformationRequestReviewStageState.OPEN, waiting.stage("first-check")?.state)
        assertEquals(InformationRequestReviewStageState.WAITING, waiting.stage("final-check")?.state)

        val returned = evaluate(listOf(first, final), assignments, listOf(decided("first-check", firstReviewer, CHANGES_REQUIRED)))
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.settledState)
        assertEquals(InformationRequestReviewStageState.WAITING, returned.stage("final-check")?.state)

        val passed = listOf(decided("first-check", firstReviewer, SATISFIED))
        val advancing = evaluate(listOf(first, final), assignments, passed)
        assertEquals(InformationRequestReviewStageState.OPEN, advancing.stage("final-check")?.state)
        assertNull(advancing.settledState)

        val finished = evaluate(
            listOf(first, final),
            assignments,
            passed + decided("final-check", secondReviewer, WAIVED),
        )
        assertEquals(WAIVED, finished.itemOutcomes[item])
        assertEquals(InformationRequestReviewState.SATISFIED_WITH_EXCEPTION, finished.settledState)
    }

    @Test
    fun `parallel stages run together and settle only when every stage has settled`()
    {
        val records = stage("record-check", InformationRequestReviewAggregation.ANY, position = 1)
        val others = stage("other-check", InformationRequestReviewAggregation.ANY, position = 2)
        val inputs = listOf(
            InformationRequestReviewStageInput(records, listOf(item)),
            InformationRequestReviewStageInput(others, listOf(otherItem)),
        )
        val assignments = active("record-check", firstReviewer) + active("other-check", secondReviewer)
        val partial = InformationRequestReviewAggregator.evaluate(
            InformationRequestReviewStageOrdering.PARALLEL,
            inputs,
            listOf(item, otherItem),
            assignments,
            listOf(decided("record-check", firstReviewer, REJECTED)),
        )
        assertEquals(InformationRequestReviewStageState.SETTLED, partial.stage("record-check")?.state)
        assertEquals(InformationRequestReviewStageState.OPEN, partial.stage("other-check")?.state)
        assertNull(partial.settledState)

        val settled = InformationRequestReviewAggregator.evaluate(
            InformationRequestReviewStageOrdering.PARALLEL,
            inputs,
            listOf(item, otherItem),
            assignments,
            listOf(
                decided("record-check", firstReviewer, REJECTED),
                decided("other-check", secondReviewer, CHANGES_REQUIRED, otherItem),
            ),
        )
        assertEquals(InformationRequestReviewState.REJECTED, settled.settledState)
        assertEquals(mapOf(item to REJECTED, otherItem to CHANGES_REQUIRED), settled.itemOutcomes)
    }

    @Test
    fun `a review with nothing to review, or a stage covering nothing, is satisfied at once`()
    {
        val empty = InformationRequestReviewAggregator.evaluate(
            InformationRequestReviewStageOrdering.SEQUENTIAL,
            listOf(InformationRequestReviewStageInput(stage("check", InformationRequestReviewAggregation.ANY), emptyList())),
            emptyList(),
            emptyList(),
            emptyList(),
        )
        assertEquals(InformationRequestReviewState.SATISFIED, empty.settledState)
        assertEquals(InformationRequestReviewStageState.SETTLED, empty.stages.single().state)
    }

    private fun evaluate(
        plans: List<InformationRequestReviewStagePlan>,
        assignments: List<InformationRequestReviewAssignmentFact>,
        decisions: List<InformationRequestReviewDecisionFact>,
        items: List<UUID> = listOf(item),
    ): InformationRequestReviewResult =
        InformationRequestReviewAggregator.evaluate(
            InformationRequestReviewStageOrdering.SEQUENTIAL,
            plans.map { InformationRequestReviewStageInput(it, items) },
            items,
            assignments,
            decisions,
        )

    private fun only(
        plan: InformationRequestReviewStagePlan,
        assignments: List<InformationRequestReviewAssignmentFact>,
        decisions: List<InformationRequestReviewDecisionFact>,
    ) = evaluate(listOf(plan), assignments, decisions).stage(plan.stageKey)!!.items.single()

    @Suppress("LongParameterList")
    private fun stage(
        key: String,
        aggregation: InformationRequestReviewAggregation,
        quorum: Int? = null,
        minimum: Int = 1,
        tie: InformationRequestReviewTieResolution = InformationRequestReviewTieResolution.MOST_SEVERE_OUTCOME,
        override: Boolean = false,
        position: Int = 1,
    ) = InformationRequestReviewStagePlan(
        id = UUID.randomUUID(),
        stageKey = key,
        position = position,
        title = "Review stage",
        aggregation = aggregation,
        quorumCount = quorum,
        minimumReviewerCount = minimum,
        tieResolution = tie,
        overridePermitted = override,
        excludesResponseParties = false,
        excludesPriorReviewers = false,
        coveredSectionIds = emptySet(),
    )

    private fun active(stageKey: String, vararg reviewers: UUID) = reviewers.map { assignment(stageKey, it) }

    private fun assignment(
        stageKey: String,
        id: UUID,
        state: InformationRequestReviewAssignmentState = InformationRequestReviewAssignmentState.ACTIVE,
    ) = InformationRequestReviewAssignmentFact(id, stageKey, state)

    private fun decided(stageKey: String, assignmentId: UUID, outcome: InformationRequestReviewOutcome, itemId: UUID = item) =
        InformationRequestReviewDecisionFact(itemId, stageKey, assignmentId, InformationRequestReviewDecisionKind.REVIEWER, outcome, ++sequence)

    private fun overridden(stageKey: String, outcome: InformationRequestReviewOutcome) =
        InformationRequestReviewDecisionFact(item, stageKey, null, InformationRequestReviewDecisionKind.OVERRIDE, outcome, ++sequence)

    private fun carried(stageKey: String, itemId: UUID, outcome: InformationRequestReviewOutcome) =
        InformationRequestReviewDecisionFact(itemId, stageKey, null, InformationRequestReviewDecisionKind.CARRIED, outcome, ++sequence)
}
