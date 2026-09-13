package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestEvidenceAttribute
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateEvidenceAcceptedValue
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Persistence for [InformationRequestTemplateEvidenceAcceptedValue]. Accepted values are read for a
 * whole policy when rendering one requirement, for one attribute when checking a single stated
 * value, and for a whole version when projecting the configuration.
 */
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

    fun findForPolicyAttribute(
        evidencePolicyId: UUID,
        attribute: InformationRequestEvidenceAttribute,
    ): List<InformationRequestTemplateEvidenceAcceptedValue> =
        entityManager.createQuery(
            """
            SELECT accepted
            FROM InformationRequestTemplateEvidenceAcceptedValue accepted
            WHERE accepted.evidencePolicyId = :policyId
              AND accepted.attribute = :attribute
            ORDER BY accepted.acceptedValue
            """.trimIndent(),
            InformationRequestTemplateEvidenceAcceptedValue::class.java,
        )
            .setParameter("policyId", evidencePolicyId)
            .setParameter("attribute", attribute)
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

    /**
     * Clears this part of one draft version's configuration so the whole document can be rewritten.
     * The stored freeze rule refuses the same statement against a version that is no longer a
     * draft, so a frozen version cannot lose configuration this way.
     */
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
