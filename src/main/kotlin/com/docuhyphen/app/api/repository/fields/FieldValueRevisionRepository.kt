package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for the append-only [FieldValueRevision] history of a
 * [com.docuhyphen.app.api.model.entity.FieldValue]. Rows are only ever inserted and read; the
 * database refuses an update to one.
 */
@ApplicationScoped
class FieldValueRevisionRepository :
    BaseRepository<FieldValueRevision>(FieldValueRevision::class.java)
{
    /** The most recently recorded revision of one question within one Value Set, or null. */
    fun findLatest(fieldValueSetId: UUID, fieldContractId: UUID): FieldValueRevision? =
        entityManager.createQuery(
            """SELECT r FROM FieldValueRevision r
               WHERE r.fieldValueSetId = :sid AND r.fieldContractId = :cid
               ORDER BY r.revisionNumber DESC""",
            FieldValueRevision::class.java,
        )
            .setParameter("sid", fieldValueSetId)
            .setParameter("cid", fieldContractId)
            .setMaxResults(1)
            .resultList
            .firstOrNull()

    /** Every recorded revision of one question within one Value Set, oldest first. */
    fun findHistory(fieldValueSetId: UUID, fieldContractId: UUID): List<FieldValueRevision> =
        entityManager.createQuery(
            """SELECT r FROM FieldValueRevision r
               WHERE r.fieldValueSetId = :sid AND r.fieldContractId = :cid
               ORDER BY r.revisionNumber""",
            FieldValueRevision::class.java,
        )
            .setParameter("sid", fieldValueSetId)
            .setParameter("cid", fieldContractId)
            .resultList

    /** Every recorded revision under one Schema Assignment, oldest first. */
    fun findByAssignment(schemaAssignmentId: UUID): List<FieldValueRevision> =
        entityManager.createQuery(
            """SELECT r FROM FieldValueRevision r
               WHERE r.schemaAssignmentId = :aid
               ORDER BY r.recordedAt, r.revisionNumber""",
            FieldValueRevision::class.java,
        )
            .setParameter("aid", schemaAssignmentId)
            .resultList
}
