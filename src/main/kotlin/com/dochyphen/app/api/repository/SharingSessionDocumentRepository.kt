package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.Document
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class SharingSessionDocumentRepository : BaseRepository<Document>(Document::class.java)
{
    fun findByDocumentId(documentId: UUID): Document?
    {
        return entityManager.createQuery(
            "SELECT d FROM Document d WHERE d.id = :documentId", Document::class.java
        ).setParameter("documentId", documentId).singleResult
    }
}