package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.resource.model.SignInLookupRequest
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class SignInLookupServiceTest
{
    private val appUserService = mock<AppUserService>()
    private val membershipService = mock<OrganizationMembershipService>()
    private val identityPolicyService = mock<OrganizationIdentityPolicyService>()
    private val identityProviderRegistry = mock<IdentityProviderRegistry>()
    private val configurationService = mock<ConfigurationService>()
    private val authAuditService = mock<AuthAuditService>()
    private val authRateLimitService = mock<AuthRateLimitService>()
    private val securityIncidentService = mock<SecurityIncidentService>()

    private val service = SignInLookupService(
        appUserService,
        membershipService,
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
    fun `lookup returns only current active memberships for an exact account`()
    {
        val user = appUser("member@gmail.com")
        val first = organization("Alpha")
        val second = organization("Beta")
        val unrelated = organization("Unrelated")
        whenever(appUserService.findRegisteredByEmail(user.email)).thenReturn(user)
        whenever(membershipService.activeOrganizationIds(user.id)).thenReturn(setOf(first.id, second.id))
        whenever(identityPolicyService.findOrganizationById(first.id)).thenReturn(first)
        whenever(identityPolicyService.findOrganizationById(second.id)).thenReturn(second)
        whenever(identityPolicyService.resolveOrganizationsForEmail(user.email))
            .thenReturn(listOf(first, second, unrelated))

        val response = service.lookup(
            SignInLookupRequest(email = " MEMBER@GMAIL.COM "),
            CLIENT_IP,
            REQUEST_ID,
        )

        assertEquals("MULTIPLE_ORGS", response.outcome)
        assertEquals(listOf(first.id.toString(), second.id.toString()), response.organizations.map { it.id })
        assertTrue(response.organizations.none { it.id == unrelated.id.toString() })
        verify(identityPolicyService, never()).resolveOrganizationsForEmail(any())
    }

    @Test
    fun `member organization selection routes through its configured provider`()
    {
        val user = appUser("member@company.example")
        val organization = organization("Company")
        val config = providerConfig(organization, IdentityProviderType.MICROSOFT)
        whenever(appUserService.findRegisteredByEmail(user.email)).thenReturn(user)
        whenever(membershipService.activeOrganizationIds(user.id)).thenReturn(setOf(organization.id))
        whenever(identityPolicyService.findOrganizationById(organization.id)).thenReturn(organization)
        whenever(identityPolicyService.findActiveProviderConfigsForOrganization(organization.id))
            .thenReturn(listOf(config))

        val response = service.lookup(
            SignInLookupRequest(email = user.email, orgId = organization.id.toString()),
            CLIENT_IP,
            REQUEST_ID,
        )

        assertEquals("MICROSOFT", response.authMethod)
        assertEquals("ORG_FOUND", response.outcome)
        assertEquals(listOf(organization.id.toString()), response.organizations.map { it.id })
        assertTrue(response.redirectUrl!!.contains(config.id.toString()))
    }

    @Test
    fun `consumer domain cannot enumerate unrelated organizations`()
    {
        val first = organization("First")
        val second = organization("Second")
        whenever(appUserService.findRegisteredByEmail("unknown@gmail.com")).thenReturn(null)
        whenever(identityPolicyService.resolveOrganizationsForEmail("unknown@gmail.com"))
            .thenReturn(listOf(first, second))

        val response = service.lookup(
            SignInLookupRequest(email = "unknown@gmail.com"),
            CLIENT_IP,
            REQUEST_ID,
        )

        assertEquals("NO_ORG", response.outcome)
        assertTrue(response.organizations.isEmpty())
        assertEquals(listOf("INTERNAL"), response.availableProviders)
        verify(identityPolicyService, never()).findActiveProviderConfigsForOrganization(any())
    }

    @Test
    fun `unambiguous configured domain routes without disclosing organization identity`()
    {
        val organization = organization("Configured Company")
        val config = providerConfig(organization, IdentityProviderType.GOOGLE)
        whenever(appUserService.findRegisteredByEmail("new@company.example")).thenReturn(null)
        whenever(identityPolicyService.resolveOrganizationsForEmail("new@company.example"))
            .thenReturn(listOf(organization))
        whenever(identityPolicyService.findActiveProviderConfigsForOrganization(organization.id))
            .thenReturn(listOf(config))

        val response = service.lookup(
            SignInLookupRequest(email = "new@company.example"),
            CLIENT_IP,
            REQUEST_ID,
        )

        assertEquals("GOOGLE", response.authMethod)
        assertEquals("NO_ORG", response.outcome)
        assertTrue(response.organizations.isEmpty())
        assertTrue(response.redirectUrl!!.contains(config.id.toString()))
    }

    @Test
    fun `tampered organization selection is denied before provider lookup and without mutation`()
    {
        val user = appUser("member@company.example")
        val organization = organization("Company")
        val originalUserActive = user.isActive
        val originalOrganizationActive = organization.isActive
        whenever(appUserService.findRegisteredByEmail(user.email)).thenReturn(user)
        whenever(membershipService.activeOrganizationIds(user.id)).thenReturn(setOf(organization.id))
        whenever(identityPolicyService.findOrganizationById(organization.id)).thenReturn(organization)

        assertThrows<InvalidSignInLookupException> {
            service.lookup(
                SignInLookupRequest(email = user.email, orgId = UUID.randomUUID().toString()),
                CLIENT_IP,
                REQUEST_ID,
            )
        }

        assertEquals(originalUserActive, user.isActive)
        assertEquals(originalOrganizationActive, organization.isActive)
        verify(identityPolicyService, never()).findActiveProviderConfigsForOrganization(any())
        verify(identityPolicyService, never()).resolveOrganizationsForEmail(any())
    }

    @Test
    fun `inactive organization membership is excluded as stale state`()
    {
        val user = appUser("member@company.example")
        val inactiveOrganization = organization("Inactive").apply { isActive = false }
        whenever(appUserService.findRegisteredByEmail(user.email)).thenReturn(user)
        whenever(membershipService.activeOrganizationIds(user.id)).thenReturn(setOf(inactiveOrganization.id))
        whenever(identityPolicyService.findOrganizationById(inactiveOrganization.id))
            .thenReturn(inactiveOrganization)
        whenever(identityPolicyService.resolveOrganizationsForEmail(user.email)).thenReturn(emptyList())

        val response = service.lookup(
            SignInLookupRequest(email = user.email),
            CLIENT_IP,
            REQUEST_ID,
        )

        assertEquals("NO_ORG", response.outcome)
        assertTrue(response.organizations.isEmpty())
    }

    @Test
    fun `deprovisioned account cannot project former memberships`()
    {
        val user = appUser("former@company.example").apply {
            deprovisionedAt = Timestamp.from(Instant.now())
        }
        whenever(appUserService.findRegisteredByEmail(user.email)).thenReturn(user)
        whenever(identityPolicyService.resolveOrganizationsForEmail(user.email)).thenReturn(emptyList())

        val response = service.lookup(
            SignInLookupRequest(email = user.email),
            CLIENT_IP,
            REQUEST_ID,
        )

        assertEquals("NO_ORG", response.outcome)
        assertTrue(response.organizations.isEmpty())
        verify(membershipService, never()).activeOrganizationIds(any())
    }

    @Test
    fun `invalid email is denied before account or organization lookup`()
    {
        assertThrows<InvalidSignInLookupException> {
            service.lookup(
                SignInLookupRequest(email = "not-an-email"),
                CLIENT_IP,
                REQUEST_ID,
            )
        }

        verify(appUserService, never()).findRegisteredByEmail(any())
        verify(identityPolicyService, never()).resolveOrganizationsForEmail(any())
    }

    @Test
    fun `rate limit denial performs no account or organization lookup`()
    {
        whenever(authRateLimitService.isLimited(eq("auth:lookup:$CLIENT_IP"), any())).thenReturn(true)

        assertThrows<SignInLookupRateLimitedException> {
            service.lookup(
                SignInLookupRequest(email = "member@company.example"),
                CLIENT_IP,
                REQUEST_ID,
            )
        }

        verify(appUserService, never()).findRegisteredByEmail(any())
        verify(identityPolicyService, never()).resolveOrganizationsForEmail(any())
    }

    private fun appUser(email: String): AppUser = AppUser().apply {
        this.email = email
        isActive = true
        isTemporary = false
    }

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
