package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingSubstitute
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateBindingSubstitute]. Alternatives are read from the
 * replaced end when rendering one requirement, from the substituting end when a requested Document
 * needs to report what it can stand in for, and for a whole version when projecting the
 * configuration.
 */
@ApplicationScoped
class InformationRequestTemplateBindingSubstituteRepository :
    BaseRepository<InformationRequestTemplateBindingSubstitute>(
        InformationRequestTemplateBindingSubstitute::class.java,
    )
{
    fun findForBinding(templateBindingId: UUID): List<InformationRequestTemplateBindingSubstitute> =
        entityManager.createQuery(
            """
            SELECT alternative
            FROM InformationRequestTemplateBindingSubstitute alternative
            WHERE alternative.templateBindingId = :bindingId
            ORDER BY alternative.substituteTemplateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingSubstitute::class.java,
        )
            .setParameter("bindingId", templateBindingId)
            .resultList

    fun findSubstitutedBy(
        substituteTemplateBindingId: UUID,
    ): List<InformationRequestTemplateBindingSubstitute> =
        entityManager.createQuery(
            """
            SELECT alternative
            FROM InformationRequestTemplateBindingSubstitute alternative
            WHERE alternative.substituteTemplateBindingId = :substituteBindingId
            ORDER BY alternative.templateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingSubstitute::class.java,
        )
            .setParameter("substituteBindingId", substituteTemplateBindingId)
            .resultList

    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateBindingSubstitute> =
        entityManager.createQuery(
            """
            SELECT alternative
            FROM InformationRequestTemplateBindingSubstitute alternative
            WHERE alternative.templateVersionId = :versionId
            ORDER BY alternative.templateBindingId, alternative.substituteTemplateBindingId
            """.trimIndent(),
            InformationRequestTemplateBindingSubstitute::class.java,
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
            DELETE FROM InformationRequestTemplateBindingSubstitute substitute
            WHERE substitute.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
