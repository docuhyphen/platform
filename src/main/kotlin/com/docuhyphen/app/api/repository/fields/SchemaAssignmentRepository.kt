package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.SchemaAssignment
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** Persistence for [SchemaAssignment] rows pinning a resource to a schema version. */
@ApplicationScoped
class SchemaAssignmentRepository :
    BaseRepository<SchemaAssignment>(SchemaAssignment::class.java)
{
    /** The single primary assignment for a resource, or null. */
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

    /** Count of assignments pinned to a given schema version (usage impact). */
    fun countByVersion(schemaVersionId: UUID): Long =
        entityManager.createQuery(
            "SELECT COUNT(a) FROM SchemaAssignment a WHERE a.schemaVersionId = :vid",
            java.lang.Long::class.java,
        )
            .setParameter("vid", schemaVersionId)
            .singleResult
            .toLong()
}
