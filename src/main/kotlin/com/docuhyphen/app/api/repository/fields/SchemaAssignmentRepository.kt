package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class SchemaAssignmentRepository :
    BaseRepository<SchemaAssignment>(SchemaAssignment::class.java)
{
    fun lockResource(resourceType: String, resourceId: UUID) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:resourceKey, 0))")
            .setParameter("resourceKey", "schema_assignment:$resourceType:$resourceId")
            .singleResult
    }

    fun findByResource(resourceType: String, resourceId: UUID): SchemaAssignment? =
        entityManager.createQuery(
            """SELECT a FROM SchemaAssignment a
               WHERE a.resourceType = :rt AND a.resourceId = :rid""",
            SchemaAssignment::class.java,
        )
            .setParameter("rt", resourceType)
            .setParameter("rid", resourceId)
            .resultList
            .firstOrNull()

    fun countByVersion(schemaVersionId: UUID): Long =
        entityManager.createQuery(
            "SELECT COUNT(a) FROM SchemaAssignment a WHERE a.schemaVersionId = :vid",
            java.lang.Long::class.java,
        )
            .setParameter("vid", schemaVersionId)
            .singleResult
            .toLong()
}
