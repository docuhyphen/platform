package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.identity.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.resource.model.SignInLookupRequest
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class SignInLookupServiceTest
{
    private val identityPolicyService = mock<OrganizationIdentityPolicyService>()
    private val identityProviderRegistry = mock<IdentityProviderRegistry>()
    private val configurationService = mock<ConfigurationService>()
    private val authAuditService = mock<AuthAuditService>()
    private val authRateLimitService = mock<AuthRateLimitService>()
    private val securityIncidentService = mock<SecurityIncidentService>()

    private val service = SignInLookupService(
        identityPolicyService,
        identityProviderRegistry,
        configurationService,
        authAuditService,
        authRateLimitService,
        securityIncidentService,
    )

    @BeforeEach
    fun setUp()
    {
        whenever(configurationService.getAuthRateLimitLookupPerMinute()).thenReturn(20)
        whenever(configurationService.baseUrl).thenReturn("https://docuhyphen.example")
        whenever(authRateLimitService.isLimited(any(), any())).thenReturn(false)
        whenever(identityProviderRegistry.getAllProviders()).thenReturn(emptyList())
    }

    @Test
    fun `addresses at the same domain receive identical responses`()
    {
        val organization = organization("Company")
        val config = providerConfig(organization, IdentityProviderType.MICROSOFT)
        whenever(identityPolicyService.resolveOrganizationsForEmail(any())).thenReturn(listOf(organization))
        whenever(identityPolicyService.findActiveProviderConfigsForOrganization(organization.id))
            .thenReturn(listOf(config))

        val memberResponse = lookup("member@company.example")
        val otherResponse = lookup("someone.else@company.example")

        assertEquals(memberResponse, otherResponse)
    }

    @Test
    fun `verified domain routes to its provider without disclosing organization identity`()
    {
        val organization = organization("Configured Company")
        val config = providerConfig(organization, IdentityProviderType.GOOGLE)
        whenever(identityPolicyService.resolveOrganizationsForEmail("new@company.example"))
            .thenReturn(listOf(organization))
        whenever(identityPolicyService.findActiveProviderConfigsForOrganization(organization.id))
            .thenReturn(listOf(config))

        val response = lookup("new@company.example")

        assertEquals("GOOGLE", response.authMethod)
        assertTrue(response.redirectUrl!!.contains(config.id.toString()))
        assertFalse(response.redirectUrl!!.contains(organization.id.toString()))
        assertNull(response.fallbackAuthMethod)
    }

    @Test
    fun `domain claimed by several organizations falls back to platform providers`()
    {
        whenever(identityPolicyService.resolveOrganizationsForEmail("unknown@shared.example"))
            .thenReturn(listOf(organization("First"), organization("Second")))

        val response = lookup("unknown@shared.example")

        assertEquals("INTERNAL", response.authMethod)
        assertNull(response.redirectUrl)
        assertEquals(listOf("INTERNAL"), response.availableProviders)
        verify(identityPolicyService, never()).findActiveProviderConfigsForOrganization(any())
    }

    @Test
    fun `verified domain without an external provider falls back to platform providers`()
    {
        val organization = organization("Internal Company")
        whenever(identityPolicyService.resolveOrganizationsForEmail("person@internal.example"))
            .thenReturn(listOf(organization))
        whenever(identityPolicyService.findActiveProviderConfigsForOrganization(organization.id))
            .thenReturn(listOf(providerConfig(organization, IdentityProviderType.INTERNAL)))

        val response = lookup("person@internal.example")

        assertEquals("INTERNAL", response.authMethod)
        assertNull(response.redirectUrl)
    }

    @Test
    fun `invalid email is denied before organization lookup`()
    {
        assertThrows<InvalidSignInLookupException> { lookup("not-an-email") }

        verify(identityPolicyService, never()).resolveOrganizationsForEmail(any())
    }

    @Test
    fun `rate limit denial performs no organization lookup`()
    {
        whenever(authRateLimitService.isLimited(eq("auth:lookup:$CLIENT_IP"), any())).thenReturn(true)

        assertThrows<SignInLookupRateLimitedException> { lookup("member@company.example") }

        verify(identityPolicyService, never()).resolveOrganizationsForEmail(any())
    }

    private fun lookup(email: String) = service.lookup(SignInLookupRequest(email = email), CLIENT_IP, REQUEST_ID)

    private fun organization(name: String): Organization = Organization().apply {
        this.name = name
        registrationNumber = UUID.randomUUID().toString()
        isActive = true
        verificationComplete = true
    }

    private fun providerConfig(
        organization: Organization,
        providerType: IdentityProviderType,
    ): OrganizationIdentityProviderConfig = OrganizationIdentityProviderConfig().apply {
        this.organization = organization
        provider = providerType.name
        isActive = true
    }

    private companion object
    {
        const val CLIENT_IP = "192.0.2.10"
        const val REQUEST_ID = "request-1"
    }
}
