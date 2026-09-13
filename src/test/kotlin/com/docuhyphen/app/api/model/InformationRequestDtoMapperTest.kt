package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.service.informationrequest.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class InformationRequestDtoMapperTest
{
    @Test
    fun `maps every runtime request field and derives the aggregate etag`()
    {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.DRAFT
            gatesExchangeClosure = false
            aggregateRevision = 3
        }

        val dto = InformationRequestDtoMapper.toDto(request)

        assertEquals(request.id, dto.id)
        assertEquals(request.exchangeId, dto.exchangeId)
        assertEquals(request.templateVersionId, dto.templateVersionId)
        assertEquals(InformationRequestOwnerType.ORGANIZATION, dto.ownerType)
        assertEquals(request.ownerOrganizationId, dto.ownerOrganizationId)
        assertEquals(InformationRequestState.DRAFT, dto.state)
        assertEquals(false, dto.gatesExchangeClosure)
        assertEquals(3, dto.aggregateRevision)
        assertEquals(InformationRequestETag.aggregateOf(request), dto.requestETag)
    }

    @Test
    fun `maps client-safe condition evaluation state`()
    {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.IN_PROGRESS
            aggregateRevision = 4
        }

        val dto = InformationRequestDtoMapper.toDto(
            request,
            listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-review-needed",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.FALSE,
                    sourceRequirementKeys = setOf("prior-response"),
                    fieldDefinitionIds = setOf(UUID.randomUUID()),
                ),
            ),
        )

        assertEquals(1, dto.conditionEvaluations.size)
        assertEquals("when-review-needed", dto.conditionEvaluations.single().ruleKey)
        assertEquals(1, dto.conditionEvaluations.single().expressionVersion)
        assertEquals(InformationRequestConditionEvaluationState.FALSE, dto.conditionEvaluations.single().state)
    }
}
