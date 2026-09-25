package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.dto.InformationRequestTemplateRequirementRequest
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateAttestationPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateAttestationRole
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateAttestationPolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateAttestationRoleRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.util.UUID

@ApplicationScoped
class InformationRequestTemplateAttestationPolicyWriter @Inject constructor(
    private val policyRepository: InformationRequestTemplateAttestationPolicyRepository,
    private val roleRepository: InformationRequestTemplateAttestationRoleRepository,
)
{
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    fun clear(templateVersionId: UUID)
    {
        roleRepository.deleteForVersion(templateVersionId)
        policyRepository.deleteForVersion(templateVersionId)
    }

    fun write(
        templateVersionId: UUID,
        authored: List<InformationRequestTemplateRequirementRequest>,
        bindings: Map<String, InformationRequestTemplateRequirementBinding>,
    )
    {
        val written = authored.mapNotNull { requirement ->
            val stated = requirement.attestationPolicy ?: return@mapNotNull null
            val policy = policyRepository.save(
                InformationRequestTemplateAttestationPolicy().apply {
                    this.templateVersionId = templateVersionId
                    templateBindingId = bindings.getValue(requirement.requirementKey).id
                    ordering = stated.ordering
                    minimumAssentCount = requireNotNull(stated.minimumAssentCount) {
                        "A normalized attestation policy states its minimum assent count"
                    }
                    minimumAuthenticationStrength = stated.minimumAuthenticationStrength
                    validityHours = stated.validityHours
                    externalSignatureReference = stated.externalSignatureReference
                },
            )
            policy to stated.requiredRoles
        }
        if (written.isEmpty()) return

        // A role names its policy, so every policy reaches the database before its roles are stated.
        entityManager.flush()
        written.forEach { (policy, roles) ->
            roles.forEachIndexed { index, role ->
                roleRepository.save(
                    InformationRequestTemplateAttestationRole().apply {
                        this.templateVersionId = templateVersionId
                        attestationPolicyId = policy.id
                        roleKey = role
                        position = index + 1
                    },
                )
            }
        }
    }
}
