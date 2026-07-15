package com.docuhyphen.app.api.realtime

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.CloseReason
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import jakarta.websocket.server.PathParam
import jakarta.websocket.server.ServerEndpoint
import org.eclipse.microprofile.context.ManagedExecutor
import org.slf4j.LoggerFactory

/**
 * One socket per [UserSession] (per device/browser). Auth: opening handshake's `?token=…`
 * must be a valid ACCESS token whose `exchange_id` claim equals the path's `userSessionId`.
 *
 * Why per-userSession and not per-user: revocation and presence both want to act on one
 * device at a time. Two browsers for the same user need independent lifecycles.
 */
@ServerEndpoint("/realtime/{userSessionId}")
@ApplicationScoped
class RealtimeWebSocket
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RealtimeWebSocket::class.java)
    }

    @Inject
    private lateinit var connectionService: RealtimeConnectionService

    @Inject
    private lateinit var executor: ManagedExecutor

    @OnOpen
    fun onOpen(session: Session, @PathParam("userSessionId") userSessionIdStr: String)
    {
        val token = extractTokenFromQuery(session)
        logger.info("Realtime opening userSessionId={}", userSessionIdStr)
        executor.execute { connectionService.open(session, userSessionIdStr, token) }
    }

    @OnClose
    fun onClose(session: Session, @PathParam("userSessionId") userSessionIdStr: String, closeReason: CloseReason?)
    {
        executor.execute { connectionService.close(session, userSessionIdStr, closeReason) }
    }

    @OnError
    fun onError(@PathParam("userSessionId") userSessionIdStr: String, throwable: Throwable)
    {
        logger.warn("Realtime error userSessionId={}", userSessionIdStr, throwable)
    }

    @OnMessage
    fun onMessage(text: String, @PathParam("userSessionId") userSessionIdStr: String)
    {
        executor.execute { connectionService.handleMessage(text, userSessionIdStr) }
    }

    private fun extractTokenFromQuery(session: Session): String?
    {
        // Prefer the decoded parameter map; fall back to raw query string for safety.
        session.requestParameterMap?.get("token")?.firstOrNull()?.let { return it }
        val raw = session.queryString ?: return null
        return raw.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "token" }
            ?.let { java.net.URLDecoder.decode(it[1], Charsets.UTF_8) }
    }

}
