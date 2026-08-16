package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.ExchangeDocumentComment
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class DocumentCommentRepository :
    BaseRepository<ExchangeDocumentComment>(ExchangeDocumentComment::class.java)
{
    fun findByDocumentId(documentId: UUID): List<ExchangeDocumentComment>
    {
        return entityManager
            .createQuery(
                """
                |SELECT c FROM ExchangeDocumentComment c
                |LEFT JOIN FETCH c.documentVersion
                |WHERE c.document.id = :documentId
                |ORDER BY c.createdDate DESC """.trimMargin(),
                ExchangeDocumentComment::class.java
            )
            .setParameter("documentId", documentId).resultList
    }
}
