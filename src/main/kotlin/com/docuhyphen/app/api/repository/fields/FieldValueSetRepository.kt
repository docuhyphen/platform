package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.*

@ApplicationScoped
class FieldValueSetRepository :
    BaseRepository<FieldValueSet>(FieldValueSet::class.java)
{
    fun findRoot(schemaAssignmentId: UUID): FieldValueSet? =
        findRoot(schemaAssignmentId, LockModeType.NONE)

    fun findRootForUpdate(schemaAssignmentId: UUID): FieldValueSet? =
        findRoot(schemaAssignmentId, LockModeType.PESSIMISTIC_WRITE)

    fun findOccurrence(schemaAssignmentId: UUID, occurrencePath: String): FieldValueSet? =
        findOccurrence(schemaAssignmentId, occurrencePath, LockModeType.NONE)

    fun findOccurrenceForUpdate(schemaAssignmentId: UUID, occurrencePath: String): FieldValueSet? =
        findOccurrence(schemaAssignmentId, occurrencePath, LockModeType.PESSIMISTIC_WRITE)

    fun findByAssignment(schemaAssignmentId: UUID): List<FieldValueSet> =
        entityManager.createQuery(
            "SELECT s FROM FieldValueSet s WHERE s.schemaAssignmentId = :aid",
            FieldValueSet::class.java,
        )
            .setParameter("aid", schemaAssignmentId)
            .resultList

    private fun findRoot(schemaAssignmentId: UUID, lockMode: LockModeType): FieldValueSet? =
        entityManager.createQuery(
            """SELECT s FROM FieldValueSet s
               WHERE s.schemaAssignmentId = :aid AND s.setKind = :kind""",
            FieldValueSet::class.java,
        )
            .setParameter("aid", schemaAssignmentId)
            .setParameter("kind", FieldValueSetKind.ROOT)
            .setLockMode(lockMode)
            .resultList
            .firstOrNull()

    private fun findOccurrence(
        schemaAssignmentId: UUID,
        occurrencePath: String,
        lockMode: LockModeType,
    ): FieldValueSet? =
        entityManager.createQuery(
            """SELECT s FROM FieldValueSet s
               WHERE s.schemaAssignmentId = :aid AND s.setKind = :kind AND s.occurrencePath = :path""",
            FieldValueSet::class.java,
        )
            .setParameter("aid", schemaAssignmentId)
            .setParameter("kind", FieldValueSetKind.OCCURRENCE)
            .setParameter("path", occurrencePath)
            .setLockMode(lockMode)
            .resultList
            .firstOrNull()
}
