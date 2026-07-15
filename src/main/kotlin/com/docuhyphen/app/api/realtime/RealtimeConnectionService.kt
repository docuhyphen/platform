package com.docuhyphen.app.api.realtime

import com.docuhyphen.app.api.model.entity.AuthTokenType
import com.docuhyphen.app.api.service.auth.AuthenticationService
import com.docuhyphen.app.api.service.auth.SessionRevocationCache
import com.docuhyphen.app.api.service.auth.UserSessionService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.websocket.CloseReason
import jakarta.websocket.Session
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

@ApplicationScoped
class RealtimeConnectionService @Inject constructor(
    private val registry: RealtimeSessionRegistry,
    private val presence: PresenceRegistry,
    private val viewers: ExchangeViewerRegistry,
    private val authenticationService: AuthenticationService,
    private val userSessionService: UserSessionService,
    private val sessionRevocationCache: SessionRevocationCache,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RealtimeConnectionService::class.java)
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        private const val CLOSE_CODE_AUTH_FAILED = 4401
        private const val CLOSE_CODE_BAD_REQUEST = 4400
    }

    @ActivateRequestContext
    @Transactional
    fun open(session: Session, userSessionIdValue: String, token: String?)
    {
        val userSessionId = parseUuid(userSessionIdValue) ?: run {
            close(session, CLOSE_CODE_BAD_REQUEST, "invalid userSessionId path")
            return
        }

        if (token.isNullOrBlank())
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "missing token")
            return
        }

        val claims = authenticationService.verifyAccessToken(token)
        if (claims == null)
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "invalid token")
            return
        }

        val tokenType = (claims["token_type"] as? String)?.trim()?.uppercase().orEmpty()
        val tokenSessionId = parseUuid(claims["exchange_id"] as? String)
        val appUserId = parseUuid(claims.subject)

        if (tokenType != AuthTokenType.ACCESS.name)
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "non-access token")
            return
        }
        if (tokenSessionId != userSessionId)
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "exchange_id mismatch")
            return
        }
        if (appUserId == null)
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "invalid subject")
            return
        }
        if (sessionRevocationCache.isRevoked(userSessionId))
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "session revoked")
            return
        }
        if (!userSessionService.isActiveSession(userSessionId, appUserId))
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "session inactive")
            return
        }

        registry.addSocket(userSessionId, appUserId, session)
        presence.markOnline(appUserId)
        userSessionService.touchSession(userSessionId)
        sendTo(
            userSessionId,
            RealtimeMessage(
                type = RealtimeMessageType.WELCOME,
                userSessionId = userSessionId.toString(),
                serverTime = System.currentTimeMillis(),
            ),
        )
        logger.info("Realtime connection opened userSessionId={} appUserId={}", userSessionId, appUserId)
    }

    @ActivateRequestContext
    @Transactional
    fun handleMessage(text: String, userSessionIdValue: String)
    {
        val userSessionId = parseUuid(userSessionIdValue) ?: return
        val appUserId = registry.getUserId(userSessionId) ?: return
        val message = runCatching { json.decodeFromString<RealtimeMessage>(text) }.getOrNull() ?: run {
            logger.warn("Unparseable realtime message userSessionId={}", userSessionId)
            return
        }

        when (message.type)
        {
            RealtimeMessageType.PING ->
            {
                userSessionService.touchSession(userSessionId)
                sendTo(
                    userSessionId,
                    RealtimeMessage(
                        type = RealtimeMessageType.PONG,
                        serverTime = System.currentTimeMillis(),
                    ),
                )
            }
            RealtimeMessageType.SUBSCRIBE_EXCHANGE ->
            {
                val exchangeId = parseUuid(message.exchangeId) ?: return
                registry.subscribeToExchange(userSessionId, exchangeId)
                viewers.markViewing(exchangeId, appUserId, userSessionId)
                sendTo(
                    userSessionId,
                    RealtimeMessage(
                        type = RealtimeMessageType.SHARING_VIEWERS,
                        exchangeId = exchangeId.toString(),
                        viewerUserIds = viewers.viewerUserIds(exchangeId).map { it.toString() },
                    ),
                )
            }
            RealtimeMessageType.UNSUBSCRIBE_EXCHANGE ->
            {
                val exchangeId = parseUuid(message.exchangeId) ?: return
                registry.unsubscribeFromExchange(userSessionId, exchangeId)
                viewers.markNotViewing(exchangeId, appUserId, userSessionId)
            }
            else -> logger.warn("Unhandled realtime message type={} userSessionId={}", message.type, userSessionId)
        }
    }

    fun close(session: Session, userSessionIdValue: String, closeReason: CloseReason?)
    {
        val userSessionId = parseUuid(userSessionIdValue) ?: return
        val appUserId = registry.getUserId(userSessionId)
        if (!registry.removeSocket(userSessionId, session)) return
        viewers.cleanupUserSession(userSessionId)
        if (appUserId != null) presence.markOffline(appUserId)
        logger.info(
            "Realtime connection closed userSessionId={} code={} reason={}",
            userSessionId,
            closeReason?.closeCode?.code,
            closeReason?.reasonPhrase,
        )
    }

    private fun sendTo(userSessionId: UUID, message: RealtimeMessage)
    {
        val socket = registry.getSocket(userSessionId) ?: return
        if (!socket.isOpen) return
        runCatching { socket.basicRemote.sendText(json.encodeToString(message)) }
            .onFailure { logger.warn("Failed to send realtime message type={}", message.type, it) }
    }

    private fun close(session: Session, code: Int, reason: String)
    {
        logger.warn("Realtime authentication failed: {}", reason)
        runCatching { session.close(CloseReason({ code }, reason)) }
    }

    private fun parseUuid(value: String?): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
}
