package com.docuhyphen.app.api.repository.fields

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

/** Persistence for [SchemaFieldBinding] rows composing a [com.docuhyphen.app.api.model.entity.SchemaVersion]. */
@ApplicationScoped
class SchemaFieldBindingRepository :
    BaseRepository<SchemaFieldBinding>(SchemaFieldBinding::class.java)
{
    /** All bindings for a version in display order. */
    fun findByVersion(schemaVersionId: UUID): List<SchemaFieldBinding> =
        entityManager.createQuery(
            """SELECT b FROM SchemaFieldBinding b
               WHERE b.schemaVersionId = :vid
               ORDER BY b.displayOrder, b.id""",
            SchemaFieldBinding::class.java,
        )
            .setParameter("vid", schemaVersionId)
            .resultList

    @Transactional
    fun deleteByVersion(schemaVersionId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM SchemaFieldBinding b WHERE b.schemaVersionId = :vid",
        )
            .setParameter("vid", schemaVersionId)
            .executeUpdate()
}
