package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestConditionEvaluationDto
import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag

object InformationRequestDtoMapper
{
    fun toDto(
        request: InformationRequest,
        revealOwner: Boolean,
        executionStanding: InformationRequestExecutionStanding,
        conditionEvaluations: List<InformationRequestConditionEvaluationProjection> = emptyList(),
        revealReplacement: Boolean = false,
    ): InformationRequestDto =
        InformationRequestDto(
            id = request.id,
            exchangeId = request.exchangeId,
            templateVersionId = request.templateVersionId,
            ownerType = request.ownerType,
            ownerOrganizationId = request.ownerOrganizationId.takeIf { revealOwner },
            ownerUserId = request.ownerUserId.takeIf { revealOwner },
            state = request.state,
            gatesExchangeClosure = request.gatesExchangeClosure,
            aggregateRevision = request.aggregateRevision,
            issuedAt = request.issuedAt,
            closedAt = request.closedAt,
            cancelledAt = request.cancelledAt,
            supersededAt = request.supersededAt,
            supersededByRequestId = request.supersededByRequestId.takeIf { revealReplacement },
            createdAt = request.createdAt,
            updatedAt = request.updatedAt,
            requestETag = InformationRequestETag.aggregateOf(request),
            executionStanding = InformationRequestExecutionStandingDtoMapper.toDto(executionStanding),
            conditionEvaluations = conditionEvaluations.map(::toConditionEvaluationDto),
        )

    private fun toConditionEvaluationDto(
        projection: InformationRequestConditionEvaluationProjection,
    ): InformationRequestConditionEvaluationDto =
        InformationRequestConditionEvaluationDto(
            ruleKey = projection.ruleKey,
            expressionVersion = projection.expressionVersion,
            state = projection.state,
            occurrencePath = projection.occurrencePath,
        )
}
