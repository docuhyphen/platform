package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.*
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class RecordOwnerScopeAccessTest
{
    private val organizationId = UUID.randomUUID()
    private val principal = PrincipalRef.user(UUID.randomUUID())
    private val ownerScopeActions = listOf(Action.INFORMATION_REQUEST_VIEW_OPERATIONS_QUEUE, Action.INFORMATION_REQUEST_MANAGE_PRIVACY)

    @Test
    fun `organization owners and administrators administer the organization's requests across the owner`()
    {
        listOf(OrganizationRoleName.ORG_OWNER, OrganizationRoleName.ORG_ADMIN).forEach { role ->
            val access = accessFor(role)
            ownerScopeActions.forEach { action ->
                assertTrue(access.permits(principal, RecordOwnerRef.organization(organizationId), action)) { "$role must hold $action" }
            }
        }
    }

    @Test
    fun `other organization roles and other organizations are refused`()
    {
        listOf(OrganizationRoleName.ORG_MEMBER, OrganizationRoleName.ORG_AUDITOR, OrganizationRoleName.ORG_BILLING_ADMIN).forEach { role ->
            val access = accessFor(role)
            ownerScopeActions.forEach { action ->
                assertFalse(access.permits(principal, RecordOwnerRef.organization(organizationId), action)) { "$role must not hold $action" }
            }
        }
        val administrator = accessFor(OrganizationRoleName.ORG_ADMIN)
        assertFalse(administrator.permits(principal, RecordOwnerRef.organization(UUID.randomUUID()), Action.INFORMATION_REQUEST_MANAGE_PRIVACY))
    }

    @Test
    fun `a person's records admit only that person, whatever organization is active`() {
        val owner = RecordOwnerRef.user(principal.id)
        val otherPerson = PrincipalRef.user(UUID.randomUUID())
        listOf(organizationId, null).forEach { activeOrgId ->
            val access = accessFor(OrganizationRoleName.ORG_OWNER, activeOrgId)
            ownerScopeActions.forEach { action ->
                assertTrue(access.permits(principal, owner, action)) { "the person must hold $action" }
                assertFalse(access.permits(otherPerson, owner, action)) { "another person must not hold $action" }
                assertFalse(access.permits(PrincipalRef.participant(principal.id), owner, action)) {
                    "a participant with the person's id must not hold $action"
                }
            }
        }
    }

    @Test
    fun `an organization whose id equals a person's id gains nothing over that person's records`() {
        val person = UUID.randomUUID()
        val administrator = PrincipalRef.user(UUID.randomUUID())
        val access = accessFor(OrganizationRoleName.ORG_ADMIN, activeOrgId = person, caller = administrator)

        assertTrue(
            access.permits(
                administrator,
                RecordOwnerRef.organization(person),
                Action.INFORMATION_REQUEST_MANAGE_PRIVACY
            )
        )
        assertFalse(
            access.permits(
                administrator,
                RecordOwnerRef.user(person),
                Action.INFORMATION_REQUEST_MANAGE_PRIVACY
            )
        )
        assertThrows<ForbiddenException> {
            access.requireAccess(RecordOwnerRef.user(person), Action.INFORMATION_REQUEST_MANAGE_PRIVACY)
        }
    }

    @Test
    fun `the current owner is the active organization when one is selected and the person otherwise`() {
        assertEquals(
            RecordOwnerRef.organization(organizationId),
            accessFor(OrganizationRoleName.ORG_MEMBER).currentOwner()
        )
        assertEquals(
            RecordOwnerRef.user(principal.id),
            accessFor(OrganizationRoleName.ORG_MEMBER, activeOrgId = null).currentOwner(),
        )
        assertThrows<ForbiddenException> {
            accessFor(
                OrganizationRoleName.ORG_OWNER,
                caller = PrincipalRef.participant(UUID.randomUUID())
            ).currentOwner()
        }
    }

    private fun accessFor(
        role: OrganizationRoleName,
        activeOrgId: UUID? = organizationId,
        caller: PrincipalRef = principal,
    ): RecordOwnerScopeAccess
    {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val contexts = mock<AuthorizationContextFactory>()
        whenever(contexts.currentContext()).thenReturn(AuthorizationContext(activeOrgId = activeOrgId))
        whenever(contexts.currentPrincipal()).thenReturn(caller)
        val roles = mock<UserRoleService>()
        whenever(roles.orgRolesIn(any(), any())).thenReturn(setOf(role))
        return RecordOwnerScopeAccess(authorization, contexts, roles)
    }
}
