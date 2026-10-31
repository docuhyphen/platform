package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Timestamp
import java.util.*

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

    fun findCurrentForRequirement(requirementId: UUID): InformationRequestRequirementRevision? =
        entityManager.createQuery(
            """
            SELECT revision
            FROM InformationRequestRequirementRevision revision
            WHERE revision.informationRequestRequirementId = :requirementId
              AND EXISTS (
                  SELECT currentRevision
                  FROM InformationRequestRequirementCurrent currentRevision
                  WHERE currentRevision.currentRevisionId = revision.id
              )
            """.trimIndent(),
            InformationRequestRequirementRevision::class.java,
        )
            .setParameter("requirementId", requirementId)
            .resultList
            .firstOrNull()

    fun closeOpen(requirementIds: Collection<UUID>, closedAt: Timestamp): Int {
        if (requirementIds.isEmpty()) return 0
        return entityManager.createQuery(
            """
            UPDATE InformationRequestRequirementRevision revision
            SET revision.effectiveTo = CASE
                WHEN revision.effectiveFrom > :closedAt THEN revision.effectiveFrom
                ELSE :closedAt
            END
            WHERE revision.informationRequestRequirementId IN :requirementIds
              AND revision.effectiveTo IS NULL
            """.trimIndent(),
        )
            .setParameter("closedAt", closedAt)
            .setParameter("requirementIds", requirementIds)
            .executeUpdate()
    }

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
