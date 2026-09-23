package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.dto.CurrentSessionDto
import com.docuhyphen.app.api.model.dto.SessionOrganizationOptionDto
import com.docuhyphen.app.api.repository.organization.OrganizationMembershipRepository
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.RoleCapabilities
import com.docuhyphen.app.api.service.subscription.SessionSubscriptionService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Instant

/**
 * Computes the effective session contract for the authenticated caller.
 *
 * The returned [CurrentSessionDto] exposes the caller's identity, the explicitly selected
 * organization (null for personal-product sessions), all applicable scoped roles, and the union
 * of effective capabilities derived from those roles. Capabilities are recomputed on every call
 * from live role assignments, so role changes are reflected without reissuing tokens.
 *
 * The commercial position of the paying subject is resolved alongside, and stays strictly
 * separate: capabilities decide what the caller is allowed to do, while the subscription
 * decides what the paying subject has bought.
 */
@ApplicationScoped
class SessionService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val userRoleService: UserRoleService,
    private val authSessionPolicyService: AuthSessionPolicyService,
    private val organizationMembershipRepository: OrganizationMembershipRepository,
    private val organizationRepository: OrganizationRepository,
    private val sessionSubscriptionService: SessionSubscriptionService,
    private val userSessionService: UserSessionService,
)
{
    fun currentSession(): CurrentSessionDto
    {
        val user = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("Not authenticated")

        val appRoles = userRoleService.appRoles(user.id!!)
        val activeOrgId = authTokenContext.activeOrganizationId
        val orgRoles = if (activeOrgId != null)
        {
            userRoleService.orgRolesIn(user.id, activeOrgId)
        }
        else
        {
            emptySet()
        }

        val capabilities = mutableSetOf<Capability>()
        appRoles.forEach { capabilities += RoleCapabilities.forAppRole(it) }
        orgRoles.forEach { capabilities += RoleCapabilities.forOrganizationRole(it) }

        val availableOrganizations = organizationMembershipRepository.findActiveByUser(user.id).map { m ->
            val org = organizationRepository.findById(m.organizationId)
            SessionOrganizationOptionDto(
                organizationId = m.organizationId,
                name = org?.name ?: "Unknown organization",
                isPrimary = m.isPrimary,
                roles = m.roles.map { it.name },
            )
        }

        val policy = authSessionPolicyService.resolveForAppUser(user)
        val now = Instant.now()
        val userSession = authTokenContext.userSessionId?.let(userSessionService::findSession)
        val lastSeenAt = userSession?.lastSeenAt?.toInstant() ?: now

        return CurrentSessionDto(
            userId = user.id,
            email = user.email,
            appRoles = appRoles.map { it.name },
            activeOrganizationId = activeOrgId,
            organizationRoles = orgRoles.map { it.name },
            capabilities = capabilities.map { it.name }.sorted(),
            availableOrganizations = availableOrganizations,
            idleTimeoutMinutes = policy.idleTimeoutMinutes,
            serverTimeEpochMs = now.toEpochMilli(),
            idleExpiresAtEpochMs = lastSeenAt.plusSeconds(policy.idleTimeoutMinutes * 60).toEpochMilli(),
            sessionExpiresAtEpochMs = userSession?.expiresAt?.toInstant()?.toEpochMilli(),
            subscription = sessionSubscriptionService.describe(user.id, activeOrgId),
        )
    }
}
