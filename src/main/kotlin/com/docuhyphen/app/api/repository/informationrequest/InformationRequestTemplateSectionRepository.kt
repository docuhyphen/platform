package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateSection
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateSection]. Sections belong to one version, and the
 * order they are read in is the order that version states.
 */
@ApplicationScoped
class InformationRequestTemplateSectionRepository :
    BaseRepository<InformationRequestTemplateSection>(InformationRequestTemplateSection::class.java)
{
    fun findOrdered(templateVersionId: UUID): List<InformationRequestTemplateSection> =
        entityManager.createQuery(
            """
            SELECT section
            FROM InformationRequestTemplateSection section
            WHERE section.templateVersionId = :versionId
            ORDER BY section.displayOrder
            """.trimIndent(),
            InformationRequestTemplateSection::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun findByKey(templateVersionId: UUID, sectionKey: String): InformationRequestTemplateSection? =
        entityManager.createQuery(
            """
            SELECT section
            FROM InformationRequestTemplateSection section
            WHERE section.templateVersionId = :versionId
              AND section.sectionKey = :sectionKey
            """.trimIndent(),
            InformationRequestTemplateSection::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .setParameter("sectionKey", sectionKey)
            .resultList
            .firstOrNull()

    /**
     * Clears this part of one draft version's configuration so the whole document can be rewritten.
     * The stored freeze rule refuses the same statement against a version that is no longer a
     * draft, so a frozen version cannot lose configuration this way.
     */
    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateSection section
            WHERE section.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
