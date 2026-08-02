package com.docuhyphen.app.api.resource.exchange

import com.docuhyphen.app.api.exception.OrganizationTrustNotFoundException
import com.docuhyphen.app.api.model.dto.ExchangeRecipientInvitationDto
import com.docuhyphen.app.api.model.entity.ExchangeAcceptanceDecision
import com.docuhyphen.app.api.resource.model.ExchangeRecipientInvitationDecisionRequest
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientInvitationService
import jakarta.ws.rs.core.GenericEntity
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeRecipientInvitationResourceTest
{
    private val service = mock<ExchangeRecipientInvitationService>()
    private val resource = ExchangeRecipientInvitationResource(service)
    private val recipientId = UUID.randomUUID()

    @Test
    fun `pending invitation response preserves its serializable element type`()
    {
        val invitation = mock<ExchangeRecipientInvitationDto>()
        whenever(service.listPending()).thenReturn(listOf(invitation))

        val response = resource.listPending()
        val entity = response.entity as GenericEntity<*>

        assertEquals(Response.Status.OK.statusCode, response.status)
        assertEquals(listOf(invitation), entity.entity)
        assertTrue(entity.type.typeName.contains("ExchangeRecipientInvitationDto"))
    }

    @Test
    fun `accept and reject decisions delegate to the recipient-specific service`()
    {
        listOf(ExchangeAcceptanceDecision.ACCEPT, ExchangeAcceptanceDecision.REJECT).forEach { decision ->
            val response = resource.decide(
                recipientId.toString(),
                ExchangeRecipientInvitationDecisionRequest(decision),
            )

            assertEquals(Response.Status.NO_CONTENT.statusCode, response.status)
            verify(service).decide(recipientId, decision)
        }
    }

    @Test
    fun `stale trust returns a generic conflict`()
    {
        doThrow(OrganizationTrustNotFoundException("internal policy detail"))
            .`when`(service)
            .decide(recipientId, ExchangeAcceptanceDecision.ACCEPT)

        val response = resource.decide(
            recipientId.toString(),
            ExchangeRecipientInvitationDecisionRequest(ExchangeAcceptanceDecision.ACCEPT),
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
    }

    @Test
    fun `tampered identifiers return bad request without service invocation`()
    {
        val response = resource.decide(
            "not-an-exchange-id",
            ExchangeRecipientInvitationDecisionRequest(ExchangeAcceptanceDecision.ACCEPT),
        )

        assertEquals(Response.Status.BAD_REQUEST.statusCode, response.status)
    }
}
