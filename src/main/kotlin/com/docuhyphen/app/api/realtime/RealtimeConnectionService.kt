package com.docuhyphen.app.api.realtime

import com.docuhyphen.app.api.service.auth.RealtimeTicketService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.SessionRevocationCache
import com.docuhyphen.app.api.service.auth.UserSessionService
import com.docuhyphen.app.api.service.exchange.RealtimeExchangeAccessService
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
    private val userSessionService: UserSessionService,
    private val sessionRevocationCache: SessionRevocationCache,
    private val realtimeTicketService: RealtimeTicketService,
    private val realtimeExchangeAccessService: RealtimeExchangeAccessService,
    private val realtimeSessionDeadlineService: RealtimeSessionDeadlineService,
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
    fun open(session: Session, userSessionIdValue: String, ticket: String?)
    {
        val userSessionId = parseUuid(userSessionIdValue) ?: run {
            close(session, CLOSE_CODE_BAD_REQUEST, "invalid userSessionId path")
            return
        }

        if (ticket.isNullOrBlank())
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "missing ticket")
            return
        }

        val identity = runCatching { realtimeTicketService.redeem(ticket) }.getOrNull()
        if (identity == null)
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "invalid ticket")
            return
        }
        if (identity.sessionId != userSessionId)
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "ticket session mismatch")
            return
        }
        if (sessionRevocationCache.isRevoked(userSessionId))
        {
            close(session, CLOSE_CODE_AUTH_FAILED, "session revoked")
            return
        }
        val endReason = userSessionService.accessEndReason(userSessionId, identity.appUserId)
        if (endReason != null)
        {
            close(session, RealtimeEventService.CLOSE_CODE_SESSION_REVOKED, "session revoked: ${endReason.name}")
            return
        }
        val deadlines = userSessionService.deadlines(userSessionId) ?: run {
            close(session, CLOSE_CODE_AUTH_FAILED, "session unavailable")
            return
        }

        registry.addSocket(userSessionId, identity.appUserId, session)
        realtimeSessionDeadlineService.schedule(userSessionId, session, deadlines)
        presence.markOnline(identity.appUserId)
        sendTo(
            userSessionId,
            RealtimeMessage(
                type = RealtimeMessageType.WELCOME,
                userSessionId = userSessionId.toString(),
                serverTime = System.currentTimeMillis(),
            ),
        )
        logger.info("Realtime connection opened userSessionId={} appUserId={}", userSessionId, identity.appUserId)
    }

    @ActivateRequestContext
    @Transactional
    fun handleMessage(text: String, userSessionIdValue: String)
    {
        val userSessionId = parseUuid(userSessionIdValue) ?: return
        val appUserId = registry.getUserId(userSessionId) ?: return
        val endReason = userSessionService.accessEndReason(userSessionId, appUserId)
        if (endReason != null)
        {
            userSessionService.revokeSession(userSessionId, endReason)
            return
        }
        val message = runCatching { json.decodeFromString<RealtimeMessage>(text) }.getOrNull() ?: run {
            logger.warn("Unparseable realtime message userSessionId={}", userSessionId)
            return
        }

        when (message.type)
        {
            RealtimeMessageType.PING ->
            {
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
                if (!realtimeExchangeAccessService.canSubscribe(appUserId, userSessionId, exchangeId))
                {
                    sendTo(
                        userSessionId,
                        RealtimeMessage(
                            type = RealtimeMessageType.ERROR,
                            code = "ACCESS_DENIED",
                            message = "Exchange subscription denied",
                        ),
                    )
                    return
                }
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
        realtimeSessionDeadlineService.cancel(userSessionId, session)
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
