package com.docuhyphen.app.api.repository.notification

import com.docuhyphen.app.api.model.entity.NotificationPreference
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class NotificationPreferenceRepository :
    BaseRepository<NotificationPreference>(NotificationPreference::class.java)
{
    fun findActiveForUser(appUserId: UUID): List<NotificationPreference> =
        entityManager.createQuery(
            """SELECT p FROM NotificationPreference p
               WHERE p.appUserId = :uid AND p.isActive = true""",
            NotificationPreference::class.java,
        )
            .setParameter("uid", appUserId)
            .resultList
}

