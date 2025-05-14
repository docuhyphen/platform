package com.dochyphen.app.api.repository

import com.dochyphen.app.api.model.entity.DocumentAuditLog
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class DocumentAuditRepository : BaseRepository<DocumentAuditLog>(DocumentAuditLog::class.java)
{
    fun findByDocumentId(uuid: UUID): List<DocumentAuditLog>
    {
        return entityManager.createQuery(
            "SELECT l FROM DocumentAuditLog l WHERE l.document.id = :documentId", DocumentAuditLog::class.java
        ).setParameter("documentId", uuid).resultList
    }
}