package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.FieldValueSelection
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

/** Persistence for [FieldValueSelection] option codes belonging to a select-typed [com.docuhyphen.app.api.model.entity.FieldValue]. */
@ApplicationScoped
class FieldValueSelectionRepository :
    BaseRepository<FieldValueSelection>(FieldValueSelection::class.java)
{
    fun findByValue(fieldValueId: UUID): List<FieldValueSelection> =
        entityManager.createQuery(
            """SELECT s FROM FieldValueSelection s
               WHERE s.fieldValueId = :vid
               ORDER BY s.displayOrder, s.id""",
            FieldValueSelection::class.java,
        )
            .setParameter("vid", fieldValueId)
            .resultList

    @Transactional
    fun deleteByValue(fieldValueId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM FieldValueSelection s WHERE s.fieldValueId = :vid",
        )
            .setParameter("vid", fieldValueId)
            .executeUpdate()
}
