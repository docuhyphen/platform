package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestDocumentPlaceholder
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestDocumentPlaceholderRepository :
    BaseRepository<InformationRequestDocumentPlaceholder>(InformationRequestDocumentPlaceholder::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestDocumentPlaceholder> =
        entityManager.createQuery(
            """
            SELECT placeholder
            FROM InformationRequestDocumentPlaceholder placeholder
            WHERE placeholder.informationRequestId = :requestId
            ORDER BY placeholder.displayOrder, placeholder.id
            """.trimIndent(),
            InformationRequestDocumentPlaceholder::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
