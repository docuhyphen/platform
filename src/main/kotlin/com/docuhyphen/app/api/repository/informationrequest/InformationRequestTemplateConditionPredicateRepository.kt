package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicate
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateConditionPredicate]. Predicates belong to one rule and,
 * through it, to one version.
 */
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

    /**
     * Clears this part of one draft version's configuration so the whole document can be rewritten.
     * The stored freeze rule refuses the same statement against a version that is no longer a
     * draft, so a frozen version cannot lose configuration this way.
     */
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
