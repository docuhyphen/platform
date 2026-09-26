package com.docuhyphen.app.api.model.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestReviewAggregation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewStageOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewTieResolution
import java.util.UUID

data class InformationRequestReviewStagePlan(
    val id: UUID,
    val stageKey: String,
    val position: Int,
    val title: String,
    val aggregation: InformationRequestReviewAggregation,
    val quorumCount: Int?,
    val minimumReviewerCount: Int,
    val tieResolution: InformationRequestReviewTieResolution,
    val overridePermitted: Boolean,
    val excludesResponseParties: Boolean,
    val excludesPriorReviewers: Boolean,
    val coveredSectionIds: Set<UUID>,
)
{
    fun coversSection(sectionId: UUID): Boolean = coveredSectionIds.isEmpty() || sectionId in coveredSectionIds
}

data class InformationRequestReviewPlan(
    val templateVersionId: UUID,
    val ordering: InformationRequestReviewStageOrdering,
    val stages: List<InformationRequestReviewStagePlan>,
)
{
    fun stage(stageKey: String): InformationRequestReviewStagePlan? = stages.firstOrNull { it.stageKey == stageKey }
}

data class InformationRequestReviewAssignmentFact(
    val id: UUID,
    val stageKey: String,
    val state: InformationRequestReviewAssignmentState,
)

data class InformationRequestReviewDecisionFact(
    val itemId: UUID,
    val stageKey: String,
    val assignmentId: UUID?,
    val kind: InformationRequestReviewDecisionKind,
    val outcome: InformationRequestReviewOutcome,
    val sequenceNumber: Int,
)

data class InformationRequestReviewStageInput(
    val plan: InformationRequestReviewStagePlan,
    val coveredItemIds: List<UUID>,
)

enum class InformationRequestReviewItemStanding
{
    PENDING,
    UNDERSTAFFED,
    TIED,
    DECIDED,
}

data class InformationRequestReviewItemResult(
    val itemId: UUID,
    val standing: InformationRequestReviewItemStanding,
    val outcome: InformationRequestReviewOutcome?,
)

enum class InformationRequestReviewStageState
{
    WAITING,
    OPEN,
    SETTLED,
}

data class InformationRequestReviewStageResult(
    val stageKey: String,
    val state: InformationRequestReviewStageState,
    val items: List<InformationRequestReviewItemResult>,
)

data class InformationRequestReviewResult(
    val stages: List<InformationRequestReviewStageResult>,
    val settledState: InformationRequestReviewState?,
    val itemOutcomes: Map<UUID, InformationRequestReviewOutcome>,
)
{
    fun stage(stageKey: String): InformationRequestReviewStageResult? = stages.firstOrNull { it.stageKey == stageKey }
}
