package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestCarryForwardDto
import com.docuhyphen.app.api.model.dto.InformationRequestLineageDto
import com.docuhyphen.app.api.model.dto.InformationRequestLineageViewDto
import com.docuhyphen.app.api.model.dto.InformationRequestRecurrenceDto
import com.docuhyphen.app.api.model.dto.InformationRequestRefreshRuleDto
import com.docuhyphen.app.api.model.dto.InformationRequestSuccessorResultDto
import com.docuhyphen.app.api.model.entity.InformationRequestCarryForwardDecision
import com.docuhyphen.app.api.model.entity.InformationRequestLineage
import com.docuhyphen.app.api.model.entity.InformationRequestRecurrence
import com.docuhyphen.app.api.model.entity.InformationRequestRefreshRule
import com.docuhyphen.app.api.model.informationrequest.InformationRequestCarryForwardOffer
import com.docuhyphen.app.api.model.informationrequest.InformationRequestLineageView
import com.docuhyphen.app.api.model.informationrequest.InformationRequestSuccessorResult

object InformationRequestLineageDtoMapper
{
    fun toDto(lineage: InformationRequestLineage) = InformationRequestLineageDto(
        id = lineage.id,
        lineageKind = lineage.lineageKind,
        sourceRequestId = lineage.sourceRequestId,
        successorRequestId = lineage.successorRequestId,
        sourcePackageId = lineage.sourcePackageId,
        recurrenceId = lineage.recurrenceId,
        recurrenceSequence = lineage.recurrenceSequence,
        refreshRuleId = lineage.refreshRuleId,
        reasonCode = lineage.reasonCode,
        createdAt = lineage.createdAt,
    )

    fun toDto(view: InformationRequestLineageView) = InformationRequestLineageViewDto(
        informationRequestId = view.request.id,
        source = view.source?.let(::toDto),
        successors = view.successors.map(::toDto),
    )

    fun toDto(offer: InformationRequestCarryForwardOffer) = InformationRequestCarryForwardDto(
        requirementId = offer.carryForward.informationRequestRequirementId,
        decision = offer.carryForward.decision,
        reasonCode = offer.carryForward.reasonCode,
        sourcePackageId = offer.carryForward.sourcePackageId,
        priorDisposition = offer.sourceItem?.disposition,
        priorNarrative = offer.sourceItem?.narrative,
        priorFieldValue = offer.sourceFieldValue?.takeUnless { it.cleared }?.value,
    )

    fun toDto(result: InformationRequestSuccessorResult) = InformationRequestSuccessorResultDto(
        sourceRequestId = result.source.id,
        sourceState = result.source.state,
        successor = InformationRequestDtoMapper.toDto(result.successor),
        successorETag = result.successorETag,
        lineage = toDto(result.lineage),
        offeredCount = result.carryForwards.count { it.decision == InformationRequestCarryForwardDecision.OFFERED },
        invalidatedCount = result.carryForwards.count { it.decision == InformationRequestCarryForwardDecision.INVALIDATED },
    )

    fun toDto(recurrence: InformationRequestRecurrence) = InformationRequestRecurrenceDto(
        id = recurrence.id,
        originRequestId = recurrence.originRequestId,
        intervalUnit = recurrence.intervalUnit,
        intervalCount = recurrence.intervalCount,
        firstDueAt = recurrence.firstDueAt,
        maximumOccurrences = recurrence.maximumOccurrences,
        createdAt = recurrence.createdAt,
    )

    fun toDto(rule: InformationRequestRefreshRule) = InformationRequestRefreshRuleDto(
        id = rule.id,
        informationRequestId = rule.informationRequestId,
        requirementKey = rule.requirementKey,
        leadDays = rule.leadDays,
        createdAt = rule.createdAt,
    )
}
