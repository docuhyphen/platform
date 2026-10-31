package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStanding
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestExecutionStandingKind
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestState
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

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

        val dto = InformationRequestDtoMapper.toDto(
            request,
            revealOwner = true,
            executionStanding = InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE)
        )

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
    fun `a header for a caller who does not manage the request keeps its Template Version and omits owner identifiers`() {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            ownerUserId = UUID.randomUUID()
            state = InformationRequestState.ISSUED
            aggregateRevision = 2
        }

        val dto = InformationRequestDtoMapper.toDto(
            request,
            revealOwner = false,
            executionStanding = InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE)
        )

        assertEquals(request.templateVersionId, dto.templateVersionId)
        assertEquals(null, dto.ownerOrganizationId)
        assertEquals(null, dto.ownerUserId)
        assertEquals(request.exchangeId, dto.exchangeId)
        assertEquals(InformationRequestOwnerType.ORGANIZATION, dto.ownerType)
        assertEquals(InformationRequestState.ISSUED, dto.state)
        assertEquals(InformationRequestETag.aggregateOf(request), dto.requestETag)
    }

    @Test
    fun `a superseded request names its replacement only when the replacement may be revealed`() {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.SUPERSEDED
            supersededByRequestId = UUID.randomUUID()
            aggregateRevision = 5
        }
        val standing = InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE)

        val revealed = InformationRequestDtoMapper.toDto(
            request,
            revealOwner = true,
            executionStanding = standing,
            revealReplacement = true
        )
        val withheld = InformationRequestDtoMapper.toDto(request, revealOwner = true, executionStanding = standing)

        assertEquals(request.supersededByRequestId, revealed.supersededByRequestId)
        assertEquals(null, withheld.supersededByRequestId)
        assertEquals(InformationRequestState.SUPERSEDED, withheld.state)
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
            revealOwner = true,
            executionStanding = InformationRequestExecutionStanding(InformationRequestExecutionStandingKind.ACTIVE),
            conditionEvaluations = listOf(
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
