package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidencePolicy
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

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
