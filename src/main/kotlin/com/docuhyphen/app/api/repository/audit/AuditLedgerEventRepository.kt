package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.model.entity.AuditLedgerEvent
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException
import java.sql.Timestamp
import java.util.*

@RequestScoped
class AuditLedgerEventRepository : BaseRepository<AuditLedgerEvent>(AuditLedgerEvent::class.java)
{
    fun findByEventIds(eventIds: Collection<UUID>): List<AuditLedgerEvent>
    {
        if (eventIds.isEmpty()) return emptyList()
        return entityManager.createQuery(
            "SELECT e FROM AuditLedgerEvent e WHERE e.eventId IN :eventIds",
            AuditLedgerEvent::class.java
        )
            .setParameter("eventIds", eventIds)
            .resultList
    }

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

    fun insert(event: AuditLedgerEvent): AuditLedgerEvent = save(event)

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
    ): List<AuditLedgerEvent> = searchScoped(
        organizationId, platformOnly, null, categories, targetTypes, targetIds, actorId,
        occurredAfter, occurredBefore, cursorOccurredAt, cursorEventId, limit,
    )

    fun searchPersonal(
        ownerUserId: UUID,
        categories: Set<String> = emptySet(),
        targetTypes: Set<String> = emptySet(),
        targetIds: Set<String> = emptySet(),
        actorId: UUID? = null,
        occurredAfter: Timestamp? = null,
        occurredBefore: Timestamp? = null,
        cursorOccurredAt: Timestamp? = null,
        cursorEventId: UUID? = null,
        limit: Int = 50,
    ): List<AuditLedgerEvent> = searchScoped(
        null, false, ownerUserId, categories, targetTypes, targetIds, actorId,
        occurredAfter, occurredBefore, cursorOccurredAt, cursorEventId, limit,
    )

    private fun searchScoped(
        organizationId: UUID?,
        platformOnly: Boolean,
        ownerUserId: UUID?,
        categories: Set<String>,
        targetTypes: Set<String>,
        targetIds: Set<String>,
        actorId: UUID?,
        occurredAfter: Timestamp?,
        occurredBefore: Timestamp?,
        cursorOccurredAt: Timestamp?,
        cursorEventId: UUID?,
        limit: Int,
    ): List<AuditLedgerEvent>
    {
        val where = mutableListOf<String>()
        if (ownerUserId != null)
        {
            where += "l.ownerType = 'USER' AND l.ownerId = :ownerUserId"
        }
        else if (platformOnly)
        {
            where += "l.ownerType = 'PLATFORM'"
        }
        else if (organizationId != null)
        {
            where += "l.ownerType = 'ORGANIZATION' AND l.ownerId = :organizationId"
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
            where += "l.occurredAt < :occurredBefore"
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
        if (ownerUserId != null)
        {
            query.setParameter("ownerUserId", ownerUserId)
        }
        else if (!platformOnly && organizationId != null)
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

    fun findDistinctStreamIdsForExport(
        organizationId: UUID?,
        platformOnly: Boolean,
        occurredAfter: Timestamp,
        occurredBefore: Timestamp,
    ): List<String>
    {
        val where = mutableListOf("l.occurredAt >= :occurredAfter", "l.occurredAt < :occurredBefore")
        if (platformOnly)
        {
            where += "l.ownerType = 'PLATFORM'"
        }
        else
        {
            where += "l.ownerType = 'ORGANIZATION' AND l.ownerId = :organizationId"
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

    fun findDistinctStreamIdsForPersonalExport(
        ownerUserId: UUID,
        occurredAfter: Timestamp,
        occurredBefore: Timestamp,
    ): List<String> = entityManager.createQuery(
        """SELECT DISTINCT l.streamId FROM AuditLedgerEvent l
           WHERE l.ownerType = 'USER' AND l.ownerId = :ownerUserId
             AND l.occurredAt >= :occurredAfter AND l.occurredAt < :occurredBefore""",
        String::class.java,
    )
        .setParameter("ownerUserId", ownerUserId)
        .setParameter("occurredAfter", occurredAfter)
        .setParameter("occurredBefore", occurredBefore)
        .resultList

    fun findDistinctStreamIdsByOrganization(organizationId: UUID?, platformOnly: Boolean): List<String>
    {
        val jpql = if (platformOnly)
        {
            "SELECT DISTINCT l.streamId FROM AuditLedgerEvent l WHERE l.ownerType = 'PLATFORM'"
        }
        else
        {
            "SELECT DISTINCT l.streamId FROM AuditLedgerEvent l WHERE l.ownerType = 'ORGANIZATION' AND l.ownerId = :organizationId"
        }
        val query = entityManager.createQuery(jpql, String::class.java)
        if (!platformOnly)
        {
            query.setParameter("organizationId", organizationId)
        }
        return query.resultList
    }

    fun findForExport(
        organizationId: UUID?,
        platformOnly: Boolean,
        categories: Set<String>,
        occurredAfter: Timestamp,
        occurredBefore: Timestamp,
    ): List<AuditLedgerEvent>
    {
        val where = mutableListOf("l.occurredAt >= :occurredAfter", "l.occurredAt < :occurredBefore")
        if (platformOnly)
        {
            where += "l.ownerType = 'PLATFORM'"
        }
        else
        {
            where += "l.ownerType = 'ORGANIZATION' AND l.ownerId = :organizationId"
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

    fun findForPersonalExport(
        ownerUserId: UUID,
        categories: Set<String>,
        occurredAfter: Timestamp,
        occurredBefore: Timestamp,
    ): List<AuditLedgerEvent>
    {
        val categoryClause = if (categories.isEmpty()) "" else " AND l.category IN :categories"
        val query = entityManager.createQuery(
            """SELECT l FROM AuditLedgerEvent l
               WHERE l.ownerType = 'USER' AND l.ownerId = :ownerUserId
                 AND l.occurredAt >= :occurredAfter AND l.occurredAt < :occurredBefore$categoryClause
               ORDER BY l.streamId ASC, l.streamSequence ASC""",
            AuditLedgerEvent::class.java,
        )
            .setParameter("ownerUserId", ownerUserId)
            .setParameter("occurredAfter", occurredAfter)
            .setParameter("occurredBefore", occurredBefore)
        if (categories.isNotEmpty()) query.setParameter("categories", categories)
        return query.resultList
    }

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
            "SELECT l FROM AuditLedgerEvent l WHERE l.ownerType = 'PLATFORM'"
        }
        else
        {
            "SELECT l FROM AuditLedgerEvent l WHERE l.ownerType = 'ORGANIZATION' AND l.ownerId = :organizationId"
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
        return entityManager.createQuery(
            "SELECT COUNT(l) FROM AuditLedgerEvent l",
            Long::class.javaObjectType
        ).singleResult
    }

    fun countByOrganization(organizationId: UUID?, platformOnly: Boolean): Long
    {
        val jpql = if (platformOnly)
        {
            "SELECT COUNT(l) FROM AuditLedgerEvent l WHERE l.ownerType = 'PLATFORM'"
        }
        else
        {
            "SELECT COUNT(l) FROM AuditLedgerEvent l WHERE l.ownerType = 'ORGANIZATION' AND l.ownerId = :organizationId"
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
        ownerUserId: UUID? = null,
    ): AuditLedgerEvent?
    {
        val where = mutableListOf("l.eventId = :eventId")
        if (ownerUserId != null)
        {
            where += "l.ownerType = 'USER' AND l.ownerId = :ownerUserId"
        }
        else if (platformOnly)
        {
            where += "l.ownerType = 'PLATFORM'"
        }
        else if (organizationId != null)
        {
            where += "l.ownerType = 'ORGANIZATION' AND l.ownerId = :organizationId"
        }

        return try
        {
            val query = entityManager.createQuery(
                "SELECT l FROM AuditLedgerEvent l WHERE ${where.joinToString(" AND ")}",
                AuditLedgerEvent::class.java,
            )
                .setParameter("eventId", eventId)
            if (ownerUserId != null)
            {
                query.setParameter("ownerUserId", ownerUserId)
            }
            else if (!platformOnly && organizationId != null)
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
