package com.docuhyphen.app.api.repository.informationrequest.lifecycle

import com.docuhyphen.app.api.model.entity.InformationRequestTransition
import com.docuhyphen.app.api.model.informationrequest.lifecycle.InformationRequestMutation
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.util.*

@ApplicationScoped
class InformationRequestTransitionRepository :
    BaseRepository<InformationRequestTransition>(InformationRequestTransition::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestTransition> =
        entityManager.createQuery(
            """
            SELECT transition
            FROM InformationRequestTransition transition
            WHERE transition.informationRequestId = :requestId
            ORDER BY transition.sequenceNumber
            """.trimIndent(),
            InformationRequestTransition::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun latestOccurrence(requestId: UUID, mutation: InformationRequestMutation): Timestamp? =
        entityManager.createQuery(
            """
            SELECT MAX(transition.occurredAt)
            FROM InformationRequestTransition transition
            WHERE transition.informationRequestId = :requestId
              AND transition.mutation = :mutation
            """.trimIndent(),
            Timestamp::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("mutation", mutation)
            .singleResult

    fun nextSequenceNumber(requestId: UUID): Int =
        entityManager.createQuery(
            """
            SELECT COALESCE(MAX(transition.sequenceNumber), 0) + 1
            FROM InformationRequestTransition transition
            WHERE transition.informationRequestId = :requestId
            """.trimIndent(),
            java.lang.Integer::class.java,
        )
            .setParameter("requestId", requestId)
            .singleResult
            .toInt()
}
