package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

/** Persistence for [InformationRequestGroupOccurrence]. Occurrences belong to one request. */
@ApplicationScoped
class InformationRequestGroupOccurrenceRepository :
    BaseRepository<InformationRequestGroupOccurrence>(InformationRequestGroupOccurrence::class.java)
{
    fun findForRequest(informationRequestId: UUID): List<InformationRequestGroupOccurrence> =
        entityManager.createQuery(
            """
            SELECT occurrence
            FROM InformationRequestGroupOccurrence occurrence
            WHERE occurrence.informationRequestId = :requestId
              AND occurrence.removedAt IS NULL
            ORDER BY occurrence.occurrenceIndex
            """.trimIndent(),
            InformationRequestGroupOccurrence::class.java,
        )
            .setParameter("requestId", informationRequestId)
            .resultList

    fun findForGroup(informationRequestId: UUID, sourceTemplateGroupId: UUID): List<InformationRequestGroupOccurrence> =
        entityManager.createQuery(
            """
            SELECT occurrence
            FROM InformationRequestGroupOccurrence occurrence
            WHERE occurrence.informationRequestId = :requestId
              AND occurrence.sourceTemplateGroupId = :groupId
              AND occurrence.removedAt IS NULL
            ORDER BY occurrence.occurrenceIndex
            """.trimIndent(),
            InformationRequestGroupOccurrence::class.java,
        )
            .setParameter("requestId", informationRequestId)
            .setParameter("groupId", sourceTemplateGroupId)
            .resultList

    fun findForGroupAndParent(
        informationRequestId: UUID,
        sourceTemplateGroupId: UUID,
        parentOccurrenceId: UUID?,
    ): List<InformationRequestGroupOccurrence> =
        groupAndParentQuery(informationRequestId, sourceTemplateGroupId, parentOccurrenceId, includeRemoved = true)
            .resultList

    fun findForGroupAndParentForUpdate(
        informationRequestId: UUID,
        sourceTemplateGroupId: UUID,
        parentOccurrenceId: UUID?,
    ): List<InformationRequestGroupOccurrence> =
        groupAndParentQuery(informationRequestId, sourceTemplateGroupId, parentOccurrenceId, includeRemoved = true)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList

    fun findActiveForGroupAndParentForUpdate(
        informationRequestId: UUID,
        sourceTemplateGroupId: UUID,
        parentOccurrenceId: UUID?,
    ): List<InformationRequestGroupOccurrence> =
        groupAndParentQuery(informationRequestId, sourceTemplateGroupId, parentOccurrenceId, includeRemoved = false)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList

    fun findActiveByIdForUpdate(
        informationRequestId: UUID,
        occurrenceId: UUID,
    ): InformationRequestGroupOccurrence? =
        entityManager.createQuery(
            """
            SELECT occurrence
            FROM InformationRequestGroupOccurrence occurrence
            WHERE occurrence.informationRequestId = :requestId
              AND occurrence.id = :occurrenceId
              AND occurrence.removedAt IS NULL
            """.trimIndent(),
            InformationRequestGroupOccurrence::class.java,
        )
            .setParameter("requestId", informationRequestId)
            .setParameter("occurrenceId", occurrenceId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()

    fun findActiveForRequestForUpdate(informationRequestId: UUID): List<InformationRequestGroupOccurrence> =
        entityManager.createQuery(
            """
            SELECT occurrence
            FROM InformationRequestGroupOccurrence occurrence
            WHERE occurrence.informationRequestId = :requestId
              AND occurrence.removedAt IS NULL
            ORDER BY occurrence.occurrencePath
            """.trimIndent(),
            InformationRequestGroupOccurrence::class.java,
        )
            .setParameter("requestId", informationRequestId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList

    private fun groupAndParentQuery(
        informationRequestId: UUID,
        sourceTemplateGroupId: UUID,
        parentOccurrenceId: UUID?,
        includeRemoved: Boolean,
    ) = entityManager.createQuery(
        """
        SELECT occurrence
        FROM InformationRequestGroupOccurrence occurrence
        WHERE occurrence.informationRequestId = :requestId
          AND occurrence.sourceTemplateGroupId = :groupId
          AND (
              (:parentId IS NULL AND occurrence.parentOccurrenceId IS NULL) OR
              occurrence.parentOccurrenceId = :parentId
          )
          ${if (includeRemoved) "" else "AND occurrence.removedAt IS NULL"}
        ORDER BY occurrence.occurrenceIndex
        """.trimIndent(),
        InformationRequestGroupOccurrence::class.java,
    )
        .setParameter("requestId", informationRequestId)
        .setParameter("groupId", sourceTemplateGroupId)
        .setParameter("parentId", parentOccurrenceId)
}
