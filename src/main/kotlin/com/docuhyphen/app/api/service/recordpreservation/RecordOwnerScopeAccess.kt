package com.docuhyphen.app.api.service.recordpreservation

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.RecordOwnerKind
import com.docuhyphen.app.api.model.recordpreservation.RecordOwnerRef
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.*
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class RecordOwnerScopeAccess @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
)
{
    fun currentOwner(): RecordOwnerRef
    {
        val principal = requireUser()
        return authorizationContextFactory.currentContext().activeOrgId?.let(RecordOwnerRef::organization)
            ?: RecordOwnerRef.user(principal.id)
    }

    fun requireAccess(owner: RecordOwnerRef, action: Action): PrincipalRef
    {
        val principal = requireUser()
        if (permits(principal, owner, action)) return principal
        throw ForbiddenException("Access denied to this owner's records")
    }

    fun permits(principal: PrincipalRef, owner: RecordOwnerRef, action: Action): Boolean = when (owner.kind)
    {
        RecordOwnerKind.USER -> principal.kind == PrincipalKind.USER && principal.id == owner.id
        RecordOwnerKind.ORGANIZATION ->
        {
            val organizationId = requireNotNull(owner.id)
            principal.kind == PrincipalKind.USER &&
                    authorizationContextFactory.currentContext().activeOrgId == organizationId &&
                    userRoleService.orgRolesIn(principal.id, organizationId)
                        .any { action.required in RoleCapabilities.forOrganizationRole(it) } &&
                    authorizationService.authorize(
                        principal,
                        action,
                        ResourceRef.organization(organizationId),
                        authorizationContextFactory.currentContext(),
                    ) is Decision.Allow
        }

        RecordOwnerKind.PLATFORM -> false
    }

    fun requireUser(): PrincipalRef
    {
        val principal = authorizationContextFactory.currentPrincipal() ?: throw ForbiddenException("Not authenticated")
        if (principal.kind != PrincipalKind.USER) throw ForbiddenException("Records are administered by a user")
        return principal
    }
}
