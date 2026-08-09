package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.exception.InactiveAccountException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.IdentityProviderLink
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.IdentityProviderLinkRepository
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.idp.OAuthUserInfo
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class OAuthUserLinkingServiceTest
{
    private val appUserService = mock<AppUserService>()
    private val linkRepository = mock<IdentityProviderLinkRepository>()
    private val authenticationService = mock<AuthenticationService>()
    private val identityPolicyService = mock<OrganizationIdentityPolicyService>()
    private val membershipService = mock<OrganizationMembershipService>()
    private val service = OAuthUserLinkingService(
        appUserService,
        linkRepository,
        authenticationService,
        identityPolicyService,
        membershipService,
    )

    @Test
    fun `existing link cannot create a session for an inactive user`()
    {
        val user = user(active = false)
        val link = IdentityProviderLink().apply {
            appUser = user
            provider = IdentityProviderType.GOOGLE
            externalSubjectId = "subject-1"
            externalEmail = user.email
        }
        whenever(
            linkRepository.findByProviderAndExternalSubjectId(IdentityProviderType.GOOGLE, "subject-1")
        ).thenReturn(link)

        assertThrows<InactiveAccountException> {
            service.linkOrCreateUser(IdentityProviderType.GOOGLE, userInfo())
        }
    }

    @Test
    fun `new OAuth user only receives organization membership from trusted configuration`()
    {
        val organization = Organization().apply {
            id = UUID.randomUUID()
            name = "Example"
        }
        val savedUser = user(active = true)
        whenever(linkRepository.findByProviderAndExternalSubjectId(any(), any())).thenReturn(null)
        whenever(appUserService.findByEmail("user@example.com")).thenReturn(null)
        whenever(appUserService.create(any())).thenReturn(savedUser)

        val result = service.linkOrCreateUser(
            IdentityProviderType.GOOGLE,
            userInfo(),
            trustedOrganization = organization,
        )

        assertEquals(savedUser.id, result.appUser.id)
        verify(identityPolicyService).enforceUserCapForOrganization(organization)
        verify(membershipService).assignOrgRole(
            appUserId = savedUser.id,
            organizationId = organization.id,
            role = OrganizationRoleName.ORG_MEMBER,
            isPrimary = true,
        )
    }

    @Test
    fun `platform OAuth user does not receive domain-derived organization membership`()
    {
        val savedUser = user(active = true)
        whenever(linkRepository.findByProviderAndExternalSubjectId(any(), any())).thenReturn(null)
        whenever(appUserService.findByEmail("user@example.com")).thenReturn(null)
        whenever(appUserService.create(any())).thenReturn(savedUser)

        service.linkOrCreateUser(IdentityProviderType.GOOGLE, userInfo())

        verify(membershipService, never()).assignOrgRole(any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `an unverified provider email cannot provision a new account`()
    {
        whenever(linkRepository.findByProviderAndExternalSubjectId(any(), any())).thenReturn(null)
        whenever(appUserService.findByEmail("user@example.com")).thenReturn(null)

        assertThrows<UnverifiedExternalEmailException> {
            service.linkOrCreateUser(IdentityProviderType.GOOGLE, userInfo(emailVerified = false))
        }

        verify(appUserService, never()).create(any())
    }

    @Test
    fun `an unverified provider email still reaches password confirmation for an existing account`()
    {
        val existingUser = user(active = true)
        whenever(linkRepository.findByProviderAndExternalSubjectId(any(), any())).thenReturn(null)
        whenever(linkRepository.findAllByAppUserId(existingUser.id)).thenReturn(emptyList())
        whenever(appUserService.findByEmail("user@example.com")).thenReturn(existingUser)
        whenever(authenticationService.generateLinkToken(any(), any(), any())).thenReturn("link-token")

        val result = service.linkOrCreateUser(
            IdentityProviderType.GOOGLE,
            userInfo(emailVerified = false),
        )

        assertTrue(result.requiresLinkConfirmation)
        verify(appUserService, never()).create(any())
    }

    private fun user(active: Boolean): AppUser = AppUser().apply {
        id = UUID.randomUUID()
        email = "user@example.com"
        isActive = active
    }

    private fun userInfo(emailVerified: Boolean = true): OAuthUserInfo = OAuthUserInfo(
        email = "user@example.com",
        subjectId = "subject-1",
        firstName = null,
        lastName = null,
        emailVerified = emailVerified,
    )
}
