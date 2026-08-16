package com.docuhyphen.app.api.service.workflow

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.Person
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.repository.exchange.ExchangeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class WorkflowSubjectDataEnrichmentTest
{
    private val exchangeRepository: ExchangeRepository = mock()

    @Test
    fun `enriches Exchange workflow subject data with display fields`()
    {
        val exchangeId = UUID.randomUUID()
        val orgId = UUID.randomUUID()
        val initiatorId = UUID.randomUUID()
        val exchange = Exchange().apply {
            id = exchangeId
            name = "Supplier onboarding"
            ownerOrganizationId = orgId
            initiator = AppUser().apply {
                id = initiatorId
                person = Person().apply {
                    firstName = "Amina"
                    lastName = "Patel"
                }
            }
        }
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)

        val request = TriggerRequest(
            triggerEvent = "exchange.draft_submitted",
            subjectResourceType = ResourceType.EXCHANGE.name,
            subjectResourceId = exchangeId,
            subjectData = mapOf("initiatorId" to initiatorId.toString()),
        )

        val enriched = enrich(request)

        assertEquals("Supplier onboarding", enriched.subjectData["exchangeName"])
        assertEquals(initiatorId.toString(), enriched.subjectData["initiatorId"])
        assertEquals("Amina Patel", enriched.subjectData["initiatorName"])
        assertEquals(orgId.toString(), enriched.subjectData["orgId"])
    }

    @Test
    fun `keeps explicit subject display fields when already provided`()
    {
        val exchangeId = UUID.randomUUID()
        val exchange = Exchange().apply {
            id = exchangeId
            name = "Repository value"
        }
        whenever(exchangeRepository.findById(exchangeId)).thenReturn(exchange)

        val request = TriggerRequest(
            triggerEvent = "exchange.acceptance_pending",
            subjectResourceType = ResourceType.EXCHANGE.name,
            subjectResourceId = exchangeId,
            subjectData = mapOf("exchangeName" to "Explicit value"),
        )

        val enriched = enrich(request)

        assertEquals("Explicit value", enriched.subjectData["exchangeName"])
    }

    private fun enrich(request: TriggerRequest): TriggerRequest
    {
        val engine = DefaultWorkflowEngineService()
        inject(engine, "exchangeRepository", exchangeRepository)
        return DefaultWorkflowEngineService::class.java.getDeclaredMethod(
            "enrichSubjectData",
            TriggerRequest::class.java,
        ).apply {
            isAccessible = true
        }.invoke(engine, request) as TriggerRequest
    }

    private fun inject(engine: DefaultWorkflowEngineService, fieldName: String, value: Any)
    {
        DefaultWorkflowEngineService::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
            set(engine, value)
        }
    }
}
