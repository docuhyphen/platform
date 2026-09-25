package com.docuhyphen.app.api.repository.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateAttestationPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateAttestationRole
import com.docuhyphen.app.api.repository.BaseRepository
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class InformationRequestTemplateAttestationPolicyRepository :
    BaseRepository<InformationRequestTemplateAttestationPolicy>(InformationRequestTemplateAttestationPolicy::class.java)
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateAttestationPolicy> =
        entityManager.createQuery(
            """
            SELECT policy
            FROM InformationRequestTemplateAttestationPolicy policy
            WHERE policy.templateVersionId = :versionId
            ORDER BY policy.templateBindingId
            """.trimIndent(),
            InformationRequestTemplateAttestationPolicy::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun findForBinding(templateBindingId: UUID): InformationRequestTemplateAttestationPolicy? =
        entityManager.createQuery(
            """
            SELECT policy
            FROM InformationRequestTemplateAttestationPolicy policy
            WHERE policy.templateBindingId = :bindingId
            """.trimIndent(),
            InformationRequestTemplateAttestationPolicy::class.java,
        )
            .setParameter("bindingId", templateBindingId)
            .resultList
            .firstOrNull()

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM InformationRequestTemplateAttestationPolicy policy WHERE policy.templateVersionId = :versionId",
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}

@ApplicationScoped
class InformationRequestTemplateAttestationRoleRepository :
    BaseRepository<InformationRequestTemplateAttestationRole>(InformationRequestTemplateAttestationRole::class.java)
{
    fun findForVersion(templateVersionId: UUID): List<InformationRequestTemplateAttestationRole> =
        entityManager.createQuery(
            """
            SELECT role
            FROM InformationRequestTemplateAttestationRole role
            WHERE role.templateVersionId = :versionId
            ORDER BY role.attestationPolicyId, role.position
            """.trimIndent(),
            InformationRequestTemplateAttestationRole::class.java,
        )
            .setParameter("versionId", templateVersionId)
            .resultList

    fun findForPolicy(attestationPolicyId: UUID): List<InformationRequestTemplateAttestationRole> =
        entityManager.createQuery(
            """
            SELECT role
            FROM InformationRequestTemplateAttestationRole role
            WHERE role.attestationPolicyId = :policyId
            ORDER BY role.position
            """.trimIndent(),
            InformationRequestTemplateAttestationRole::class.java,
        )
            .setParameter("policyId", attestationPolicyId)
            .resultList

    fun deleteForVersion(templateVersionId: UUID): Int =
        entityManager.createQuery(
            "DELETE FROM InformationRequestTemplateAttestationRole role WHERE role.templateVersionId = :versionId",
        )
            .setParameter("versionId", templateVersionId)
            .executeUpdate()
}
