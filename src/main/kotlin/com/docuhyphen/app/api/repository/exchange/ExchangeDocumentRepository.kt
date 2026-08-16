package com.docuhyphen.app.api.repository.exchange

import com.docuhyphen.app.api.repository.BaseRepository

import com.docuhyphen.app.api.model.entity.Document
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class ExchangeDocumentRepository : BaseRepository<Document>(Document::class.java)
{
    fun findByDocumentId(documentId: UUID): Document?
    {
        return entityManager.createQuery(
            "SELECT d FROM Document d WHERE d.id = :documentId", Document::class.java
        ).setParameter("documentId", documentId).singleResult
    }
}
