package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestRequirementRevisionRepository :
    BaseRepository<InformationRequestRequirementRevision>(InformationRequestRequirementRevision::class.java)
{
    fun findForRequirement(requirementId: UUID): List<InformationRequestRequirementRevision> =
        entityManager.createQuery(
            """
            SELECT revision
            FROM InformationRequestRequirementRevision revision
            WHERE revision.informationRequestRequirementId = :requirementId
            ORDER BY revision.revisionNumber
            """.trimIndent(),
            InformationRequestRequirementRevision::class.java,
        )
            .setParameter("requirementId", requirementId)
            .resultList

    fun findCurrentForRequest(requestId: UUID): List<InformationRequestRequirementRevision> =
        entityManager.createQuery(
            """
            SELECT revision
            FROM InformationRequestRequirementRevision revision
            WHERE revision.informationRequestId = :requestId
              AND EXISTS (
                  SELECT currentRevision
                  FROM InformationRequestRequirementCurrent currentRevision
                  WHERE currentRevision.currentRevisionId = revision.id
              )
            ORDER BY revision.occurrencePath, revision.revisionNumber
            """.trimIndent(),
            InformationRequestRequirementRevision::class.java,
        )
            .setParameter("requestId", requestId)
            .resultList
}
