package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class FieldValueRepository :
    BaseRepository<FieldValue>(FieldValue::class.java)
{
    fun findByAssignment(schemaAssignmentId: UUID): List<FieldValue> =
        entityManager.createQuery(
            "SELECT v FROM FieldValue v WHERE v.schemaAssignmentId = :aid",
            FieldValue::class.java,
        )
            .setParameter("aid", schemaAssignmentId)
            .resultList

    fun findByValueSet(fieldValueSetId: UUID): List<FieldValue> =
        entityManager.createQuery(
            "SELECT v FROM FieldValue v WHERE v.fieldValueSetId = :sid",
            FieldValue::class.java,
        )
            .setParameter("sid", fieldValueSetId)
            .resultList

    fun findByValueSetAndContract(fieldValueSetId: UUID, fieldContractId: UUID): FieldValue? =
        entityManager.createQuery(
            """SELECT v FROM FieldValue v
               WHERE v.fieldValueSetId = :sid AND v.fieldContractId = :cid""",
            FieldValue::class.java,
        )
            .setParameter("sid", fieldValueSetId)
            .setParameter("cid", fieldContractId)
            .resultList
            .firstOrNull()
}
