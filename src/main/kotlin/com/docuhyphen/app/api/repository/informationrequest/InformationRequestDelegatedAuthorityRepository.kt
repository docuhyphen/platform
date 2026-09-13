package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestDelegatedAuthority
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestDelegatedAuthorityRepository :
    BaseRepository<InformationRequestDelegatedAuthority>(InformationRequestDelegatedAuthority::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestDelegatedAuthority> =
        entityManager.createQuery(
            """
            SELECT authority
            FROM InformationRequestDelegatedAuthority authority
            WHERE authority.informationRequestId = :requestId
            ORDER BY authority.recordedAt, authority.id
            """.trimIndent(),
            InformationRequestDelegatedAuthority::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
