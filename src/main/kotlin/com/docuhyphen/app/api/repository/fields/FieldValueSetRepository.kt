package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

/**
 * Persistence for the [FieldValueSet] rows that group typed answers under one
 * [com.docuhyphen.app.api.model.entity.SchemaAssignment].
 *
 * Every lookup comes in two forms. The plain form reads the set as it stands and is what a
 * projection uses. The for-update form takes a row-level write lock that the caller's transaction
 * holds to its end, and is what a save uses: two saves that arrive together then run one after the
 * other, so the second one decides against the state the first committed rather than against the
 * state they both started from.
 */
@ApplicationScoped
class FieldValueSetRepository :
    BaseRepository<FieldValueSet>(FieldValueSet::class.java)
{
    /** The single set holding the answers an assignment gives as itself, or null if it has none. */
    fun findRoot(schemaAssignmentId: UUID): FieldValueSet? =
        findRoot(schemaAssignmentId, LockModeType.NONE)

    /** The same set, locked against competing writers for the rest of the caller's transaction. */
    fun findRootForUpdate(schemaAssignmentId: UUID): FieldValueSet? =
        findRoot(schemaAssignmentId, LockModeType.PESSIMISTIC_WRITE)

    /** The set holding one repetition's answers, or null when the assignment has no such repetition. */
    fun findOccurrence(schemaAssignmentId: UUID, occurrencePath: String): FieldValueSet? =
        findOccurrence(schemaAssignmentId, occurrencePath, LockModeType.NONE)

    /** The same repetition, locked against competing writers for the rest of the caller's transaction. */
    fun findOccurrenceForUpdate(schemaAssignmentId: UUID, occurrencePath: String): FieldValueSet? =
        findOccurrence(schemaAssignmentId, occurrencePath, LockModeType.PESSIMISTIC_WRITE)

    /** Every set of an assignment, root and repetitions alike. */
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
