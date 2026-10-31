package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateBindingDispositionRepository :
    BaseRepository<InformationRequestTemplateBindingDisposition>(
        InformationRequestTemplateBindingDisposition::class.java,
    )
{
    fun findForBinding(templateBindingId: UUID): List<InformationRequestTemplateBindingDisposition> =
        entityManager.createQuery(
            """
            SELECT permitted
            FROM InformationRequestTemplateBindingDisposition permitted
            WHERE permitted.templateBindingId = :bindingId
            ORDER BY permitted.disposition
            """.trimIndent(),
            InformationRequestTemplateBindingDisposition::class.java,
        )
            .setParameter("bindingId", templateBindingId)
            .resultList

    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateBindingDisposition> =
        entityManager.createQuery(
            """
            SELECT permitted
            FROM InformationRequestTemplateBindingDisposition permitted
            WHERE permitted.templateVersionId = :versionId
            ORDER BY permitted.templateBindingId, permitted.disposition
            """.trimIndent(),
            InformationRequestTemplateBindingDisposition::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateBindingDisposition permitted
            WHERE permitted.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
