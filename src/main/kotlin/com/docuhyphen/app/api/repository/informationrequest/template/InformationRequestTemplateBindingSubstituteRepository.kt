package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateBindingSubstituteRepository :
    BaseRepository<InformationRequestTemplateBindingSubstitute>(
        InformationRequestTemplateBindingSubstitute::class.java,
    )
{
    fun findForBinding(templateBindingId: UUID): List<InformationRequestTemplateBindingSubstitute> =
        entityManager.createQuery(
            """
            SELECT alternative
            FROM InformationRequestTemplateBindingSubstitute alternative
            WHERE alternative.templateBindingId = :bindingId
            ORDER BY alternative.substituteTemplateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingSubstitute::class.java,
        )
            .setParameter("bindingId", templateBindingId)
            .resultList

    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateBindingSubstitute> =
        entityManager.createQuery(
            """
            SELECT alternative
            FROM InformationRequestTemplateBindingSubstitute alternative
            WHERE alternative.templateVersionId = :versionId
            ORDER BY alternative.templateBindingId, alternative.substituteTemplateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingSubstitute::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateBindingSubstitute substitute
            WHERE substitute.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
