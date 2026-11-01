package com.docuhyphen.app.api.service.organization

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.organization.OrganizationAuthSessionPolicyDtoMapper
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyResponse
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicySettingsResponse
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthSessionPolicyService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.identity.OrganizationIdentityProviderConfigService
import com.docuhyphen.app.api.service.user.AppUserService
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class OrganizationAuthSessionPolicyService @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val appUserService: AppUserService,
    private val authSessionPolicyService: AuthSessionPolicyService,
    private val authAuditService: AuthAuditService,
    private val organizationIdentityProviderConfigService: OrganizationIdentityProviderConfigService,
    private val configurationService: ConfigurationService,
    private val userRoleService: UserRoleService,
    private val organizationMembershipService: OrganizationMembershipService,
)
{
    companion object
    {
        private const val VIEW_ACTION = "ORG_AUTH_EXCHANGE_POLICY_VIEW"
    }

    private class ViewAttempt(var actorId: UUID? = null, var organizationId: UUID? = null)

    fun getEffectivePolicy(
        organizationId: String,
        appUserId: String?,
        requestId: String?,
    ): OrganizationAuthSessionPolicyResponse
    {
        val attempt = ViewAttempt()
        try
        {
            val orgId = requireOrganizationAdmin(organizationId, attempt)

            val policy = if (!appUserId.isNullOrBlank())
            {
                val targetUserId = runCatching { UUID.fromString(appUserId) }
                    .getOrElse { throw IllegalArgumentException("Invalid app user ID format") }
                val targetUser = appUserService.getById(targetUserId)
                    ?: throw IllegalArgumentException("App user not found")

                if (!organizationMembershipService.isMember(targetUser.id, orgId))
                {
                    throw UnauthorizedException("App user does not belong to the organization")
                }

                authSessionPolicyService.resolveForAppUser(targetUser)
            }
            else
            {
                authSessionPolicyService.resolveForOrganization(orgId)
            }

            authAuditService.emit(
                action = VIEW_ACTION,
                outcome = "SUCCESS",
                actorId = attempt.actorId,
                organizationId = attempt.organizationId,
                requestId = requestId,
                reason = if (appUserId.isNullOrBlank()) "Viewed effective organization auth session policy" else "Viewed effective app-user auth session policy",
                afterSnapshot = OrganizationAuthSessionPolicyDtoMapper.policySnapshot(organizationId, appUserId, policy),
            )
            return OrganizationAuthSessionPolicyDtoMapper.toEffectiveResponse(orgId, appUserId, policy)
        }
        catch (exception: Exception)
        {
            emitDenied(attempt, requestId, exception)
            throw exception
        }
    }

    fun getSettings(organizationId: String, requestId: String?): OrganizationAuthSessionPolicySettingsResponse
    {
        val attempt = ViewAttempt()
        try
        {
            val orgId = requireOrganizationAdmin(organizationId, attempt)

            organizationIdentityProviderConfigService.ensureInternalConfig(orgId)

            val configs = organizationIdentityProviderConfigService.listForPolicyView(organizationId)
            val policy = authSessionPolicyService.resolveForOrganization(orgId)

            authAuditService.emit(
                action = VIEW_ACTION,
                outcome = "SUCCESS",
                actorId = attempt.actorId,
                organizationId = attempt.organizationId,
                requestId = requestId,
                reason = "Viewed organization auth session policy settings",
            )
            return OrganizationAuthSessionPolicyDtoMapper.toSettingsResponse(orgId, policy, configurationService, configs)
        }
        catch (exception: Exception)
        {
            emitDenied(attempt, requestId, exception)
            throw exception
        }
    }

    private fun requireOrganizationAdmin(organizationId: String, attempt: ViewAttempt): UUID
    {
        val currentUser = authTokenContext.authToken.appUser
            ?: throw UnauthorizedException("User is not authenticated")
        attempt.actorId = currentUser.id

        val orgId = runCatching { UUID.fromString(organizationId) }
            .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }
        attempt.organizationId = orgId

        if (!userRoleService.isOrgAdminIn(currentUser.id, orgId))
        {
            throw UnauthorizedException("User does not have permission to view auth session policy")
        }
        return orgId
    }

    private fun emitDenied(attempt: ViewAttempt, requestId: String?, exception: Exception)
    {
        authAuditService.emit(
            action = VIEW_ACTION,
            outcome = "DENY",
            reasonCode = RevocationReasonCode.SECURITY_POLICY,
            actorId = attempt.actorId,
            organizationId = attempt.organizationId,
            requestId = requestId,
            reason = exception.message,
        )
    }
}
