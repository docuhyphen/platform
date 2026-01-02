package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.SharingSessionDocumentComment
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class DocumentCommentRepository :
    BaseRepository<SharingSessionDocumentComment>(SharingSessionDocumentComment::class.java)
{
    fun findByDocumentId(documentId: UUID): List<SharingSessionDocumentComment>
    {
        return entityManager
            .createQuery(
                """
                |SELECT c FROM SharingSessionDocumentComment c
                |WHERE c.document.id = :documentId
                |ORDER BY c.createdDate DESC """.trimMargin(),
                SharingSessionDocumentComment::class.java
            )
            .setParameter("documentId", documentId).resultList
    }
}