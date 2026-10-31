package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingEvidenceLink
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateBindingEvidenceLinkRepository :
    BaseRepository<InformationRequestTemplateBindingEvidenceLink>(
        InformationRequestTemplateBindingEvidenceLink::class.java,
    )
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateBindingEvidenceLink> =
        entityManager.createQuery(
            """
            SELECT link
            FROM InformationRequestTemplateBindingEvidenceLink link
            WHERE link.templateVersionId = :versionId
            ORDER BY link.templateBindingId, link.supportingTemplateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingEvidenceLink::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateBindingEvidenceLink link
            WHERE link.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
