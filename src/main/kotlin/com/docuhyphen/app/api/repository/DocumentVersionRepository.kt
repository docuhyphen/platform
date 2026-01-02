package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.DocumentVersion
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class DocumentVersionRepository : BaseRepository<DocumentVersion>(DocumentVersion::class.java)
{

    fun findByDocumentId(documentId: UUID): List<DocumentVersion>
    {
        return entityManager
            .createQuery(
                """
                SELECT v FROM DocumentVersion v
                WHERE v.document.id = :documentId
                ORDER BY v.createdDate DESC
                """.trimIndent(),
                DocumentVersion::class.java
            )
            .setParameter("documentId", documentId)
            .resultList
    }

    fun findLatestByDocumentId(documentId: UUID): DocumentVersion?
    {
        val results = entityManager
            .createQuery(
                """
                SELECT v FROM DocumentVersion v
                WHERE v.document.id = :documentId
                ORDER BY v.createdDate DESC
                """.trimIndent(),
                DocumentVersion::class.java
            )
            .setParameter("documentId", documentId)
            .setMaxResults(1)
            .resultList

        return if (results.isEmpty()) null else results[0]
    }
}