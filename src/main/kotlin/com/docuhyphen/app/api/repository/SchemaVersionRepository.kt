package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.SchemaVersion
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/** Persistence for immutable [SchemaVersion] contracts of a [SchemaDefinition]. */
@ApplicationScoped
class SchemaVersionRepository :
    BaseRepository<SchemaVersion>(SchemaVersion::class.java)
{
    /** All versions for a definition, newest first. */
    fun findByDefinition(schemaDefinitionId: UUID): List<SchemaVersion> =
        entityManager.createQuery(
            """SELECT v FROM SchemaVersion v
               WHERE v.schemaDefinitionId = :did
               ORDER BY v.versionNumber DESC""",
            SchemaVersion::class.java,
        )
            .setParameter("did", schemaDefinitionId)
            .resultList

    /** The single DRAFT version for a definition, or null. */
    fun findDraft(schemaDefinitionId: UUID): SchemaVersion? =
        entityManager.createQuery(
            """SELECT v FROM SchemaVersion v
               WHERE v.schemaDefinitionId = :did
                 AND v.status = com.docuhyphen.app.api.model.entity.FieldLifecycleStatus.DRAFT
               ORDER BY v.versionNumber DESC""",
            SchemaVersion::class.java,
        )
            .setParameter("did", schemaDefinitionId)
            .resultList
            .firstOrNull()

    /** The latest PUBLISHED version for a definition, or null. */
    fun findLatestPublished(schemaDefinitionId: UUID): SchemaVersion? =
        entityManager.createQuery(
            """SELECT v FROM SchemaVersion v
               WHERE v.schemaDefinitionId = :did
                 AND v.status = com.docuhyphen.app.api.model.entity.FieldLifecycleStatus.PUBLISHED
               ORDER BY v.versionNumber DESC""",
            SchemaVersion::class.java,
        )
            .setParameter("did", schemaDefinitionId)
            .resultList
            .firstOrNull()

    fun findMaxVersion(schemaDefinitionId: UUID): Int =
        entityManager.createQuery(
            """SELECT COALESCE(MAX(v.versionNumber), 0) FROM SchemaVersion v
               WHERE v.schemaDefinitionId = :did""",
            Integer::class.java,
        )
            .setParameter("did", schemaDefinitionId)
            .singleResult
            .toInt()
}
