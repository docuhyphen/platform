package com.docuhyphen.app.api.repository.informationrequest.template

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.*

@ApplicationScoped
class InformationRequestTemplateEvidenceAcceptedValueRepository :
    BaseRepository<InformationRequestTemplateEvidenceAcceptedValue>(
        InformationRequestTemplateEvidenceAcceptedValue::class.java,
    )
{
    fun findForPolicy(evidencePolicyId: UUID): List<InformationRequestTemplateEvidenceAcceptedValue> =
        entityManager.createQuery(
            """
            SELECT accepted
            FROM InformationRequestTemplateEvidenceAcceptedValue accepted
            WHERE accepted.evidencePolicyId = :policyId
            ORDER BY accepted.attribute, accepted.acceptedValue
            """.trimIndent(),
            InformationRequestTemplateEvidenceAcceptedValue::class.java,
        )
            .setParameter("policyId", evidencePolicyId)
            .resultList

    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateEvidenceAcceptedValue> =
        entityManager.createQuery(
            """
            SELECT accepted
            FROM InformationRequestTemplateEvidenceAcceptedValue accepted
            WHERE accepted.templateVersionId = :versionId
            ORDER BY accepted.evidencePolicyId, accepted.attribute, accepted.acceptedValue
            """.trimIndent(),
            InformationRequestTemplateEvidenceAcceptedValue::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            """
            DELETE FROM InformationRequestTemplateEvidenceAcceptedValue accepted
            WHERE accepted.templateVersionId = :versionId
            """.trimIndent(),
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
