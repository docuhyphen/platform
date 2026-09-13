package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateRequirementGroup]. Groups belong to one version, like
 * sections and bindings.
 */
@ApplicationScoped
class InformationRequestTemplateRequirementGroupRepository :
    BaseRepository<InformationRequestTemplateRequirementGroup>(
        InformationRequestTemplateRequirementGroup::class.java,
    )
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateRequirementGroup> =
        entityManager.createQuery(
            """
            SELECT requirementGroup
            FROM InformationRequestTemplateRequirementGroup requirementGroup
            WHERE requirementGroup.templateVersionId = :versionId
            """.trimIndent(),
            InformationRequestTemplateRequirementGroup::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    /**
     * Clears this part of one draft version's configuration so the whole document can be rewritten.
     * The stored freeze rule refuses the same statement against a version that is no longer a
     * draft, so a frozen version cannot lose configuration this way.
     */
    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateRequirementGroup requirementGroup
            WHERE requirementGroup.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
