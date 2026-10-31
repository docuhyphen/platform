package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateSection
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

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
