package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

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
