package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateConditionPredicateLiteral
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateConditionPredicateLiteral]. Literals belong to one
 * predicate and, through it, to one version.
 */
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

    /**
     * Clears this part of one draft version's configuration so the whole document can be rewritten.
     * The stored freeze rule refuses the same statement against a version that is no longer a
     * draft, so a frozen version cannot lose configuration this way.
     */
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
