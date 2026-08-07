package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeNotFoundException
import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.ExchangeRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class ExchangeRetrievalServicePendingPrimaryTest
{
    private val repository = mock<ExchangeRepository>()
    private val authTokenContext = mock<AuthTokenContext>()
    private val authorizationService = mock<AuthorizationService>()
    private val authorizationContextFactory = mock<AuthorizationContextFactory>()
    private val shareService = mock<ShareService>()
    private val recipientService = mock<ExchangeRecipientService>()
    private val noAuthAccessTokenService = mock<NoAuthExchangeAccessTokenService>()
    private val service = ExchangeRetrievalService(
        repository,
        authTokenContext,
        authorizationService,
        authorizationContextFactory,
        shareService,
        recipientService,
        noAuthAccessTokenService,
        mock(),
    )

    @Test
    fun `direct retrieval permits an eligible pending primary group decision maker`()
    {
        val userId = UUID.randomUUID()
        val exchange = pendingExchange()
        val principal = PrincipalRef(PrincipalKind.USER, userId)
        whenever(repository.findByIdWithDocumentsOrderedByTitle(exchange.id)).thenReturn(exchange)
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        whenever(
            authorizationService.authorize(
                eq(principal),
                eq(Action.EXCHANGE_VIEW),
                eq(ResourceRef.exchange(exchange.id)),
                any(),
            ),
        ).thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "No active Share"))
        whenever(recipientService.canViewPendingPrimaryInvitation(exchange.id, userId)).thenReturn(true)

        assertEquals(exchange, service.getExchange(exchange.id.toString()))
    }

    @Test
    fun `direct retrieval remains non-enumerating for an ineligible group user`()
    {
        val userId = UUID.randomUUID()
        val exchange = pendingExchange()
        val principal = PrincipalRef(PrincipalKind.USER, userId)
        whenever(repository.findByIdWithDocumentsOrderedByTitle(exchange.id)).thenReturn(exchange)
        whenever(authorizationContextFactory.currentPrincipal()).thenReturn(principal)
        whenever(authorizationContextFactory.currentContext()).thenReturn(AuthorizationContext.ANONYMOUS)
        whenever(authorizationService.authorize(any(), any(), any(), any()))
            .thenReturn(Decision.Deny(Decision.REASON_NO_GRANT, "No active Share"))
        whenever(recipientService.canViewPendingPrimaryInvitation(exchange.id, userId)).thenReturn(false)

        assertThrows(ExchangeNotFoundException::class.java) {
            service.getExchange(exchange.id.toString())
        }
    }

    private fun pendingExchange(): Exchange = Exchange().apply {
        status = ExchangeStatus.INITIATED
        isDeleted = false
        documents = mutableListOf()
    }
}
