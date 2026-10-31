package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicateLiteral
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateConditionPredicateLiteralRepository :
    BaseRepository<InformationRequestTemplateConditionPredicateLiteral>(
        InformationRequestTemplateConditionPredicateLiteral::class.java,
    )
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateConditionPredicateLiteral> =
        entityManager.createQuery(
            """
            SELECT literal
            FROM InformationRequestTemplateConditionPredicateLiteral literal
            WHERE literal.templateVersionId = :versionId
            """.trimIndent(),
            InformationRequestTemplateConditionPredicateLiteral::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateConditionPredicateLiteral literal
            WHERE literal.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
