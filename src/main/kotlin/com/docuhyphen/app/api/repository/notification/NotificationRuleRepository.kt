package com.docuhyphen.app.api.repository.notification

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.NotificationRule
import com.docuhyphen.app.api.model.entity.NotificationRuleScope
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class NotificationRuleRepository : BaseRepository<NotificationRule>(NotificationRule::class.java)
{
    /** Active rules that could match the given event, ordered by priority (lowest first wins for SUPPRESS chains). */
    fun findActiveForEvent(eventType: String, organizationId: UUID?): List<NotificationRule>
    {
        val app = entityManager.createQuery(
            """SELECT r FROM NotificationRule r
               WHERE r.isActive = true
                 AND r.scope = :scope
                 AND r.eventPattern = :ev
               ORDER BY r.priority ASC""",
            NotificationRule::class.java,
        )
            .setParameter("scope", NotificationRuleScope.APP)
            .setParameter("ev", eventType)
            .resultList

        val org = if (organizationId == null) emptyList()
        else entityManager.createQuery(
            """SELECT r FROM NotificationRule r
               WHERE r.isActive = true
                 AND r.scope = :scope
                 AND r.organizationId = :oid
                 AND r.eventPattern = :ev
               ORDER BY r.priority ASC""",
            NotificationRule::class.java,
        )
            .setParameter("scope", NotificationRuleScope.ORG)
            .setParameter("oid", organizationId)
            .setParameter("ev", eventType)
            .resultList

        return (org + app)
    }
}

