package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.*

@ApplicationScoped
class InformationRequestTemplateVersionRepository :
    BaseRepository<InformationRequestTemplateVersion>(InformationRequestTemplateVersion::class.java)
{
    fun flushChanges() = entityManager.flush()

    fun findByNumber(definitionId: UUID, versionNumber: Int): InformationRequestTemplateVersion? =
        entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestTemplateVersion version
            WHERE version.templateDefinitionId = :definitionId
              AND version.versionNumber = :versionNumber
            """.trimIndent(),
            InformationRequestTemplateVersion::class.java,
        )
            .setParameter("definitionId", definitionId)
            .setParameter("versionNumber", versionNumber)
            .resultList
            .firstOrNull()

    fun findForDefinitions(definitionIds: List<UUID>): List<InformationRequestTemplateVersion>
    {
        if (definitionIds.isEmpty())
        {
            return emptyList()
        }
        return entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestTemplateVersion version
            WHERE version.templateDefinitionId IN :definitionIds
            ORDER BY version.templateDefinitionId, version.versionNumber
            """.trimIndent(),
            InformationRequestTemplateVersion::class.java,
        )
            .setParameter("definitionIds", definitionIds)
            .resultList
    }

    fun findDraft(definitionId: UUID): InformationRequestTemplateVersion? =
        entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestTemplateVersion version
            WHERE version.templateDefinitionId = :definitionId
              AND version.status =
                  com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus.DRAFT
            ORDER BY version.versionNumber
            """.trimIndent(),
            InformationRequestTemplateVersion::class.java,
        )
            .setParameter("definitionId", definitionId)
            .resultList
            .firstOrNull()

    fun findDraftForUpdate(definitionId: UUID): InformationRequestTemplateVersion? =
        entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestTemplateVersion version
            WHERE version.templateDefinitionId = :definitionId
              AND version.status =
                  com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus.DRAFT
            ORDER BY version.versionNumber
            """.trimIndent(),
            InformationRequestTemplateVersion::class.java,
        )
            .setParameter("definitionId", definitionId)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList
            .firstOrNull()

    fun findPublished(definitionId: UUID): List<InformationRequestTemplateVersion> =
        entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestTemplateVersion version
            WHERE version.templateDefinitionId = :definitionId
              AND version.status =
                  com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus.PUBLISHED
            ORDER BY version.versionNumber
            """.trimIndent(),
            InformationRequestTemplateVersion::class.java,
        )
            .setParameter("definitionId", definitionId)
            .resultList

    fun findLatestPublished(definitionId: UUID): InformationRequestTemplateVersion? =
        findPublished(definitionId).lastOrNull()

    fun findForIds(ids: Collection<UUID>): List<InformationRequestTemplateVersion>
    {
        if (ids.isEmpty()) return emptyList()
        return entityManager.createQuery(
            """
            SELECT version
            FROM InformationRequestTemplateVersion version
            WHERE version.id IN :ids
            """.trimIndent(),
            InformationRequestTemplateVersion::class.java,
        )
            .setParameter("ids", ids)
            .resultList
    }
}
