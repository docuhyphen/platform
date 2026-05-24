package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.repository.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.repository.OrganizationSubscriptionPolicyRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

@RequestScoped
class OrganizationIdentityPolicyService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val organizationSubscriptionPolicyRepository: OrganizationSubscriptionPolicyRepository,
    private val organizationIdentityProviderConfigRepository: OrganizationIdentityProviderConfigRepository,
)
{
    fun resolveOrganizationForEmail(email: String): Organization?
    {
        val domain = email.substringAfter('@', "").trim().lowercase()
        if (domain.isBlank())
        {
            return null
        }

        return organizationRepository.findByVerifiedContactEmailDomain(domain)
    }

    fun resolveOrganizationsForEmail(email: String): List<Organization>
    {
        val domain = email.substringAfter('@', "").trim().lowercase()
        if (domain.isBlank())
        {
            return emptyList()
        }

        return organizationRepository.findAllByVerifiedContactEmailDomain(domain)
    }

    fun enforceUserCapForEmail(email: String)
    {
        val organization = resolveOrganizationForEmail(email) ?: return
        val policy = organizationSubscriptionPolicyRepository.findByOrganizationId(organization.id) ?: return
        val maxUsers = policy.maxUsers ?: return

        val activeUsers = organization.appUsers.count { it.isActive }
        if (activeUsers >= maxUsers)
        {
            throw IllegalArgumentException("Organization user limit reached")
        }
    }

    fun assertProviderAllowedForEmail(email: String, provider: IdentityProviderType)
    {
        val organization = resolveOrganizationForEmail(email) ?: return

        val hasAnyActiveProvider = organizationIdentityProviderConfigRepository
            .findActiveByOrganizationId(organization.id)
            .isNotEmpty()

        if (!hasAnyActiveProvider)
        {
            return
        }

        val activeConfig = organizationIdentityProviderConfigRepository
            .findActiveByOrganizationIdAndProvider(organization.id, provider.name)

        if (activeConfig == null)
        {
            throw IllegalArgumentException("Provider is not enabled for this organization")
        }
    }

    fun findActiveProviderConfigIdForEmail(email: String, provider: IdentityProviderType): UUID?
    {
        val organization = resolveOrganizationForEmail(email) ?: return null
        return organizationIdentityProviderConfigRepository
            .findActiveByOrganizationIdAndProvider(organization.id, provider.name)
            ?.id
    }

    fun findActiveProviderConfigsForOrganization(organizationId: UUID): List<OrganizationIdentityProviderConfig>
    {
        return organizationIdentityProviderConfigRepository.findActiveByOrganizationId(organizationId)
    }

    fun findOrganizationById(organizationId: UUID): Organization?
    {
        return organizationRepository.findById(organizationId)
    }
}



