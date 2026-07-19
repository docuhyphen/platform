package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.exception.ExchangeRecipientEligibilityException
import com.docuhyphen.app.api.model.entity.ExchangeAcceptanceDecision
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.resource.model.ExchangeAcceptanceDecisionRequest
import com.docuhyphen.app.api.resource.model.UpdateNoAuthExchange
import com.docuhyphen.app.api.service.exchange.ExchangeAcceptanceService
import com.docuhyphen.app.api.service.exchange.ExchangeDocumentService
import com.docuhyphen.app.api.service.exchange.ExchangeRetrievalService
import com.docuhyphen.app.api.service.exchange.ExchangeUpdateService
import com.docuhyphen.app.api.service.exchange.ShareLinkValidationService
import com.docuhyphen.app.api.service.storage.FileStorageService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeRecipientEligibilityResourceTest
{
    @Test
    fun `authenticated acceptance maps stale eligibility to generic conflict`()
    {
        val service = mock<ExchangeAcceptanceService>()
        doThrow(
            ExchangeRecipientEligibilityException(
                cause = IllegalArgumentException("receiver policy denied"),
            ),
        ).whenever(service).decide(any(), any(), anyOrNull())
        val resource = ExchangeAcceptanceResource(service)

        val response = resource.decide(
            UUID.randomUUID().toString(),
            ExchangeAcceptanceDecisionRequest(ExchangeAcceptanceDecision.ACCEPT, null),
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(
            "This Exchange can no longer be accepted",
            (response.entity as com.docuhyphen.app.api.resource.model.ResponseError).errorMessage,
        )
    }

    @Test
    fun `no-auth acceptance maps stale eligibility to generic conflict`()
    {
        val exchangeId = UUID.randomUUID().toString()
        val updateService = mock<ExchangeUpdateService> {
            on {
                updateNoAuthExchange(
                    eq(exchangeId),
                    eq(ExchangeStatus.ACCEPTED_STARTED),
                    eq("123456"),
                    eq(null),
                    eq("access-token"),
                )
            } doThrow ExchangeRecipientEligibilityException(
                cause = IllegalArgumentException("relationship suspended"),
            )
        }
        val resource = NoAuthExchangeResource(
            mock<ExchangeRetrievalService>(),
            updateService,
            mock<ExchangeDocumentService>(),
            mock<FileStorageService>(),
            mock<ShareLinkValidationService>(),
        )

        val response = resource.updateNoAuthExchange(
            exchangeId,
            "access-token",
            UpdateNoAuthExchange(
                status = ExchangeStatus.ACCEPTED_STARTED,
                otp = "123456",
            ),
        )

        assertEquals(Response.Status.CONFLICT.statusCode, response.status)
        assertEquals(
            "This Exchange can no longer be accepted",
            (response.entity as com.docuhyphen.app.api.resource.model.ResponseError).errorMessage,
        )
    }
}
