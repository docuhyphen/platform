package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException
import java.sql.Timestamp
import java.util.UUID

@RequestScoped
class AuditLedgerEventRepository : BaseRepository<AuditLedgerEvent>(AuditLedgerEvent::class.java)
{
    /**
     * [LedgerProcessor] uses this before appending so re-draining an already-ledgered
     * `audit_outbox` row is a no-op rather than a duplicate ledger row. The `event_id` unique
     * constraint on `audit_ledger_event` is the authoritative belt-and-braces guard underneath.
     */
    fun existsByEventId(eventId: UUID): Boolean
    {
        val count = entityManager.createQuery(
            "SELECT COUNT(l) FROM AuditLedgerEvent l WHERE l.eventId = :eventId",
            Long::class.javaObjectType,
        )
            .setParameter("eventId", eventId)
            .singleResult
        return count > 0
    }

    fun findLatestByStream(streamId: String): AuditLedgerEvent?
    {
        return try
        {
            entityManager.createQuery(
                "SELECT l FROM AuditLedgerEvent l WHERE l.streamId = :streamId ORDER BY l.streamSequence DESC",
                AuditLedgerEvent::class.java,
            )
                .setParameter("streamId", streamId)
                .setMaxResults(1)
                .singleResult
        }
        catch (e: NoResultException)
        {
            null
        }
    }

    fun findByStreamOrderBySequence(streamId: String): List<AuditLedgerEvent>
    {
        return entityManager.createQuery(
            "SELECT l FROM AuditLedgerEvent l WHERE l.streamId = :streamId ORDER BY l.streamSequence ASC",
            AuditLedgerEvent::class.java,
        )
            .setParameter("streamId", streamId)
            .resultList
    }

    /**
     * Retrieves every ledger event whose [targetType]
     * matches and whose [targetId] is one of [targetIds], newest first. Used to back the Exchange
     * "Audit" tab with a single request across all of an Exchange's documents instead of one
     * request per document.
     */
    fun findByTargetTypeAndTargetIds(targetType: String, targetIds: List<String>): List<AuditLedgerEvent>
    {
        if (targetIds.isEmpty())
        {
            return emptyList()
        }

        return entityManager.createQuery(
            """SELECT l FROM AuditLedgerEvent l
               WHERE l.targetType = :targetType AND l.targetId IN :targetIds
               ORDER BY l.occurredAt DESC""",
            AuditLedgerEvent::class.java,
        )
            .setParameter("targetType", targetType)
            .setParameter("targetIds", targetIds)
            .resultList
    }

    /** Inserts [event] in the caller's active transaction (no `REQUIRES_NEW`). */
    fun insert(event: AuditLedgerEvent): AuditLedgerEvent = save(event)

    /**
     * Every distinct stream that currently has at least one ledger event. Used by
     * [com.docuhyphen.app.api.service.audit.archive.AuditArchiver.closeReadySegments] to find
     * which streams have candidate events to close into a segment, without needing a separate
     * "streams" table.
     */
    fun findDistinctStreamIds(): List<String>
    {
        return entityManager.createQuery(
            "SELECT DISTINCT l.streamId FROM AuditLedgerEvent l",
            String::class.java,
        ).resultList
    }

    fun search(
        organizationId: UUID? = null,
        platformOnly: Boolean = false,
        categories: Set<String> = emptySet(),
        targetTypes: Set<String> = emptySet(),
        targetIds: Set<String> = emptySet(),
        actorId: UUID? = null,
        occurredAfter: Timestamp? = null,
        occurredBefore: Timestamp? = null,
        cursorOccurredAt: Timestamp? = null,
        cursorEventId: UUID? = null,
        limit: Int = 50,
    ): List<AuditLedgerEvent>
    {
        val where = mutableListOf<String>()
        if (platformOnly)
        {
            where += "l.organizationId IS NULL"
        }
        else if (organizationId != null)
        {
            where += "l.organizationId = :organizationId"
        }
        if (categories.isNotEmpty())
        {
            where += "l.category IN :categories"
        }
        if (targetTypes.isNotEmpty())
        {
            where += "l.targetType IN :targetTypes"
        }
        if (targetIds.isNotEmpty())
        {
            where += "l.targetId IN :targetIds"
        }
        if (actorId != null)
        {
            where += "l.actorId = :actorId"
        }
        if (occurredAfter != null)
        {
            where += "l.occurredAt >= :occurredAfter"
        }
        if (occurredBefore != null)
        {
            where += "l.occurredAt <= :occurredBefore"
        }
        if (cursorOccurredAt != null && cursorEventId != null)
        {
            where += "(l.occurredAt < :cursorOccurredAt OR (l.occurredAt = :cursorOccurredAt AND l.eventId < :cursorEventId))"
        }

        val jpql = buildString {
            append("SELECT l FROM AuditLedgerEvent l")
            if (where.isNotEmpty())
            {
                append(" WHERE ")
                append(where.joinToString(" AND "))
            }
            append(" ORDER BY l.occurredAt DESC, l.eventId DESC")
        }

        val query = entityManager.createQuery(jpql, AuditLedgerEvent::class.java)
            .setMaxResults(limit)
        if (!platformOnly && organizationId != null)
        {
            query.setParameter("organizationId", organizationId)
        }
        if (categories.isNotEmpty())
        {
            query.setParameter("categories", categories)
        }
        if (targetTypes.isNotEmpty())
        {
            query.setParameter("targetTypes", targetTypes)
        }
        if (targetIds.isNotEmpty())
        {
            query.setParameter("targetIds", targetIds)
        }
        if (actorId != null)
        {
            query.setParameter("actorId", actorId)
        }
        if (occurredAfter != null)
        {
            query.setParameter("occurredAfter", occurredAfter)
        }
        if (occurredBefore != null)
        {
            query.setParameter("occurredBefore", occurredBefore)
        }
        if (cursorOccurredAt != null && cursorEventId != null)
        {
            query.setParameter("cursorOccurredAt", cursorOccurredAt)
            query.setParameter("cursorEventId", cursorEventId)
        }
        return query.resultList
    }

    /**
     * Every distinct stream that has at least one event for [organizationId] (or, when
     * [platformOnly], every platform stream) within `[occurredAfter, occurredBefore]`. Used by
     * [com.docuhyphen.app.api.service.audit.export.AuditExportBuilder] to know exactly which
     * streams' archive segment chains must be verified before an export bundle is built.
     */
    fun findDistinctStreamIdsForExport(
        organizationId: UUID?,
        platformOnly: Boolean,
        occurredAfter: Timestamp,
        occurredBefore: Timestamp,
    ): List<String>
    {
        val where = mutableListOf("l.occurredAt >= :occurredAfter", "l.occurredAt <= :occurredBefore")
        if (platformOnly)
        {
            where += "l.organizationId IS NULL"
        }
        else
        {
            where += "l.organizationId = :organizationId"
        }

        val query = entityManager.createQuery(
            "SELECT DISTINCT l.streamId FROM AuditLedgerEvent l WHERE ${where.joinToString(" AND ")}",
            String::class.java,
        )
            .setParameter("occurredAfter", occurredAfter)
            .setParameter("occurredBefore", occurredBefore)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }

    /**
     * Every distinct stream currently carrying at least one event for [organizationId] (or every
     * platform stream when [platformOnly]), regardless of time range. Backs the
     * `GET /organizations/{organizationId}/audit-integrity` report
     * ([com.docuhyphen.app.api.service.audit.export.AuditIntegrityService]).
     */
    fun findDistinctStreamIdsByOrganization(organizationId: UUID?, platformOnly: Boolean): List<String>
    {
        val jpql = if (platformOnly)
        {
            "SELECT DISTINCT l.streamId FROM AuditLedgerEvent l WHERE l.organizationId IS NULL"
        }
        else
        {
            "SELECT DISTINCT l.streamId FROM AuditLedgerEvent l WHERE l.organizationId = :organizationId"
        }
        val query = entityManager.createQuery(jpql, String::class.java)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }

    /**
     * Full, unpaginated ledger event range for export bundle construction, ordered by
     * `(streamId, streamSequence)` so the same range always serializes identically regardless of
     * query plan. Callers must bound the range using `maxRangeDays`; this method
     * itself performs no pagination since an export bundle is materialized in one pass.
     */
    fun findForExport(
        organizationId: UUID?,
        platformOnly: Boolean,
        categories: Set<String>,
        occurredAfter: Timestamp,
        occurredBefore: Timestamp,
    ): List<AuditLedgerEvent>
    {
        val where = mutableListOf("l.occurredAt >= :occurredAfter", "l.occurredAt <= :occurredBefore")
        if (platformOnly)
        {
            where += "l.organizationId IS NULL"
        }
        else
        {
            where += "l.organizationId = :organizationId"
        }
        if (categories.isNotEmpty())
        {
            where += "l.category IN :categories"
        }

        val query = entityManager.createQuery(
            "SELECT l FROM AuditLedgerEvent l WHERE ${where.joinToString(" AND ")} ORDER BY l.streamId ASC, l.streamSequence ASC",
            AuditLedgerEvent::class.java,
        )
            .setParameter("occurredAfter", occurredAfter)
            .setParameter("occurredBefore", occurredBefore)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        if (categories.isNotEmpty())
        {
            query.setParameter("categories", categories)
        }
        return query.resultList
    }

    /**
     * Oldest-first batch of ledger events with no corresponding `audit_analytics_fact` row yet,
     * ordered by the append-order [AuditLedgerEvent.id]'s underlying `global_sequence` column so
     * [com.docuhyphen.app.api.service.audit.AuditAnalyticsProjector] can drain the ledger
     * incrementally instead of rescanning the whole table every pass.
     */
    @Suppress("UNCHECKED_CAST")
    fun findUnprojected(limit: Int): List<AuditLedgerEvent>
    {
        return entityManager.createNativeQuery(
            """SELECT l.* FROM audit_ledger_event l
               WHERE NOT EXISTS (SELECT 1 FROM audit_analytics_fact f WHERE f.ledger_event_id = l.event_id)
               ORDER BY l.global_sequence ASC
               LIMIT :limit""",
            AuditLedgerEvent::class.java,
        )
            .setParameter("limit", limit)
            .resultList as List<AuditLedgerEvent>
    }

    fun findAllByOrganization(organizationId: UUID?, platformOnly: Boolean): List<AuditLedgerEvent>
    {
        val jpql = if (platformOnly)
        {
            "SELECT l FROM AuditLedgerEvent l WHERE l.organizationId IS NULL"
        }
        else
        {
            "SELECT l FROM AuditLedgerEvent l WHERE l.organizationId = :organizationId"
        }
        val query = entityManager.createQuery(jpql, AuditLedgerEvent::class.java)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }

    fun countAll(): Long
    {
        return entityManager.createQuery("SELECT COUNT(l) FROM AuditLedgerEvent l", Long::class.javaObjectType).singleResult
    }

    fun countByOrganization(organizationId: UUID?, platformOnly: Boolean): Long
    {
        val jpql = if (platformOnly)
        {
            "SELECT COUNT(l) FROM AuditLedgerEvent l WHERE l.organizationId IS NULL"
        }
        else
        {
            "SELECT COUNT(l) FROM AuditLedgerEvent l WHERE l.organizationId = :organizationId"
        }
        val query = entityManager.createQuery(jpql, Long::class.javaObjectType)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.singleResult
    }

    fun findByEventIdScoped(
        eventId: UUID,
        organizationId: UUID? = null,
        platformOnly: Boolean = false,
    ): AuditLedgerEvent?
    {
        val where = mutableListOf("l.eventId = :eventId")
        if (platformOnly)
        {
            where += "l.organizationId IS NULL"
        }
        else if (organizationId != null)
        {
            where += "l.organizationId = :organizationId"
        }

        return try
        {
            val query = entityManager.createQuery(
                "SELECT l FROM AuditLedgerEvent l WHERE ${where.joinToString(" AND ")}",
                AuditLedgerEvent::class.java,
            )
                .setParameter("eventId", eventId)
            if (!platformOnly && organizationId != null)
            {
                query.setParameter("organizationId", organizationId)
            }
            query.singleResult
        }
        catch (e: NoResultException)
        {
            null
        }
    }
}
