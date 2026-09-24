package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.auth.RealtimeTicketIdentity
import com.docuhyphen.app.api.model.dto.RealtimeTicketDto
import io.vertx.mutiny.redis.client.Command
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Request
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@ApplicationScoped
class RealtimeTicketService @Inject constructor(
    private val redis: Redis,
)
{
    companion object
    {
        private const val PREFIX = "realtime_ticket:"
        private const val TICKET_BYTES = 32
        const val TICKET_TTL_SECONDS = 30L
        private val secureRandom = SecureRandom()
    }

    fun issue(sessionId: UUID, appUserId: UUID): RealtimeTicketDto
    {
        val ticket = ByteArray(TICKET_BYTES)
            .also(secureRandom::nextBytes)
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        redis.send(
            Request.cmd(Command.SET)
                .arg(key(ticket))
                .arg("$sessionId:$appUserId")
                .arg("EX")
                .arg(TICKET_TTL_SECONDS.toString())
                .arg("NX")
        ).await().indefinitely()
        return RealtimeTicketDto(ticket, TICKET_TTL_SECONDS)
    }

    fun redeem(ticket: String): RealtimeTicketIdentity?
    {
        if (ticket.length !in 40..128) return null
        val value = redis.send(
            Request.cmd(Command.GETDEL).arg(key(ticket))
        ).await().indefinitely()?.toString() ?: return null
        val parts = value.split(':', limit = 2)
        if (parts.size != 2) return null
        val sessionId = runCatching { UUID.fromString(parts[0]) }.getOrNull() ?: return null
        val appUserId = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return null
        return RealtimeTicketIdentity(sessionId, appUserId)
    }

    private fun key(ticket: String): String
    {
        val digest = MessageDigest.getInstance("SHA-256").digest(ticket.toByteArray(Charsets.UTF_8))
        return PREFIX + digest.joinToString("") { "%02x".format(it) }
    }
}
