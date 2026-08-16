package com.docuhyphen.app.api.repository.notification

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.NotificationChannelType
import com.docuhyphen.app.api.model.entity.OrganizationNotificationChannel
import com.docuhyphen.app.api.model.entity.UserChannelLink
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class OrganizationNotificationChannelRepository :
    BaseRepository<OrganizationNotificationChannel>(OrganizationNotificationChannel::class.java)
{
    fun findActiveForOrg(organizationId: UUID): List<OrganizationNotificationChannel> =
        entityManager.createQuery(
            """SELECT c FROM OrganizationNotificationChannel c
               WHERE c.organizationId = :oid AND c.isActive = true""",
            OrganizationNotificationChannel::class.java,
        )
            .setParameter("oid", organizationId)
            .resultList

    fun findActive(organizationId: UUID, channel: NotificationChannelType): OrganizationNotificationChannel? =
        entityManager.createQuery(
            """SELECT c FROM OrganizationNotificationChannel c
               WHERE c.organizationId = :oid AND c.channel = :ch AND c.isActive = true""",
            OrganizationNotificationChannel::class.java,
        )
            .setParameter("oid", organizationId)
            .setParameter("ch", channel)
            .resultList
            .firstOrNull()
}

@ApplicationScoped
class UserChannelLinkRepository : BaseRepository<UserChannelLink>(UserChannelLink::class.java)
{
    fun findActiveForUserAndChannel(appUserId: UUID, orgChannelId: UUID): UserChannelLink? =
        entityManager.createQuery(
            """SELECT l FROM UserChannelLink l
               WHERE l.appUserId = :uid
                 AND l.organizationNotificationChannelId = :cid
                 AND l.isActive = true""",
            UserChannelLink::class.java,
        )
            .setParameter("uid", appUserId)
            .setParameter("cid", orgChannelId)
            .resultList
            .firstOrNull()
}

