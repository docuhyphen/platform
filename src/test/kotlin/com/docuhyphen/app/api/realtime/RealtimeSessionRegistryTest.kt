package com.docuhyphen.app.api.realtime

import jakarta.websocket.Session
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class RealtimeSessionRegistryTest
{
    @Test
    fun `closing a replaced socket does not remove the current socket`()
    {
        val registry = RealtimeSessionRegistry()
        val userSessionId = UUID.randomUUID()
        val appUserId = UUID.randomUUID()
        val originalSocket = mock<Session>()
        val replacementSocket = mock<Session>()
        whenever(originalSocket.isOpen).thenReturn(false)

        registry.addSocket(userSessionId, appUserId, originalSocket)
        registry.addSocket(userSessionId, appUserId, replacementSocket)

        assertFalse(registry.removeSocket(userSessionId, originalSocket))
        assertSame(replacementSocket, registry.getSocket(userSessionId))
        assertTrue(registry.removeSocket(userSessionId, replacementSocket))
    }
}
