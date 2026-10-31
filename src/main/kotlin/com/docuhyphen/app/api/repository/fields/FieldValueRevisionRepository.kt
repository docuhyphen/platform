package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class FieldValueRevisionRepository :
    BaseRepository<FieldValueRevision>(FieldValueRevision::class.java)
{
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
}
