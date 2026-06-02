package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.model.entity.NotificationDeliveryLog
import com.docuhyphen.app.api.model.entity.NotificationDeliveryOutcome
import com.docuhyphen.app.api.repository.NotificationDeliveryLogRepository
import com.docuhyphen.app.api.repository.OrganizationNotificationChannelRepository
import com.docuhyphen.app.api.service.notification.channels.NotificationChannel
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Dispatches a batch of [DeliveryTask]s. For each task:
 *   1. Pick the [NotificationChannel] bean matching the task's channel type.
 *   2. Invoke `send(task)`; capture outcome.
 *   3. Persist a [NotificationDeliveryLog] row.
 *   4. On `FAILED`, walk the org-level fallback chain (see [OrganizationNotificationChannel.fallbackChain])
 *      until something delivers or the chain is exhausted.
 */
@ApplicationScoped
class DeliveryDispatcher
{
    private val logger = LoggerFactory.getLogger(DeliveryDispatcher::class.java)

    @Inject lateinit var channels: Instance<NotificationChannel>
    @Inject private lateinit var logRepository: NotificationDeliveryLogRepository
    @Inject private lateinit var orgChannelRepository: OrganizationNotificationChannelRepository

    fun dispatchAll(tasks: Collection<DeliveryTask>) = tasks.forEach { dispatch(it) }

    @Transactional
    fun dispatch(task: DeliveryTask)
    {
        val outcome = attempt(task, attemptNumber = 1)
        // On a hard failure of the primary channel, walk the org's configured
        // fallback chain (e.g. SLACK,IN_APP,EMAIL) until one channel delivers.
        if (outcome is ChannelSendResult.Failed)
        {
            walkFallbackChain(task)
        }
    }

    /**
     * Send [task] via its channel bean and record the outcome under [attemptNumber].
     * Returns the channel result so the caller can decide whether to fall back.
     */
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

    /**
     * After the primary channel fails, resolve the org-level fallback chain configured on
     * the failed channel's [com.docuhyphen.app.api.model.entity.OrganizationNotificationChannel]
     * row and try each subsequent channel in order until one delivers (or the chain is
     * exhausted). The org context comes from the event itself; events without an org carry
     * no fallback configuration so nothing is attempted.
     */
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
            if (!tried.add(fallbackChannel)) continue   // skip the primary + any duplicates
            val result = attempt(task.copy(channel = fallbackChannel), attemptNumber)
            attemptNumber++
            // Stop as soon as a channel doesn't hard-fail (delivered / suppressed / quiet-hours).
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

/** Channel-level send result. Outcome rolls up into [NotificationDeliveryOutcome] in the log. */
sealed class ChannelSendResult
{
    object Delivered : ChannelSendResult()
    object Suppressed : ChannelSendResult()
    object QuietHours : ChannelSendResult()
    data class Failed(val errorMessage: String) : ChannelSendResult()
}

/** Suppress unused-warning for the channel-type import. */
@Suppress("unused")
private val keep = NotificationChannelType.IN_APP

