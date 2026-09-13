package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateEvidencePolicy]. One requested Document is judged by
 * one policy, so the binding lookup answers with at most one row; the version lookup projects the
 * configuration a request was issued against.
 */
@ApplicationScoped
class InformationRequestTemplateEvidencePolicyRepository :
    BaseRepository<InformationRequestTemplateEvidencePolicy>(
        InformationRequestTemplateEvidencePolicy::class.java,
    )
{
    fun findForBinding(templateBindingId: UUID): InformationRequestTemplateEvidencePolicy? =
        entityManager.createQuery(
            """
            SELECT policy
            FROM InformationRequestTemplateEvidencePolicy policy
            WHERE policy.templateBindingId = :bindingId
            """.trimIndent(),
            InformationRequestTemplateEvidencePolicy::class.java,
        )
            .setParameter("bindingId", templateBindingId)
            .resultList
            .firstOrNull()

    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateEvidencePolicy> =
        entityManager.createQuery(
            """
            SELECT policy
            FROM InformationRequestTemplateEvidencePolicy policy
            WHERE policy.templateVersionId = :versionId
            ORDER BY policy.templateBindingId
            """.trimIndent(),
            InformationRequestTemplateEvidencePolicy::class.java,
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
            DELETE FROM InformationRequestTemplateEvidencePolicy policy
            WHERE policy.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
