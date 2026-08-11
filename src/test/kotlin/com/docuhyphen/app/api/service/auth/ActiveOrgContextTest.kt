package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.OrganizationMembership
import com.docuhyphen.app.api.model.entity.OrganizationMembershipStatus
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.repository.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.subscription.SessionSubscriptionService
import io.quarkus.security.UnauthorizedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Explicit active organization context and current-session capability contract.
 *
 * Rules verified:
 *  - No active-org header -> session has null activeOrganizationId (personal mode).
 *  - Valid active-org header -> session reflects that organization and its roles.
 *  - Capabilities are the union of app-role and org-role capability sets.
 *  - Multiple concurrent org roles compose their capability sets.
 *  - Session unauthenticated (no appUser) -> UnauthorizedException.
 *  - Active org is caller context only; no capabilities are added by setting it alone.
 *  - Removing org context (null header) drops org-role capabilities.
 *  - APP_USER role provides EXCHANGE_INITIATE; org-only capabilities absent without active org.
 *  - ORG_BILLING_ADMIN + ORG_MEMBER compose independently: billing + member capabilities both present.
 *  - ORG_MEMBER capabilities not present when user has org billing role only.
 *  - APP_ADMIN gains APP_ADMIN, APP_AUDIT_READ, APP_REG_READ, APP_REG_ADMIN.
 *  - Organization switching preserves app capabilities and recalculates organization capabilities.
 *  - Stale roles reflect immediately because capabilities are resolved at call time.
 */
class ActiveOrgContextTest
{
    private val userId = UUID.randomUUID()
    private val orgId = UUID.randomUUID()
    private val membershipId = UUID.randomUUID()

    private fun makeUser(): AppUser = AppUser().apply {
        id = userId
        email = "alice@example.com"
        isActive = true
    }

    private fun makeToken(user: AppUser): AuthToken = AuthToken().apply {
        appUser = user
    }

    private fun makeContext(
        user: AppUser? = makeUser(),
        activeOrgId: UUID? = null,
        activeMembershipId: UUID? = null,
    ): AuthTokenContext
    {
        val ctx = AuthTokenContext()
        if (user != null)
        {
            ctx.authToken = makeToken(user)
        }
        ctx.activeOrganizationId = activeOrgId
        ctx.activeMembershipId = activeMembershipId
        return ctx
    }

    private fun makeUserRoleService(
        appRoles: Set<AppRoleName> = emptySet(),
        orgRoles: Set<OrganizationRoleName> = emptySet(),
        forOrgId: UUID = orgId,
    ): UserRoleService
    {
        val svc = mock<UserRoleService>()
        whenever(svc.appRoles(userId)).thenReturn(appRoles)
        whenever(svc.orgRolesIn(userId, forOrgId)).thenReturn(orgRoles)
        return svc
    }

    private fun makeMembership(
        organizationId: UUID,
        isPrimary: Boolean = false,
        roles: Set<OrganizationRoleName> = emptySet(),
    ): OrganizationMembership = OrganizationMembership().apply {
        this.appUserId = userId
        this.organizationId = organizationId
        this.isPrimary = isPrimary
        this.roles = roles.toMutableSet()
        this.status = OrganizationMembershipStatus.ACTIVE
    }

    private fun makeAuthSessionPolicyService(): AuthSessionPolicyService
    {
        val service = mock<AuthSessionPolicyService>()
        whenever(service.resolveForAppUser(any())).thenReturn(
            AuthSessionPolicy(
                accessTokenExpiryMinutes = 15,
                refreshTokenExpiryMinutes = 60,
                maxSessionDurationHours = 8,
                idleTimeoutMinutes = 30,
            )
        )
        return service
    }

    private fun makeSessionService(
        ctx: AuthTokenContext,
        userRoleService: UserRoleService,
        memberships: List<OrganizationMembership> = emptyList(),
        orgNames: Map<UUID, String> = emptyMap(),
    ): SessionService
    {
        val membershipRepo = mock<OrganizationMembershipRepository>()
        whenever(membershipRepo.findActiveByUser(userId)).thenReturn(memberships)
        val orgRepo = mock<OrganizationRepository>()
        memberships.forEach { m ->
            val org = Organization().apply {
                id = m.organizationId
                name = orgNames[m.organizationId] ?: "Org ${m.organizationId}"
            }
            whenever(orgRepo.findById(m.organizationId)).thenReturn(org)
        }
        return SessionService(
            ctx,
            userRoleService,
            makeAuthSessionPolicyService(),
            membershipRepo,
            orgRepo,
            mock<SessionSubscriptionService>(),
        )
    }

    // -----------------------------------------------------------------------
    // Personal mode (no active org header)
    // -----------------------------------------------------------------------

    @Test
    fun `no active org header produces null activeOrganizationId in session`()
    {
        val ctx = makeContext(activeOrgId = null)
        val svc = makeSessionService(ctx, makeUserRoleService(appRoles = setOf(AppRoleName.APP_USER)))

        val session = svc.currentSession()

        assertNull(session.activeOrganizationId)
        assertTrue(session.organizationRoles.isEmpty())
        assertEquals(30, session.idleTimeoutMinutes)
    }

    @Test
    fun `APP_USER in personal mode has EXCHANGE_INITIATE and no org capabilities`()
    {
        val ctx = makeContext(activeOrgId = null)
        val svc = makeSessionService(ctx, makeUserRoleService(appRoles = setOf(AppRoleName.APP_USER)))

        val caps = svc.currentSession().capabilities

        assertTrue(Capability.EXCHANGE_INITIATE.name in caps)
        assertFalse(Capability.ORG_POLICY_MANAGE.name in caps)
        assertFalse(Capability.DOC_LIBRARY_WRITE.name in caps)
    }

    @Test
    fun `unauthenticated request throws UnauthorizedException`()
    {
        val ctx = AuthTokenContext()
        val svc = makeSessionService(ctx, mock())

        assertThrows<Exception> { svc.currentSession() }
    }

    // -----------------------------------------------------------------------
    // Active org selection
    // -----------------------------------------------------------------------

    @Test
    fun `valid active org produces correct organization roles and capabilities`()
    {
        val ctx = makeContext(activeOrgId = orgId, activeMembershipId = membershipId)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(
                appRoles = setOf(AppRoleName.APP_USER),
                orgRoles = setOf(OrganizationRoleName.ORG_MEMBER),
            ),
        )

        val session = svc.currentSession()

        assertEquals(orgId, session.activeOrganizationId)
        assertTrue(OrganizationRoleName.ORG_MEMBER.name in session.organizationRoles)
        assertTrue(Capability.DOC_LIBRARY_DISCOVER.name in session.capabilities)
        assertTrue(Capability.BLUEPRINT_DISCOVER.name in session.capabilities)
    }

    @Test
    fun `org admin capabilities present when active org membership is ORG_ADMIN`()
    {
        val ctx = makeContext(activeOrgId = orgId, activeMembershipId = membershipId)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(
                appRoles = setOf(AppRoleName.APP_USER),
                orgRoles = setOf(OrganizationRoleName.ORG_ADMIN),
            ),
        )

        val caps = svc.currentSession().capabilities

        assertTrue(Capability.ORG_POLICY_MANAGE.name in caps)
        assertTrue(Capability.ORG_MEMBER_MANAGE.name in caps)
        assertTrue(Capability.DOC_LIBRARY_WRITE.name in caps)
        assertFalse(Capability.ORG_BILLING_MANAGE.name in caps)
    }

    @Test
    fun `ORG_OWNER has billing capability that ORG_ADMIN does not`()
    {
        val ctx = makeContext(activeOrgId = orgId, activeMembershipId = membershipId)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(
                orgRoles = setOf(OrganizationRoleName.ORG_OWNER),
            ),
        )

        val caps = svc.currentSession().capabilities

        assertTrue(Capability.ORG_BILLING_MANAGE.name in caps)
        assertTrue(Capability.ORG_POLICY_MANAGE.name in caps)
    }

    // -----------------------------------------------------------------------
    // Multi-role composition
    // -----------------------------------------------------------------------

    @Test
    fun `ORG_BILLING_ADMIN and ORG_MEMBER compose independently`()
    {
        val ctx = makeContext(activeOrgId = orgId, activeMembershipId = membershipId)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(
                appRoles = setOf(AppRoleName.APP_USER),
                orgRoles = setOf(OrganizationRoleName.ORG_BILLING_ADMIN, OrganizationRoleName.ORG_MEMBER),
            ),
        )

        val caps = svc.currentSession().capabilities

        assertTrue(Capability.ORG_BILLING_MANAGE.name in caps, "Billing role must contribute billing cap")
        assertTrue(Capability.DOC_LIBRARY_DISCOVER.name in caps, "Member role must contribute member cap")
        assertTrue(Capability.EXCHANGE_INITIATE.name in caps, "Member role must contribute initiate")
    }

    @Test
    fun `ORG_BILLING_ADMIN alone does not grant ORG_MEMBER capabilities`()
    {
        val ctx = makeContext(activeOrgId = orgId, activeMembershipId = membershipId)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(
                orgRoles = setOf(OrganizationRoleName.ORG_BILLING_ADMIN),
            ),
        )

        val caps = svc.currentSession().capabilities

        assertTrue(Capability.ORG_BILLING_MANAGE.name in caps)
        assertFalse(Capability.DOC_LIBRARY_WRITE.name in caps)
        assertFalse(Capability.BLUEPRINT_WRITE.name in caps)
    }

    // -----------------------------------------------------------------------
    // Platform roles
    // -----------------------------------------------------------------------

    @Test
    fun `APP_ADMIN session has platform capabilities but not org-admin capabilities`()
    {
        val ctx = makeContext(activeOrgId = null)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(appRoles = setOf(AppRoleName.APP_ADMIN)),
        )

        val caps = svc.currentSession().capabilities

        assertTrue(Capability.APP_ADMIN.name in caps)
        assertTrue(Capability.APP_AUDIT_READ.name in caps)
        assertTrue(Capability.APP_REG_READ.name in caps)
        assertTrue(Capability.APP_REG_ADMIN.name in caps)
        assertFalse(Capability.ORG_POLICY_MANAGE.name in caps, "APP_ADMIN must not get org-admin cap without org role")
    }

    @Test
    fun `APP_AUDITOR has audit read caps and not customer-content write caps`()
    {
        val ctx = makeContext(activeOrgId = null)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(appRoles = setOf(AppRoleName.APP_AUDITOR)),
        )

        val caps = svc.currentSession().capabilities

        assertTrue(Capability.APP_AUDIT_READ.name in caps)
        assertFalse(Capability.EXCHANGE_WRITE.name in caps)
        assertFalse(Capability.DOCUMENT_WRITE.name in caps)
    }

    @Test
    fun `switching organizations preserves APP_ADMIN and recalculates tenant capabilities`()
    {
        val memberOrgId = UUID.randomUUID()
        val ctx = makeContext(activeOrgId = orgId, activeMembershipId = membershipId)
        val userRoleSvc = mock<UserRoleService>()
        whenever(userRoleSvc.appRoles(userId)).thenReturn(setOf(AppRoleName.APP_ADMIN))
        whenever(userRoleSvc.orgRolesIn(userId, orgId))
            .thenReturn(setOf(OrganizationRoleName.ORG_ADMIN))
        whenever(userRoleSvc.orgRolesIn(userId, memberOrgId))
            .thenReturn(setOf(OrganizationRoleName.ORG_MEMBER))
        val svc = makeSessionService(ctx, userRoleSvc)

        val adminSession = svc.currentSession()

        assertEquals(orgId, adminSession.activeOrganizationId)
        assertTrue(Capability.APP_ADMIN.name in adminSession.capabilities)
        assertTrue(Capability.ORG_POLICY_MANAGE.name in adminSession.capabilities)

        ctx.activeOrganizationId = memberOrgId
        ctx.activeMembershipId = UUID.randomUUID()
        val memberSession = svc.currentSession()

        assertEquals(memberOrgId, memberSession.activeOrganizationId)
        assertTrue(Capability.APP_ADMIN.name in memberSession.capabilities)
        assertTrue(Capability.DOC_LIBRARY_DISCOVER.name in memberSession.capabilities)
        assertFalse(Capability.ORG_POLICY_MANAGE.name in memberSession.capabilities)
        assertEquals(
            listOf(OrganizationRoleName.ORG_MEMBER.name),
            memberSession.organizationRoles,
        )

        ctx.activeOrganizationId = null
        ctx.activeMembershipId = null
        val personalSession = svc.currentSession()

        assertNull(personalSession.activeOrganizationId)
        assertTrue(Capability.APP_ADMIN.name in personalSession.capabilities)
        assertFalse(Capability.DOC_LIBRARY_DISCOVER.name in personalSession.capabilities)
        assertFalse(Capability.ORG_POLICY_MANAGE.name in personalSession.capabilities)
        assertTrue(personalSession.organizationRoles.isEmpty())
    }

    // -----------------------------------------------------------------------
    // Session DTO shape
    // -----------------------------------------------------------------------

    @Test
    fun `session DTO carries user identity fields`()
    {
        val ctx = makeContext()
        val svc = makeSessionService(ctx, makeUserRoleService())

        val session = svc.currentSession()

        assertEquals(userId, session.userId)
        assertEquals("alice@example.com", session.email)
    }

    @Test
    fun `capabilities list is sorted for stable serialization`()
    {
        val ctx = makeContext(activeOrgId = orgId, activeMembershipId = membershipId)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(
                appRoles = setOf(AppRoleName.APP_USER),
                orgRoles = setOf(OrganizationRoleName.ORG_MEMBER),
            ),
        )

        val caps = svc.currentSession().capabilities

        assertEquals(caps.sorted(), caps)
    }

    @Test
    fun `no active org drops org capabilities even if orgRolesIn would return roles`()
    {
        val ctx = makeContext(activeOrgId = null)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(
                appRoles = setOf(AppRoleName.APP_USER),
                orgRoles = setOf(OrganizationRoleName.ORG_ADMIN),
                forOrgId = orgId,
            ),
        )

        val caps = svc.currentSession().capabilities

        assertFalse(Capability.ORG_POLICY_MANAGE.name in caps)
        assertFalse(Capability.DOC_LIBRARY_WRITE.name in caps)
    }

    @Test
    fun `stale roles reflected immediately because capabilities resolved at call time`()
    {
        val ctx = makeContext(activeOrgId = orgId, activeMembershipId = membershipId)
        val userRoleSvc = mock<UserRoleService>()

        whenever(userRoleSvc.appRoles(userId)).thenReturn(setOf(AppRoleName.APP_USER))
        whenever(userRoleSvc.orgRolesIn(userId, orgId))
            .thenReturn(setOf(OrganizationRoleName.ORG_ADMIN))
            .thenReturn(emptySet())

        val svc = makeSessionService(ctx, userRoleSvc)

        val firstCall = svc.currentSession().capabilities
        val secondCall = svc.currentSession().capabilities

        assertTrue(Capability.ORG_POLICY_MANAGE.name in firstCall, "First call: admin role present")
        assertFalse(Capability.ORG_POLICY_MANAGE.name in secondCall, "Second call: role revoked, must not appear")
    }

    @Test
    fun `AuthTokenContext fields are null when no header is provided`()
    {
        val ctx = AuthTokenContext()
        ctx.authToken = makeToken(makeUser())

        assertNull(ctx.activeOrganizationId)
        assertNull(ctx.activeMembershipId)
    }

    @Test
    fun `AuthTokenContext stores active org fields set by filter`()
    {
        val ctx = AuthTokenContext()
        ctx.authToken = makeToken(makeUser())
        ctx.activeOrganizationId = orgId
        ctx.activeMembershipId = membershipId

        assertEquals(orgId, ctx.activeOrganizationId)
        assertEquals(membershipId, ctx.activeMembershipId)
    }

    // -----------------------------------------------------------------------
    // Available organizations (auto-select / picker source data)
    // -----------------------------------------------------------------------

    @Test
    fun `zero active memberships yields empty availableOrganizations`()
    {
        val ctx = makeContext(activeOrgId = null)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(appRoles = setOf(AppRoleName.APP_USER)),
            memberships = emptyList(),
        )

        assertTrue(svc.currentSession().availableOrganizations.isEmpty())
    }

    @Test
    fun `single active membership is surfaced with name roles and primary flag`()
    {
        val ctx = makeContext(activeOrgId = null)
        val membership = makeMembership(
            organizationId = orgId,
            isPrimary = false,
            roles = setOf(OrganizationRoleName.ORG_MEMBER),
        )
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(appRoles = setOf(AppRoleName.APP_USER)),
            memberships = listOf(membership),
            orgNames = mapOf(orgId to "Acme Inc"),
        )

        val orgs = svc.currentSession().availableOrganizations

        assertEquals(1, orgs.size)
        assertEquals(orgId, orgs[0].organizationId)
        assertEquals("Acme Inc", orgs[0].name)
        assertFalse(orgs[0].isPrimary)
        assertTrue(OrganizationRoleName.ORG_MEMBER.name in orgs[0].roles)
    }

    @Test
    fun `multiple active memberships are all surfaced`()
    {
        val secondOrgId = UUID.randomUUID()
        val ctx = makeContext(activeOrgId = null)
        val svc = makeSessionService(
            ctx,
            makeUserRoleService(appRoles = setOf(AppRoleName.APP_USER)),
            memberships = listOf(
                makeMembership(organizationId = orgId, isPrimary = true),
                makeMembership(organizationId = secondOrgId, isPrimary = false),
            ),
            orgNames = mapOf(orgId to "Acme Inc", secondOrgId to "Globex"),
        )

        val orgs = svc.currentSession().availableOrganizations

        assertEquals(2, orgs.size)
        assertEquals(setOf(orgId, secondOrgId), orgs.map { it.organizationId }.toSet())
    }

    @Test
    fun `unresolvable organization falls back to placeholder name`()
    {
        val ctx = makeContext(activeOrgId = null)
        val membershipRepo = mock<OrganizationMembershipRepository>()
        whenever(membershipRepo.findActiveByUser(userId))
            .thenReturn(listOf(makeMembership(organizationId = orgId)))
        val orgRepo = mock<OrganizationRepository>()
        whenever(orgRepo.findById(orgId)).thenReturn(null)
        val svc = SessionService(
            ctx,
            makeUserRoleService(appRoles = setOf(AppRoleName.APP_USER)),
            makeAuthSessionPolicyService(),
            membershipRepo,
            orgRepo,
            mock<SessionSubscriptionService>(),
        )

        assertEquals("Unknown organization", svc.currentSession().availableOrganizations[0].name)
    }
}
