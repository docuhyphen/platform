package com.docuhyphen.app.api.realtime

import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.websocket.*
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
@ServerEndpoint(
    value = "/realtime/{userSessionId}",
    configurator = RealtimeOriginConfigurator::class,
)
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
        val ticket = extractTicketFromQuery(session)
        logger.info("Realtime opening userSessionId={}", userSessionIdStr)
        executor.execute { connectionService.open(session, userSessionIdStr, ticket) }
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

    private fun extractTicketFromQuery(session: Session): String?
    {
        session.requestParameterMap?.get("ticket")?.firstOrNull()?.let { return it }
        val raw = session.queryString ?: return null
        return raw.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "ticket" }
            ?.let { java.net.URLDecoder.decode(it[1], Charsets.UTF_8) }
    }

}
