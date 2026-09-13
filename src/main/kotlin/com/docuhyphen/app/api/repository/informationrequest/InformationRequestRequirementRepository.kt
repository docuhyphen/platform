package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestRequirementRepository :
    BaseRepository<InformationRequestRequirement>(InformationRequestRequirement::class.java)
{
    fun findForRequest(requestId: UUID): List<InformationRequestRequirement> =
        entityManager.createQuery(
            """
            SELECT requirement
            FROM InformationRequestRequirement requirement
            WHERE requirement.informationRequestId = :requestId
            ORDER BY requirement.occurrencePath, requirement.id
            """.trimIndent(),
            InformationRequestRequirement::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList

    fun findOccurrence(
        requestId: UUID,
        templateBindingId: UUID,
        occurrencePath: String,
    ): InformationRequestRequirement? =
        entityManager.createQuery(
            """
            SELECT requirement
            FROM InformationRequestRequirement requirement
            WHERE requirement.informationRequestId = :requestId
              AND requirement.sourceTemplateBindingId = :templateBindingId
              AND requirement.occurrencePath = :occurrencePath
            """.trimIndent(),
            InformationRequestRequirement::class.java,
        )
            .setParameter("requestId", requestId)
            .setParameter("templateBindingId", templateBindingId)
            .setParameter("occurrencePath", occurrencePath)
            .resultList
            .firstOrNull()
}
