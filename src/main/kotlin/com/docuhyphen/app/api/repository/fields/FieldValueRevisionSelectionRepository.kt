package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldValueRevisionSelection
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class FieldValueRevisionSelectionRepository :
    BaseRepository<FieldValueRevisionSelection>(FieldValueRevisionSelection::class.java)
{
    fun findByRevision(fieldValueRevisionId: UUID): List<FieldValueRevisionSelection> =
        entityManager.createQuery(
            """SELECT s FROM FieldValueRevisionSelection s
               WHERE s.fieldValueRevisionId = :rid
               ORDER BY s.displayOrder, s.id""",
            FieldValueRevisionSelection::class.java,
        )
            .setParameter("rid", fieldValueRevisionId)
            .resultList
}
