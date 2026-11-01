package com.docuhyphen.app.api.repository.audit

import com.docuhyphen.app.api.model.entity.AuditArchiveSegment
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.persistence.NoResultException

@RequestScoped
class AuditArchiveSegmentRepository : BaseRepository<AuditArchiveSegment>(AuditArchiveSegment::class.java)
{
    fun findLatestByStream(streamId: String): AuditArchiveSegment?
    {
        return try
        {
            entityManager.createQuery(
                "SELECT s FROM AuditArchiveSegment s WHERE s.streamId = :streamId ORDER BY s.lastSequence DESC",
                AuditArchiveSegment::class.java,
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

    fun findByStreamOrderBySequence(streamId: String): List<AuditArchiveSegment>
    {
        return entityManager.createQuery(
            "SELECT s FROM AuditArchiveSegment s WHERE s.streamId = :streamId ORDER BY s.firstSequence ASC",
            AuditArchiveSegment::class.java,
        )
            .setParameter("streamId", streamId)
            .resultList
    }

    fun findDistinctStreamIds(): List<String>
    {
        return entityManager.createQuery(
            "SELECT DISTINCT s.streamId FROM AuditArchiveSegment s",
            String::class.java,
        ).resultList
    }

    fun findDueForVerification(since: java.sql.Timestamp, limit: Int): List<AuditArchiveSegment>
    {
        return entityManager.createQuery(
            """SELECT s FROM AuditArchiveSegment s
               WHERE s.lastVerificationAt IS NULL OR s.lastVerificationAt < :since
               ORDER BY s.lastVerificationAt ASC NULLS FIRST, s.createdAt ASC""",
            AuditArchiveSegment::class.java,
        )
            .setParameter("since", since)
            .setMaxResults(limit)
            .resultList
    }

    fun insert(segment: AuditArchiveSegment): AuditArchiveSegment = save(segment)

    fun updateVerification(segment: AuditArchiveSegment): AuditArchiveSegment = update(segment)
}
