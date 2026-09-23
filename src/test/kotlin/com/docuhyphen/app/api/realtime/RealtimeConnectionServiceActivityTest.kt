package com.docuhyphen.app.api.realtime

import com.docuhyphen.app.api.service.auth.RealtimeTicketService
import com.docuhyphen.app.api.service.auth.SessionRevocationCache
import com.docuhyphen.app.api.service.auth.UserSessionService
import com.docuhyphen.app.api.service.exchange.RealtimeExchangeAccessService
import jakarta.websocket.RemoteEndpoint
import jakarta.websocket.Session
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class RealtimeConnectionServiceActivityTest
{
    private val registry = mock<RealtimeSessionRegistry>()
    private val userSessionService = mock<UserSessionService>()
    private val realtimeExchangeAccessService = mock<RealtimeExchangeAccessService>()
    private val service = RealtimeConnectionService(
        registry,
        mock<PresenceRegistry>(),
        mock<ExchangeViewerRegistry>(),
        userSessionService,
        mock<SessionRevocationCache>(),
        mock<RealtimeTicketService>(),
        realtimeExchangeAccessService,
        mock<RealtimeSessionDeadlineService>(),
    )

    @Test
    fun `transport ping does not extend user activity`()
    {
        val socket = mock<Session>()
        val remote = mock<RemoteEndpoint.Basic>()
        val userSessionId = UUID.randomUUID()
        whenever(registry.getUserId(userSessionId)).thenReturn(UUID.randomUUID())
        whenever(registry.getSocket(userSessionId)).thenReturn(socket)
        whenever(socket.isOpen).thenReturn(true)
        whenever(socket.basicRemote).thenReturn(remote)
        service.handleMessage("{\"type\":\"PING\"}", userSessionId.toString())

        verify(userSessionService, never()).touchSession(any())
        verify(remote).sendText(check { assertTrue(it.contains("\"type\":\"PONG\"")) })
    }

    @Test
    fun `unauthorized Exchange subscription is rejected`()
    {
        val userSessionId = UUID.randomUUID()
        val appUserId = UUID.randomUUID()
        val exchangeId = UUID.randomUUID()
        val socket = mock<Session>()
        val remote = mock<RemoteEndpoint.Basic>()
        whenever(registry.getUserId(userSessionId)).thenReturn(appUserId)
        whenever(registry.getSocket(userSessionId)).thenReturn(socket)
        whenever(socket.isOpen).thenReturn(true)
        whenever(socket.basicRemote).thenReturn(remote)
        whenever(realtimeExchangeAccessService.canSubscribe(appUserId, userSessionId, exchangeId)).thenReturn(false)

        service.handleMessage(
            "{\"type\":\"SUBSCRIBE_EXCHANGE\",\"exchangeId\":\"$exchangeId\"}",
            userSessionId.toString(),
        )

        verify(registry, never()).subscribeToExchange(any(), any())
        verify(remote).sendText(check { assertTrue(it.contains("ACCESS_DENIED")) })
    }
}
