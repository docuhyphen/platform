package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuthAuditEvent
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.TypedQuery
import java.util.UUID

@RequestScoped
class AuthAuditEventRepository : BaseRepository<AuthAuditEvent>(AuthAuditEvent::class.java)
{
    fun findLatestEventHash(): String?
    {
        return entityManager.createQuery(
            "SELECT a.eventHash FROM AuthAuditEvent a ORDER BY a.createdDate DESC",
            String::class.java,
        )
            .setMaxResults(1)
            .resultList
            .firstOrNull()
    }

    fun findRecent(
        limit: Int,
        action: String? = null,
        outcome: String? = null,
        organizationId: UUID? = null,
    ): List<AuthAuditEvent>
    {
        val filters = mutableListOf<String>()
        if (!action.isNullOrBlank()) filters.add("a.action = :action")
        if (!outcome.isNullOrBlank()) filters.add("a.outcome = :outcome")
        if (organizationId != null) filters.add("a.organizationId = :organizationId")

        val whereClause = if (filters.isEmpty()) "" else " WHERE ${filters.joinToString(" AND ")}"
        val queryString = "SELECT a FROM AuthAuditEvent a$whereClause ORDER BY a.createdDate DESC"

        val query: TypedQuery<AuthAuditEvent> = entityManager.createQuery(queryString, AuthAuditEvent::class.java)

        if (!action.isNullOrBlank()) query.setParameter("action", action)
        if (!outcome.isNullOrBlank()) query.setParameter("outcome", outcome)
        if (organizationId != null) query.setParameter("organizationId", organizationId)

        return query.setMaxResults(limit.coerceIn(1, 100)).resultList
    }

    /**
     * `AuthAuditEvent` rows with no corresponding `audit_ledger_event` (matched by
     * `audit_ledger_event.event_id = auth_audit_event.id`, the same identity
     * [com.docuhyphen.app.api.service.auth.AuthAuditService.recordOnRecorder]'s ongoing dual
     * write already uses). Only rows created before that dual write went live ever match this -
     * see [com.docuhyphen.app.api.service.audit.archive.LegacyAuditImportService].
     */
    fun findLegacyUnimported(limit: Int): List<AuthAuditEvent>
    {
        return entityManager.createQuery(
            """SELECT a FROM AuthAuditEvent a
               WHERE NOT EXISTS (
                   SELECT 1 FROM AuditLedgerEvent l WHERE l.eventId = a.id
               )
               ORDER BY a.createdDate ASC""",
            AuthAuditEvent::class.java,
        )
            .setMaxResults(limit.coerceIn(1, 1000))
            .resultList
    }
}


