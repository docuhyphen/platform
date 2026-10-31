package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionRule
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

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
