package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.model.entity.NotificationDeliveryLog
import com.docuhyphen.app.api.model.entity.NotificationDeliveryOutcome
import com.docuhyphen.app.api.model.notification.ChannelSendResult
import com.docuhyphen.app.api.model.notification.DeliveryTask
import com.docuhyphen.app.api.repository.notification.NotificationDeliveryLogRepository
import com.docuhyphen.app.api.repository.notification.OrganizationNotificationChannelRepository
import com.docuhyphen.app.api.service.notification.channels.NotificationChannel
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class DeliveryDispatcher
{
    private val logger = LoggerFactory.getLogger(DeliveryDispatcher::class.java)

    @Inject
    lateinit var channels: Instance<NotificationChannel>
    @Inject
    private lateinit var logRepository: NotificationDeliveryLogRepository
    @Inject
    private lateinit var orgChannelRepository: OrganizationNotificationChannelRepository

    fun dispatchAll(tasks: Collection<DeliveryTask>) = tasks.forEach { dispatch(it) }

    @Transactional
    fun dispatch(task: DeliveryTask)
    {
        val outcome = attempt(task, attemptNumber = 1)
        if (outcome is ChannelSendResult.Failed)
        {
            walkFallbackChain(task)
        }
    }

    private fun attempt(task: DeliveryTask, attemptNumber: Int): ChannelSendResult
    {
        val channelBean = channels.firstOrNull { it.type == task.channel }
        if (channelBean == null)
        {
            logger.debug("No channel bean registered for {}; skipping", task.channel)
            logOutcome(task, NotificationDeliveryOutcome.SKIPPED, "channel not registered", attemptNumber)
            return ChannelSendResult.Failed("channel ${task.channel} not registered")
        }
        val outcome = try
        {
            channelBean.send(task)
        }
        catch (t: Throwable)
        {
            logger.warn("Channel {} threw for event {}: {}", task.channel, task.event.id, t.message)
            ChannelSendResult.Failed(t.message ?: t.javaClass.simpleName)
        }

        when (outcome)
        {
            ChannelSendResult.Delivered ->
                logOutcome(task, NotificationDeliveryOutcome.DELIVERED, attempt = attemptNumber)

            ChannelSendResult.Suppressed ->
                logOutcome(task, NotificationDeliveryOutcome.SUPPRESSED, attempt = attemptNumber)

            ChannelSendResult.QuietHours ->
                logOutcome(task, NotificationDeliveryOutcome.QUIET_HOURS, attempt = attemptNumber)

            is ChannelSendResult.Failed ->
                logOutcome(task, NotificationDeliveryOutcome.FAILED, outcome.errorMessage, attemptNumber)
        }
        return outcome
    }

    private fun walkFallbackChain(task: DeliveryTask)
    {
        val orgId = runCatching { task.event.organizationId?.let(UUID::fromString) }.getOrNull()
        if (orgId == null)
        {
            logger.debug("No org context on event {}; no fallback chain to walk", task.event.id)
            return
        }

        val chain = orgChannelRepository.findActive(orgId, task.channel)?.fallbackChain
            ?.split(",")
            ?.mapNotNull { runCatching { NotificationChannelType.valueOf(it.trim()) }.getOrNull() }
            ?: emptyList()
        if (chain.isEmpty()) return

        val tried = mutableSetOf(task.channel)
        var attemptNumber = 2
        for (fallbackChannel in chain)
        {
            if (!tried.add(fallbackChannel)) continue
            val result = attempt(task.copy(channel = fallbackChannel), attemptNumber)
            attemptNumber++
            if (result !is ChannelSendResult.Failed) return
        }
        logger.warn(
            "Fallback chain exhausted for event {} user {} (primary {} + {} fallback channel(s))",
            task.event.id, task.recipientUserId, task.channel, chain.size,
        )
    }

    private fun logOutcome(
        task: DeliveryTask,
        outcome: NotificationDeliveryOutcome,
        errorMessage: String? = null,
        attempt: Int = 1,
    )
    {
        val row = NotificationDeliveryLog().apply {
            eventId = task.event.eventUuid()
            eventType = task.event.type
            appUserId = task.recipientUserId
            channel = task.channel
            this.outcome = outcome
            attemptNumber = attempt
            this.errorMessage = errorMessage?.take(2048)
            createdAt = Timestamp.from(Instant.now())
        }
        logRepository.save(row)
    }
}

@Suppress("unused")
private val keep = NotificationChannelType.IN_APP
