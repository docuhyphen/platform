package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.RequestExecutionGrant
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class RequestExecutionGrantRepository :
    BaseRepository<RequestExecutionGrant>(RequestExecutionGrant::class.java)
{
    fun findByRequestId(requestId: UUID): RequestExecutionGrant? =
        entityManager.createQuery(
            """
            SELECT grant_
            FROM RequestExecutionGrant grant_
            WHERE grant_.requestId = :requestId
            """.trimIndent(),
            RequestExecutionGrant::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
            .firstOrNull()
}
