package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.LockModeType
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateVersion]. Every lookup states the definition it is
 * asking about, so a version number, which is only unique within one definition, cannot resolve
 * against a Template that belongs to somebody else.
 */
@ApplicationScoped
class InformationRequestTemplateVersionRepository :
    BaseRepository<InformationRequestTemplateVersion>(InformationRequestTemplateVersion::class.java)
{
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

    /**
     * Every version of several definitions at once, so listing Templates costs one read rather than
     * one per Template. Ordered by definition and then by version number, so a caller can group the
     * result without sorting it again.
     */
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

    /**
     * The one editable version of this definition, or null when every version has frozen. There is
     * at most one: a second open draft would leave two answers to what the next published version
     * is going to be.
     */
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

    /** Published versions oldest first. A retired version is deliberately not one of them. */
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
}
