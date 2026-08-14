package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.repository.OrganizationIdentityDomainRepository
import com.docuhyphen.app.api.repository.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.organization.OrganizationSeatGuard
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

class IdentityProviderNotAllowedException(message: String) : RuntimeException(message)

@RequestScoped
class OrganizationIdentityPolicyService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val organizationSeatGuard: OrganizationSeatGuard,
    private val organizationIdentityProviderConfigRepository: OrganizationIdentityProviderConfigRepository,
    private val organizationIdentityDomainRepository: OrganizationIdentityDomainRepository,
)
{
    fun resolveOrganizationForEmail(email: String): Organization?
    {
        val domain = email.substringAfter('@', "").trim().lowercase()
        if (domain.isBlank())
        {
            return null
        }

        return organizationIdentityDomainRepository.findVerifiedOrganizationByDomain(domain)
    }

    fun resolveOrganizationsForEmail(email: String): List<Organization>
    {
        val domain = email.substringAfter('@', "").trim().lowercase()
        if (domain.isBlank())
        {
            return emptyList()
        }

        return organizationIdentityDomainRepository.findAllVerifiedOrganizationsByDomain(domain)
    }

    fun enforceUserCapForEmail(email: String)
    {
        val organization = resolveOrganizationForEmail(email) ?: return
        enforceUserCapForOrganization(organization)
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
            throw IdentityProviderNotAllowedException("Provider is not enabled for this organization")
        }
    }

    fun resolveTrustedOrganizationForOAuth(
        email: String,
        provider: IdentityProviderType,
        orgIdpConfigId: UUID?,
    ): Organization?
    {
        val organization = resolveOrganizationForEmail(email) ?: return null
        val activeConfigs = organizationIdentityProviderConfigRepository.findActiveByOrganizationId(organization.id)
        if (activeConfigs.isEmpty())
        {
            return null
        }

        val matchingConfig = activeConfigs.firstOrNull {
            it.provider.equals(provider.name, ignoreCase = true)
        } ?: throw IdentityProviderNotAllowedException("Provider is not enabled for this organization")

        if (orgIdpConfigId == null || matchingConfig.id != orgIdpConfigId)
        {
            throw IdentityProviderNotAllowedException("OAuth sign-in did not use this organization's IdP configuration")
        }

        return organization
    }

    fun enforceUserCapForOrganization(organization: Organization)
    {
        organizationSeatGuard.enforceAvailableSeat(organization.id)
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



