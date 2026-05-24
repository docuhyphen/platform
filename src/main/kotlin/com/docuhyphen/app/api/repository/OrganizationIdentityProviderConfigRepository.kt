package com.docuhyphen.app.api.repository

import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import jakarta.enterprise.context.RequestScoped
import java.util.UUID

@RequestScoped
class OrganizationIdentityProviderConfigRepository : BaseRepository<OrganizationIdentityProviderConfig>(OrganizationIdentityProviderConfig::class.java)
{
    fun findByOrganizationId(organizationId: UUID): List<OrganizationIdentityProviderConfig>
    {
        return entityManager.createQuery(
            "SELECT c FROM OrganizationIdentityProviderConfig c WHERE c.organization.id = :organizationId ORDER BY c.createdDate DESC",
            OrganizationIdentityProviderConfig::class.java,
        )
            .setParameter("organizationId", organizationId)
            .resultList
    }

    fun findActiveByOrganizationId(organizationId: UUID): List<OrganizationIdentityProviderConfig>
    {
        return entityManager.createQuery(
            "SELECT c FROM OrganizationIdentityProviderConfig c WHERE c.organization.id = :organizationId AND c.isActive = true",
            OrganizationIdentityProviderConfig::class.java,
        )
            .setParameter("organizationId", organizationId)
            .resultList
    }

    fun findActiveByOrganizationIdAndProvider(organizationId: UUID, provider: String): OrganizationIdentityProviderConfig?
    {
        return entityManager.createQuery(
            "SELECT c FROM OrganizationIdentityProviderConfig c WHERE c.organization.id = :organizationId AND c.isActive = true AND UPPER(c.provider) = :provider",
            OrganizationIdentityProviderConfig::class.java,
        )
            .setParameter("organizationId", organizationId)
            .setParameter("provider", provider.uppercase())
            .resultList
            .firstOrNull()
    }
}



