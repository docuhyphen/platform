package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.InAppNotification
import com.docuhyphen.app.api.model.entity.NotificationDeliveryLog
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InAppNotificationRepository : BaseRepository<InAppNotification>(InAppNotification::class.java)
{
    fun findPageForUser(
        appUserId: UUID,
        limit: Int,
        beforeTimestamp: java.sql.Timestamp?,
        beforeId: UUID?,
    ): List<InAppNotification>
    {
        val hasCursor = beforeTimestamp != null && beforeId != null
        val query = entityManager.createQuery(
            if (hasCursor)
            {
                """SELECT n FROM InAppNotification n
                   WHERE n.appUserId = :uid
                     AND (n.createdAt < :beforeTimestamp
                          OR (n.createdAt = :beforeTimestamp AND n.id < :beforeId))
                   ORDER BY n.createdAt DESC, n.id DESC"""
            }
            else
            {
                """SELECT n FROM InAppNotification n
                   WHERE n.appUserId = :uid
                   ORDER BY n.createdAt DESC, n.id DESC"""
            },
            InAppNotification::class.java,
        ).setParameter("uid", appUserId)

        if (hasCursor)
        {
            query.setParameter("beforeTimestamp", beforeTimestamp)
            query.setParameter("beforeId", beforeId)
        }
        return query.setMaxResults(limit).resultList
    }

    fun countUnread(appUserId: UUID): Long =
        entityManager.createQuery(
            """SELECT COUNT(n) FROM InAppNotification n
               WHERE n.appUserId = :uid AND n.isRead = false""",
            Long::class.java,
        )
            .setParameter("uid", appUserId)
            .singleResult ?: 0

    fun findUnreadForUser(appUserId: UUID): List<InAppNotification> =
        entityManager.createQuery(
            """SELECT n FROM InAppNotification n
               WHERE n.appUserId = :uid AND n.isRead = false""",
            InAppNotification::class.java,
        )
            .setParameter("uid", appUserId)
            .resultList

    fun findUnreadByIdsForUser(appUserId: UUID, notificationIds: Set<UUID>): List<InAppNotification>
    {
        if (notificationIds.isEmpty())
        {
            return emptyList()
        }

        return entityManager.createQuery(
            """SELECT n FROM InAppNotification n
               WHERE n.appUserId = :uid
                 AND n.isRead = false
                 AND n.id IN :notificationIds""",
            InAppNotification::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("notificationIds", notificationIds)
            .resultList
    }

    fun findUnreadByEventTypesForUser(appUserId: UUID, eventTypes: Set<String>): List<InAppNotification>
    {
        if (eventTypes.isEmpty())
        {
            return emptyList()
        }

        return entityManager.createQuery(
            """SELECT n FROM InAppNotification n
               WHERE n.appUserId = :uid
                 AND n.isRead = false
                 AND n.eventType IN :eventTypes""",
            InAppNotification::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("eventTypes", eventTypes)
            .resultList
    }
}

@ApplicationScoped
class NotificationDeliveryLogRepository :
    BaseRepository<NotificationDeliveryLog>(NotificationDeliveryLog::class.java)
{
    fun findByEvent(eventId: UUID): List<NotificationDeliveryLog> =
        entityManager.createQuery(
            "SELECT l FROM NotificationDeliveryLog l WHERE l.eventId = :eid ORDER BY l.createdAt ASC",
            NotificationDeliveryLog::class.java,
        )
            .setParameter("eid", eventId)
            .resultList
}

