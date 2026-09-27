package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

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

    private fun accessFor(role: OrganizationRoleName): RecordOwnerScopeAccess
    {
        val authorization = mock<AuthorizationService>()
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        val contexts = mock<AuthorizationContextFactory>()
        whenever(contexts.currentContext()).thenReturn(AuthorizationContext(activeOrgId = organizationId))
        whenever(contexts.currentPrincipal()).thenReturn(principal)
        val roles = mock<UserRoleService>()
        whenever(roles.orgRolesIn(any(), any())).thenReturn(setOf(role))
        return RecordOwnerScopeAccess(authorization, contexts, roles)
    }
}
