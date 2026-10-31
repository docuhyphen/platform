package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.exchange.ExchangeBlueprintInformationRequestStart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class ExchangeInitiationBlueprintStartTest {
    private val blueprintId: UUID = UUID.randomUUID()
    private val templateVersionId: UUID = UUID.randomUUID()

    @Test
    fun `an Exchange started without a Blueprint still needs a document`() {
        val fixture = ExchangeInitiationAutoAcceptFixture()

        val refusal = assertThrows<IllegalArgumentException> { fixture.initiate(documents = emptyList()) }

        assertEquals("Add at least one document", refusal.message)
        verify(fixture.blueprintInformationRequestStarter, never()).start(any(), any(), any(), any())
    }

    @Test
    fun `a Blueprint that starts no Information Request still needs a document`() {
        val fixture = ExchangeInitiationAutoAcceptFixture()

        val refusal = assertThrows<IllegalArgumentException> {
            fixture.initiate(documents = emptyList(), blueprintDefinitionId = blueprintId)
        }

        assertEquals("Add at least one document", refusal.message)
        verify(fixture.blueprintInformationRequestStarter)
            .prepare(blueprintId, fixture.initiator.id, fixture.organizationId)
        verify(fixture.exchangeRepository, never()).save(any())
    }

    @Test
    fun `a Blueprint whose Information Request collects the documents starts with none`() {
        val fixture = ExchangeInitiationAutoAcceptFixture()
        val start = ExchangeBlueprintInformationRequestStart(blueprintId, templateVersionId)
        whenever(
            fixture.blueprintInformationRequestStarter.prepare(
                blueprintId,
                fixture.initiator.id,
                fixture.organizationId
            )
        )
            .thenReturn(start)

        val exchange = fixture.initiate(documents = emptyList(), blueprintDefinitionId = blueprintId)

        assertTrue(exchange.documents.isEmpty())
        verify(fixture.blueprintInformationRequestStarter).start(
            eq(start),
            eq(exchange.id),
            eq(fixture.initiator.id),
            any()
        )
    }

    @Test
    fun `the request starts after the owner Share and before any Workflow sees the Exchange`() {
        val fixture = ExchangeInitiationAutoAcceptFixture()
        val start = ExchangeBlueprintInformationRequestStart(blueprintId, templateVersionId)
        whenever(
            fixture.blueprintInformationRequestStarter.prepare(
                blueprintId,
                fixture.initiator.id,
                fixture.organizationId
            )
        )
            .thenReturn(start)

        val exchange = fixture.initiate(blueprintDefinitionId = blueprintId)

        val order =
            inOrder(fixture.shareService, fixture.blueprintInformationRequestStarter, fixture.workflowEngineService)
        order.verify(fixture.shareService).grant(
            eq(ResourceType.EXCHANGE),
            eq(exchange.id),
            any(),
            eq(fixture.initiator.id),
            eq(ExchangeShareRoleName.OWNER),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            any(),
            anyOrNull(),
        )
        order.verify(fixture.blueprintInformationRequestStarter)
            .start(eq(start), eq(exchange.id), eq(fixture.initiator.id), any())
        order.verify(fixture.workflowEngineService).trigger(argThat { triggerEvent == "exchange.draft_submitted" })
    }
}
