package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.realtime.RealtimeMessage
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Status
import jakarta.transaction.Synchronization
import jakarta.transaction.TransactionSynchronizationRegistry
import org.slf4j.LoggerFactory
import java.util.UUID

@ApplicationScoped
class DocumentCommentRealtimeService @Inject constructor(
    private val realtimeEventService: RealtimeEventService,
    private val transactionSynchronizationRegistry: TransactionSynchronizationRegistry,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(DocumentCommentRealtimeService::class.java)
    }

    fun scheduleAfterCommit(
        exchangeId: UUID,
        message: RealtimeMessage,
        recipientUserIds: Set<UUID>?,
    )
    {
        val recipientSnapshot = recipientUserIds?.toSet()
        transactionSynchronizationRegistry.registerInterposedSynchronization(
            object : Synchronization
            {
                override fun beforeCompletion() = Unit

                override fun afterCompletion(status: Int)
                {
                    if (status != Status.STATUS_COMMITTED) return
                    runCatching {
                        if (recipientSnapshot == null)
                        {
                            realtimeEventService.broadcastToExchange(exchangeId, message)
                        }
                        else
                        {
                            recipientSnapshot.forEach { realtimeEventService.broadcastToUser(it, message) }
                        }
                    }.onFailure { exception ->
                        logger.warn("Failed to broadcast committed document comment for Exchange={}", exchangeId, exception)
                    }
                }
            },
        )
    }
}
