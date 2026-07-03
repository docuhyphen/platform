package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.FieldValue
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** Persistence for typed [FieldValue] rows against a [com.docuhyphen.app.api.model.entity.SchemaAssignment]. */
@ApplicationScoped
class FieldValueRepository :
    BaseRepository<FieldValue>(FieldValue::class.java)
{
    /** All values for one assignment. */
    fun findByAssignment(schemaAssignmentId: UUID): List<FieldValue> =
        entityManager.createQuery(
            "SELECT v FROM FieldValue v WHERE v.schemaAssignmentId = :aid",
            FieldValue::class.java,
        )
            .setParameter("aid", schemaAssignmentId)
            .resultList

    /** All values for a resource (across its single primary assignment). */
    fun findByResource(resourceType: String, resourceId: UUID): List<FieldValue> =
        entityManager.createQuery(
            """SELECT v FROM FieldValue v
               WHERE v.resourceType = :rt AND v.resourceId = :rid""",
            FieldValue::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .resultList

    fun findByAssignmentAndContract(schemaAssignmentId: UUID, fieldContractId: UUID): FieldValue? =
        entityManager.createQuery(
            """SELECT v FROM FieldValue v
               WHERE v.schemaAssignmentId = :aid AND v.fieldContractId = :cid""",
            FieldValue::class.java,
        )
            .setParameter("aid", schemaAssignmentId)
            .setParameter("cid", fieldContractId)
            .resultList
            .firstOrNull()
}
