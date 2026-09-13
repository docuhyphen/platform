package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.repository.BaseRepository
import com.docuhyphen.app.api.service.informationrequest.InformationRequestResponseStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

@ApplicationScoped
class InformationRequestResponseRepository :
    BaseRepository<InformationRequestResponse>(InformationRequestResponse::class.java),
    InformationRequestResponseStore
{
    override fun findCurrentForRequest(requestId: UUID): List<InformationRequestResponse> =
        entityManager.createQuery(
            """
            SELECT response
            FROM InformationRequestResponse response
            WHERE response.informationRequestId = :requestId
              AND response.activeInResponse = true
            ORDER BY response.occurrencePath, response.informationRequestRequirementId
            """.trimIndent(),
            InformationRequestResponse::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    override fun findAllForRequest(requestId: UUID): List<InformationRequestResponse> =
        entityManager.createQuery(
            """
            SELECT response
            FROM InformationRequestResponse response
            WHERE response.informationRequestId = :requestId
            ORDER BY response.occurrencePath, response.informationRequestRequirementId
            """.trimIndent(),
            InformationRequestResponse::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    override fun findCurrentForUpdate(
        requestId: UUID,
        requirementId: UUID,
    ): InformationRequestResponse? =
        entityManager.createQuery(
            """
            SELECT response
            FROM InformationRequestResponse response
            WHERE response.informationRequestId = :requestId
              AND response.informationRequestRequirementId = :requirementId
            """.trimIndent(),
            InformationRequestResponse::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("requirementId", requirementId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()
}
