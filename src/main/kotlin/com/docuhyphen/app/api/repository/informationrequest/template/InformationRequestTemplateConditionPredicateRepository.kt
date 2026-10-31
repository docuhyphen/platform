package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicate
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateConditionPredicateRepository :
    BaseRepository<InformationRequestTemplateConditionPredicate>(
        InformationRequestTemplateConditionPredicate::class.java,
    )
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateConditionPredicate> =
        entityManager.createQuery(
            """
            SELECT predicate
            FROM InformationRequestTemplateConditionPredicate predicate
            WHERE predicate.templateVersionId = :versionId
            """.trimIndent(),
            InformationRequestTemplateConditionPredicate::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateConditionPredicate predicate
            WHERE predicate.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
