package com.docuhyphen.app.api.service.notification

import com.docuhyphen.app.api.model.InAppNotificationMapper
import com.docuhyphen.app.api.model.dto.NotificationDto
import com.docuhyphen.app.api.model.dto.NotificationListResponse
import com.docuhyphen.app.api.model.dto.NotificationPageCursorDto
import com.docuhyphen.app.api.model.entity.InAppNotification
import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.realtime.RealtimeEventService
import com.docuhyphen.app.api.repository.InAppNotificationRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class InAppNotificationService @Inject constructor(
    private val repository: InAppNotificationRepository,
    private val mapper: InAppNotificationMapper,
    private val preferenceService: UserNotificationPreferenceService,
    private val realtimeEventService: RealtimeEventService,
)
{
    private val json = Json { encodeDefaults = true }

    @Transactional
    fun listPage(
        appUserId: UUID,
        limit: Int,
        beforeTimestamp: Timestamp?,
        beforeId: UUID?,
    ): NotificationListResponse
    {
        val results = repository.findPageForUser(appUserId, limit + 1, beforeTimestamp, beforeId)
        val hasMore = results.size > limit
        val page = results.take(limit)
        val lastNotification = page.lastOrNull()
        return NotificationListResponse(
            notifications = page.map(mapper::toDto),
            nextCursor = lastNotification?.takeIf { hasMore }?.let {
                NotificationPageCursorDto(
                    timestamp = it.createdAt.time,
                    id = it.id.toString(),
                )
            },
            hasMore = hasMore,
            unreadCount = repository.countUnread(appUserId),
        )
    }

    @Transactional
    fun markAsRead(appUserId: UUID, criteria: NotificationReadCriteria): NotificationReadResult
    {
        val unread = when
        {
            criteria.all -> repository.findUnreadForUser(appUserId)
            criteria.notificationIds.isNotEmpty() -> repository.findUnreadByIdsForUser(
                appUserId,
                criteria.notificationIds,
            )
            criteria.eventTypes.isNotEmpty() -> repository.findUnreadByEventTypesForUser(
                appUserId,
                criteria.eventTypes,
            )
            else -> repository.findUnreadForUser(appUserId)
        }

        val matching = unread.filter { notification ->
            criteria.eventTypes.isEmpty() || notification.eventType in criteria.eventTypes
        }.filter { notification ->
            payloadMatches(notification, criteria.data)
        }

        val now = Timestamp.from(Instant.now())
        matching.forEach { notification ->
            notification.isRead = true
            notification.readAt = now
            repository.update(notification)
        }

        return NotificationReadResult(
            readNotificationIds = matching.map { it.id },
            unreadCount = repository.countUnread(appUserId),
        )
    }

    @Transactional
    fun publishIfEnabled(
        appUserId: UUID,
        preference: UserNotificationPreference,
        type: String,
        title: String,
        message: String,
        data: Map<String, String>,
    ): NotificationDto?
    {
        if (!preferenceService.isEnabled(appUserId, preference, NotificationChannelType.IN_APP))
        {
            return null
        }

        return publish(appUserId, type, title, message, data)
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun publishAfterCommitIfEnabled(
        appUserId: UUID,
        preference: UserNotificationPreference,
        type: String,
        title: String,
        message: String,
        data: Map<String, String>,
    ): NotificationDto?
    {
        if (!preferenceService.isEnabled(appUserId, preference, NotificationChannelType.IN_APP))
        {
            return null
        }

        return publish(appUserId, type, title, message, data)
    }

    private fun publish(
        appUserId: UUID,
        type: String,
        title: String,
        message: String,
        data: Map<String, String>,
    ): NotificationDto
    {
        val notification = InAppNotification().apply {
            this.appUserId = appUserId
            eventType = type
            this.title = title
            body = message
            payloadJson = json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                data,
            )
            createdAt = Timestamp.from(Instant.now())
        }
        val dto = mapper.toDto(repository.save(notification))
        realtimeEventService.broadcastNotificationToUser(appUserId, dto)
        return dto
    }

    private fun payloadMatches(notification: InAppNotification, data: Map<String, String>): Boolean
    {
        if (data.isEmpty())
        {
            return true
        }

        val payload = notification.payloadJson
            ?.let { payloadJson ->
                runCatching {
                    json.parseToJsonElement(payloadJson) as? JsonObject
                }.getOrNull()
            }
            ?: return false

        return data.all { (key, value) ->
            (payload[key] as? JsonPrimitive)?.content == value
        }
    }

    @Transactional
    fun publishAdministrative(
        appUserId: UUID,
        type: String,
        title: String,
        message: String,
        data: Map<String, String>,
    ): NotificationDto
    {
        val notification = InAppNotification().apply {
            this.appUserId = appUserId
            eventType = type
            this.title = title
            body = message
            payloadJson = json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                data,
            )
            createdAt = Timestamp.from(Instant.now())
        }
        val dto = mapper.toDto(repository.save(notification))
        realtimeEventService.broadcastNotificationToUser(appUserId, dto)
        return dto
    }
}

data class NotificationReadCriteria(
    val all: Boolean = false,
    val notificationIds: Set<UUID> = emptySet(),
    val eventTypes: Set<String> = emptySet(),
    val data: Map<String, String> = emptyMap(),
)

data class NotificationReadResult(
    val readNotificationIds: List<UUID>,
    val unreadCount: Long,
)
