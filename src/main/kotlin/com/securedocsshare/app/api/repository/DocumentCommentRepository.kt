package com.securedocsshare.app.api.repository

import com.securedocsshare.app.api.model.DocumentComment
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class DocumentCommentRepository: BaseRepository<DocumentComment>(DocumentComment::class.java)
{
    fun findByDocumentId(documentId: UUID): List<DocumentComment>
    {
        return entityManager.createQuery(
            "SELECT c FROM DocumentComment c WHERE c.document.id = :documentId", DocumentComment::class.java
        ).setParameter("documentId", documentId).resultList
    }
}