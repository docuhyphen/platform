package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequestTemplateAttestationPolicy
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateAttestationRole
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestAttestationPolicy
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateAttestationPolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateAttestationRoleRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

@ApplicationScoped
class InformationRequestAttestationPolicyLoader @Inject constructor(
    private val policyRepository: InformationRequestTemplateAttestationPolicyRepository,
    private val roleRepository: InformationRequestTemplateAttestationRoleRepository,
)
{
    fun forBinding(bindingId: UUID): InformationRequestAttestationPolicy?
    {
        val policy = policyRepository.findForBinding(bindingId) ?: return null
        return toModel(policy, roleRepository.findForPolicy(policy.id))
    }

    fun forVersion(templateVersionId: UUID): Map<UUID, InformationRequestAttestationPolicy>
    {
        val rolesByPolicy = roleRepository.findForVersion(templateVersionId).groupBy { it.attestationPolicyId }
        return policyRepository.findForVersion(templateVersionId).associate { policy ->
            policy.templateBindingId to toModel(policy, rolesByPolicy[policy.id].orEmpty())
        }
    }

    fun statementHash(binding: InformationRequestTemplateRequirementBinding): String =
        sha256Hex(listOf(binding.prompt, binding.helpText.orEmpty()).joinToString("\n"))

    private fun toModel(
        policy: InformationRequestTemplateAttestationPolicy,
        roles: List<InformationRequestTemplateAttestationRole>,
    ): InformationRequestAttestationPolicy
    {
        val ordered = roles.sortedBy { it.position }.map { it.roleKey }
        return InformationRequestAttestationPolicy(
            bindingId = policy.templateBindingId,
            requiredRoles = ordered,
            ordering = policy.ordering,
            minimumAssentCount = policy.minimumAssentCount,
            minimumAuthenticationStrength = policy.minimumAuthenticationStrength,
            validityHours = policy.validityHours,
            externalSignatureReference = policy.externalSignatureReference,
            policyHash = sha256Hex(
                listOf(
                    "binding:${policy.templateBindingId}",
                    "roles:${ordered.joinToString(",")}",
                    "ordering:${policy.ordering}",
                    "assents:${policy.minimumAssentCount}",
                    "strength:${policy.minimumAuthenticationStrength}",
                    "validity:${policy.validityHours ?: ""}",
                    "reference:${policy.externalSignatureReference}",
                ).joinToString("\n"),
            ),
        )
    }

    private fun sha256Hex(material: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
