package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionRule
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateConditionRule]. Rules belong to one version, like
 * sections, bindings, and groups.
 */
@ApplicationScoped
class InformationRequestTemplateConditionRuleRepository :
    BaseRepository<InformationRequestTemplateConditionRule>(
        InformationRequestTemplateConditionRule::class.java,
    )
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateConditionRule> =
        entityManager.createQuery(
            """
            SELECT conditionRule
            FROM InformationRequestTemplateConditionRule conditionRule
            WHERE conditionRule.templateVersionId = :versionId
            """.trimIndent(),
            InformationRequestTemplateConditionRule::class.java,
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
            DELETE FROM InformationRequestTemplateConditionRule conditionRule
            WHERE conditionRule.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
