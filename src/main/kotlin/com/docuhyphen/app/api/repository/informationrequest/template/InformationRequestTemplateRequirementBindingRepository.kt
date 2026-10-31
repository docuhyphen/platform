package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateRequirementBindingRepository :
    BaseRepository<InformationRequestTemplateRequirementBinding>(
        InformationRequestTemplateRequirementBinding::class.java,
    )
{
    fun findOrdered(templateVersionId: UUID): List<InformationRequestTemplateRequirementBinding> =
        entityManager.createQuery(
            """
            SELECT binding
            FROM InformationRequestTemplateRequirementBinding binding
            WHERE binding.templateVersionId = :versionId
            ORDER BY binding.displayOrder
            """.trimIndent(),
            InformationRequestTemplateRequirementBinding::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateRequirementBinding binding
            WHERE binding.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
