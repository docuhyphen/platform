package com.docuhyphen.app.api.realtime

import com.docuhyphen.app.api.model.entity.AuthTokenType
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.SessionRevocationCache
import com.docuhyphen.app.api.service.auth.UserSessionService
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

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
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        private const val CLOSE_CODE_AUTH_FAILED = 4401
        private const val CLOSE_CODE_BAD_REQUEST = 4400
    }

    @Inject
    private lateinit var registry: RealtimeSessionRegistry

    @Inject
    private lateinit var presence: PresenceRegistry

    @Inject
    private lateinit var viewers: ExchangeViewerRegistry

    @Inject
    private lateinit var authenticationService: AuthenticationService

    @Inject
    private lateinit var userSessionService: UserSessionService

    @Inject
    private lateinit var sessionRevocationCache: SessionRevocationCache

    @OnOpen
    fun onOpen(session: Session, @PathParam("userSessionId") userSessionIdStr: String)
    {
        logger.info("Realtime @OnOpen received userSessionIdParam={} query={}", userSessionIdStr, session.queryString)
        val userSessionId = parseUuid(userSessionIdStr) ?: run {
            closeBad(session, "invalid userSessionId path")
            return
        }

        val token = extractTokenFromQuery(session)
        if (token.isNullOrBlank())
        {
            closeAuth(session, "missing token")
            return
        }

        val claims = authenticationService.verifyAccessToken(token)
        if (claims == null)
        {
            closeAuth(session, "invalid token")
            return
        }

        val tokenType = (claims["token_type"] as? String)?.trim()?.uppercase().orEmpty()
        if (tokenType != AuthTokenType.ACCESS.name)
        {
            closeAuth(session, "non-access token")
            return
        }

        val tokenSessionId = parseUuid(claims["exchange_id"] as? String)
        if (tokenSessionId == null || tokenSessionId != userSessionId)
        {
            closeAuth(session, "exchange_id mismatch")
            return
        }

        val appUserId = parseUuid(claims.subject) ?: run {
            closeAuth(session, "invalid subject")
            return
        }

        if (sessionRevocationCache.isRevoked(userSessionId))
        {
            closeAuth(session, "session revoked")
            return
        }
        if (!userSessionService.isActiveSession(userSessionId, appUserId))
        {
            closeAuth(session, "session inactive")
            return
        }

        registry.addSocket(userSessionId, appUserId, session)
        presence.markOnline(appUserId)
        userSessionService.touchSession(userSessionId)

        runCatching {
            session.basicRemote.sendText(
                json.encodeToString(
                    RealtimeMessage(
                        type = RealtimeMessageType.WELCOME,
                        userSessionId = userSessionId.toString(),
                        serverTime = System.currentTimeMillis(),
                    )
                )
            )
        }
        logger.info("Realtime onOpen userSessionId={} appUserId={}", userSessionId, appUserId)
    }

    @OnClose
    fun onClose(session: Session, @PathParam("userSessionId") userSessionIdStr: String, closeReason: CloseReason?)
    {
        val userSessionId = parseUuid(userSessionIdStr) ?: return
        val appUserId = registry.getUserId(userSessionId)
        viewers.cleanupUserSession(userSessionId)
        registry.removeSocket(userSessionId)
        if (appUserId != null) presence.markOffline(appUserId)
        logger.info("Realtime onClose userSessionId={} code={} reason={}", userSessionId, closeReason?.closeCode?.code, closeReason?.reasonPhrase)
    }

    @OnError
    fun onError(@PathParam("userSessionId") userSessionIdStr: String, throwable: Throwable)
    {
        logger.warn("Realtime onError userSessionId={} message={}", userSessionIdStr, throwable.message)
    }

    @OnMessage
    fun onMessage(text: String, @PathParam("userSessionId") userSessionIdStr: String)
    {
        val userSessionId = parseUuid(userSessionIdStr) ?: return
        val appUserId = registry.getUserId(userSessionId) ?: return

        val parsed = runCatching { json.decodeFromString<RealtimeMessage>(text) }.getOrNull() ?: run {
            logger.warn("Unparseable realtime message userSessionId={}", userSessionId)
            return
        }

        when (parsed.type)
        {
            RealtimeMessageType.PING ->
            {
                userSessionService.touchSession(userSessionId)
                sendTo(userSessionId, RealtimeMessage(type = RealtimeMessageType.PONG, serverTime = System.currentTimeMillis()))
            }
            RealtimeMessageType.SUBSCRIBE_EXCHANGE ->
            {
                val exchangeId = parseUuid(parsed.exchangeId) ?: return
                registry.subscribeToExchange(userSessionId, exchangeId)
                viewers.markViewing(exchangeId, appUserId, userSessionId)
                // Send the current viewer list to the new subscriber.
                sendTo(
                    userSessionId,
                    RealtimeMessage(
                        type = RealtimeMessageType.SHARING_VIEWERS,
                        exchangeId = exchangeId.toString(),
                        viewerUserIds = viewers.viewerUserIds(exchangeId).map { it.toString() },
                    )
                )
            }
            RealtimeMessageType.UNSUBSCRIBE_EXCHANGE ->
            {
                val exchangeId = parseUuid(parsed.exchangeId) ?: return
                registry.unsubscribeFromExchange(userSessionId, exchangeId)
                viewers.markNotViewing(exchangeId, appUserId, userSessionId)
            }
            else -> logger.warn("Unhandled realtime message type={} userSessionId={}", parsed.type, userSessionId)
        }
    }

    private fun sendTo(userSessionId: UUID, message: RealtimeMessage)
    {
        val socket = registry.getSocket(userSessionId) ?: return
        if (!socket.isOpen) return
        runCatching { socket.basicRemote.sendText(json.encodeToString(message)) }
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

    private fun parseUuid(s: String?): UUID? = runCatching { UUID.fromString(s) }.getOrNull()

    private fun closeAuth(session: Session, reason: String)
    {
        logger.warn("Realtime auth failed: {}", reason)
        runCatching { session.close(CloseReason({ CLOSE_CODE_AUTH_FAILED }, reason)) }
    }

    private fun closeBad(session: Session, reason: String)
    {
        runCatching { session.close(CloseReason({ CLOSE_CODE_BAD_REQUEST }, reason)) }
    }
}
