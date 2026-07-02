package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyEffectiveDto
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyGuardrailsDto
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyIdpDto
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyResponse
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicySettingsResponse
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyUpdateRequest
import com.docuhyphen.app.api.resource.model.OrganizationIdpConfigResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthSessionPolicyService
import com.docuhyphen.app.api.service.auth.OrganizationIdentityProviderConfigService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.core.Response.Status.FORBIDDEN
import jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR
import org.slf4j.LoggerFactory
import java.util.UUID

@Path("organizations/{organizationId}/auth/session-policy")
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
class OrganizationAuthSessionPolicyResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val appUserService: AppUserService,
    private val authSessionPolicyService: AuthSessionPolicyService,
    private val authAuditService: AuthAuditService,
    private val organizationIdentityProviderConfigService: OrganizationIdentityProviderConfigService,
    private val configurationService: ConfigurationService,
    private val userRoleService: com.docuhyphen.app.api.service.auth.UserRoleService,
    private val organizationMembershipService: com.docuhyphen.app.api.service.organization.OrganizationMembershipService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationAuthSessionPolicyResource::class.java)
    }

    @GET
    @Path("/effective")
    fun getEffectivePolicy(
        @PathParam("organizationId") organizationId: String,
        @QueryParam("appUserId") appUserId: String?,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        var actorId: UUID? = null
        var resolvedOrgId: UUID? = null

        return try
        {
            val currentUser = authTokenContext.authToken.appUser
                ?: throw UnauthorizedException("User is not authenticated")
            actorId = currentUser.id

            val orgId = runCatching { UUID.fromString(organizationId) }
                .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }
            resolvedOrgId = orgId

            if (!userRoleService.isOrgAdminIn(currentUser.id, orgId))
            {
                throw UnauthorizedException("User does not have permission to view auth session policy")
            }

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

            Response.ok(
                OrganizationAuthSessionPolicyResponse(
                    organizationId = orgId.toString(),
                    appUserId = appUserId,
                    accessTokenExpiryMinutes = policy.accessTokenExpiryMinutes,
                    refreshTokenExpiryMinutes = policy.refreshTokenExpiryMinutes,
                    maxSessionDurationHours = policy.maxSessionDurationHours,
                    idleTimeoutMinutes = policy.idleTimeoutMinutes,
                )
            ).build().also {
                authAuditService.emit(
                    action = "ORG_AUTH_EXCHANGE_POLICY_VIEW",
                    outcome = "SUCCESS",
                    actorId = actorId,
                    organizationId = resolvedOrgId,
                    requestId = requestId,
                    reason = if (appUserId.isNullOrBlank()) "Viewed effective organization auth session policy" else "Viewed effective app-user auth session policy",
                    afterSnapshot = "organizationId=$organizationId;appUserId=${appUserId ?: ""};accessTokenExpiryMinutes=${policy.accessTokenExpiryMinutes};refreshTokenExpiryMinutes=${policy.refreshTokenExpiryMinutes};maxSessionDurationHours=${policy.maxSessionDurationHours};idleTimeoutMinutes=${policy.idleTimeoutMinutes}",
                )
            }
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching effective auth session policy", exception)
            authAuditService.emit(
                action = "ORG_AUTH_EXCHANGE_POLICY_VIEW",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                actorId = actorId,
                organizationId = resolvedOrgId,
                requestId = requestId,
                reason = exception.message,
            )
            when (exception)
            {
                is UnauthorizedException -> Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                is IllegalArgumentException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                else -> Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred")).build()
            }
        }
    }

    /**
     * Returns the per-IdP session policy rows plus the resolved effective policy
     * and platform guardrails. This backs the "Auth session policy" section in the
     * organization settings UI; no separate admin page is introduced.
     */
    @GET
    fun getSettings(
        @PathParam("organizationId") organizationId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
    ): Response
    {
        var actorId: UUID? = null
        var resolvedOrgId: UUID? = null

        return try
        {
            val currentUser = authTokenContext.authToken.appUser
                ?: throw UnauthorizedException("User is not authenticated")
            actorId = currentUser.id

            val orgId = runCatching { UUID.fromString(organizationId) }
                .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }
            resolvedOrgId = orgId

            if (!userRoleService.isOrgAdminIn(currentUser.id, orgId))
            {
                throw UnauthorizedException("User does not have permission to view auth session policy")
            }

            // Auto-provision the INTERNAL IdP row so org admins can always tune the
            // built-in identity provider's session policy without going through the
            // full IdP-creation flow (which requires external OAuth credentials).
            organizationIdentityProviderConfigService.ensureInternalConfig(orgId)

            val configs = organizationIdentityProviderConfigService.listForPolicyView(organizationId)
            val policy = authSessionPolicyService.resolveForOrganization(orgId)

            val response = OrganizationAuthSessionPolicySettingsResponse(
                organizationId = orgId.toString(),
                effective = OrganizationAuthSessionPolicyEffectiveDto(
                    accessTokenExpiryMinutes = policy.accessTokenExpiryMinutes,
                    refreshTokenExpiryMinutes = policy.refreshTokenExpiryMinutes,
                    maxSessionDurationHours = policy.maxSessionDurationHours,
                    idleTimeoutMinutes = policy.idleTimeoutMinutes,
                ),
                guardrails = OrganizationAuthSessionPolicyGuardrailsDto(
                    minAccessTokenExpiryMinutes = configurationService.getMinAccessTokenExpiryMinutes(),
                    maxAccessTokenExpiryMinutes = configurationService.getMaxAccessTokenExpiryMinutes(),
                    minRefreshTokenExpiryMinutes = configurationService.getMinRefreshTokenExpiryMinutes(),
                    maxRefreshTokenExpiryMinutes = configurationService.getMaxRefreshTokenExpiryMinutes(),
                    minSessionMaxDurationHours = configurationService.getMinSessionMaxDurationHours(),
                    maxSessionMaxDurationHours = configurationService.getMaxSessionMaxDurationHours(),
                    minIdleTimeoutMinutes = configurationService.getMinIdleTimeoutMinutes(),
                    maxIdleTimeoutMinutes = configurationService.getMaxIdleTimeoutMinutes(),
                ),
                idpConfigs = configs.map { c ->
                    OrganizationAuthSessionPolicyIdpDto(
                        configId = c.id.toString(),
                        provider = c.provider,
                        isActive = c.isActive,
                        accessTokenExpiryMinutes = c.accessTokenExpiryMinutes,
                        refreshTokenExpiryMinutes = c.refreshTokenExpiryMinutes,
                        maxSessionDurationHours = c.maxSessionDurationHours,
                        idleTimeoutMinutes = c.idleTimeoutMinutes,
                    )
                },
                hasActiveIdpConfig = configs.any { it.isActive },
            )

            authAuditService.emit(
                action = "ORG_AUTH_EXCHANGE_POLICY_VIEW",
                outcome = "SUCCESS",
                actorId = actorId,
                organizationId = resolvedOrgId,
                requestId = requestId,
                reason = "Viewed organization auth session policy settings",
            )

            Response.ok(response).build()
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching auth session policy settings", exception)
            authAuditService.emit(
                action = "ORG_AUTH_EXCHANGE_POLICY_VIEW",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                actorId = actorId,
                organizationId = resolvedOrgId,
                requestId = requestId,
                reason = exception.message,
            )
            when (exception)
            {
                is UnauthorizedException -> Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                is IllegalArgumentException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                else -> Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred")).build()
            }
        }
    }

    /**
     * Update the session-policy fields of a single IdP config that belongs to
     * the target organization. Only access/refresh/session duration are mutated;
     * provider, secrets, issuer, and claim configuration are left untouched.
     */
    @PUT
    @Path("/{configId}")
    fun updateIdpSessionPolicy(
        @PathParam("organizationId") organizationId: String,
        @PathParam("configId") configId: String,
        @HeaderParam("X-Request-Id") requestId: String?,
        payload: OrganizationAuthSessionPolicyUpdateRequest,
    ): Response
    {
        return try
        {
            val updated = organizationIdentityProviderConfigService.updateSessionPolicyFields(
                organizationId = organizationId,
                configId = configId,
                request = payload,
                adminApprovalContext = AdminApprovalContext(requestId = requestId),
            )

            Response.ok(
                OrganizationIdpConfigResponse(
                    id = updated.id.toString(),
                    organizationId = updated.organization?.id?.toString().orEmpty(),
                    provider = updated.provider,
                    clientId = updated.clientId,
                    clientSecretRef = updated.clientSecretRef,
                    tenantId = updated.tenantId,
                    scopes = splitCsv(updated.scopes),
                    isActive = updated.isActive,
                    accessTokenExpiryMinutes = updated.accessTokenExpiryMinutes,
                    refreshTokenExpiryMinutes = updated.refreshTokenExpiryMinutes,
                    maxSessionDurationHours = updated.maxSessionDurationHours,
                    idleTimeoutMinutes = updated.idleTimeoutMinutes,
                    oidcIssuer = updated.oidcIssuer,
                    allowedAudiences = splitCsv(updated.allowedAudiences),
                    allowedAlgs = splitCsv(updated.allowedAlgs),
                    requiredClaims = splitCsv(updated.requiredClaims),
                    createdDate = updated.createdDate.toString(),
                    updatedDate = updated.updatedDate.toString(),
                )
            ).build()
        }
        catch (exception: Exception)
        {
            if (exception is jakarta.ws.rs.WebApplicationException) throw exception
            logger.error("Error updating IdP auth session policy", exception)
            when (exception)
            {
                is UnauthorizedException -> Response.status(FORBIDDEN).entity(ResponseError(exception.message)).build()
                is IllegalArgumentException -> Response.status(BAD_REQUEST).entity(ResponseError(exception.message)).build()
                else -> Response.status(INTERNAL_SERVER_ERROR).entity(ResponseError("An unexpected error occurred")).build()
            }
        }
    }

    private fun splitCsv(value: String?): List<String>
    {
        return value
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }
}


