package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.InformationRequestDto
import com.docuhyphen.app.api.model.dto.InformationRequestConditionEvaluationDto
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag

object InformationRequestDtoMapper
{
    fun toDto(
        request: InformationRequest,
        conditionEvaluations: List<InformationRequestConditionEvaluationProjection> = emptyList(),
    ): InformationRequestDto =
        InformationRequestDto(
            id = request.id,
            exchangeId = request.exchangeId,
            templateVersionId = request.templateVersionId,
            ownerType = request.ownerType,
            ownerOrganizationId = request.ownerOrganizationId,
            ownerUserId = request.ownerUserId,
            state = request.state,
            gatesExchangeClosure = request.gatesExchangeClosure,
            aggregateRevision = request.aggregateRevision,
            issuedAt = request.issuedAt,
            closedAt = request.closedAt,
            cancelledAt = request.cancelledAt,
            supersededAt = request.supersededAt,
            supersededByRequestId = request.supersededByRequestId,
            createdAt = request.createdAt,
            updatedAt = request.updatedAt,
            requestETag = InformationRequestETag.aggregateOf(request),
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
