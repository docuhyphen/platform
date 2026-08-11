package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.repository.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.organization.OrganizationSeatGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class OrganizationOAuthPolicyTest
{
    private val organizationRepository = mock<OrganizationRepository>()
    private val organizationSeatGuard = mock<OrganizationSeatGuard>()
    private val idpConfigRepository = mock<OrganizationIdentityProviderConfigRepository>()
    private val service = OrganizationIdentityPolicyService(
        organizationRepository,
        organizationSeatGuard,
        idpConfigRepository,
    )

    @Test
    fun `organization OAuth requires the exact active provider configuration`()
    {
        val organization = organization()
        val config = config(organization, IdentityProviderType.GOOGLE)
        whenever(organizationRepository.findByVerifiedContactEmailDomain("example.com")).thenReturn(organization)
        whenever(idpConfigRepository.findActiveByOrganizationId(organization.id)).thenReturn(listOf(config))

        assertThrows<IdentityProviderNotAllowedException> {
            service.resolveTrustedOrganizationForOAuth(
                "user@example.com",
                IdentityProviderType.GOOGLE,
                UUID.randomUUID(),
            )
        }
    }

    @Test
    fun `organization OAuth returns trusted organization for matching configuration`()
    {
        val organization = organization()
        val config = config(organization, IdentityProviderType.MICROSOFT)
        whenever(organizationRepository.findByVerifiedContactEmailDomain("example.com")).thenReturn(organization)
        whenever(idpConfigRepository.findActiveByOrganizationId(organization.id)).thenReturn(listOf(config))

        val result = service.resolveTrustedOrganizationForOAuth(
            "user@example.com",
            IdentityProviderType.MICROSOFT,
            config.id,
        )

        assertEquals(organization.id, result?.id)
    }

    private fun organization(): Organization = Organization().apply {
        id = UUID.randomUUID()
        name = "Example"
    }

    private fun config(
        organization: Organization,
        provider: IdentityProviderType,
    ): OrganizationIdentityProviderConfig = OrganizationIdentityProviderConfig().apply {
        id = UUID.randomUUID()
        this.organization = organization
        this.provider = provider.name
        isActive = true
    }
}
