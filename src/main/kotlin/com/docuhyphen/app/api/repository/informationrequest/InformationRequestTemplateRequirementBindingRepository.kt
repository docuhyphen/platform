package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateRequirementBinding]. Bindings are read for one version
 * at a time, in the order that version places them.
 */
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

    fun findByRequirement(
        templateVersionId: UUID,
        templateRequirementId: UUID,
    ): InformationRequestTemplateRequirementBinding? =
        entityManager.createQuery(
            """
            SELECT binding
            FROM InformationRequestTemplateRequirementBinding binding
            WHERE binding.templateVersionId = :versionId
              AND binding.templateRequirementId = :requirementId
            """.trimIndent(),
            InformationRequestTemplateRequirementBinding::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .setParameter("requirementId", templateRequirementId)
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
            DELETE FROM InformationRequestTemplateRequirementBinding binding
            WHERE binding.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
