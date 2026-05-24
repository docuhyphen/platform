package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUserRole
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyResponse
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.AuthSessionPolicyService
import com.docuhyphen.app.api.service.auth.RevocationReasonCode
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
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
class OrganizationAuthSessionPolicyResource @Inject constructor(
    private val authTokenContext: AuthTokenContext,
    private val organizationRepository: OrganizationRepository,
    private val appUserService: AppUserService,
    private val authSessionPolicyService: AuthSessionPolicyService,
    private val authAuditService: AuthAuditService,
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

            if (currentUser.role != AppUserRole.ORG_ADMIN)
            {
                throw UnauthorizedException("User does not have permission to view auth session policy")
            }

            val orgId = runCatching { UUID.fromString(organizationId) }
                .getOrElse { throw IllegalArgumentException("Invalid organization ID format") }
            resolvedOrgId = orgId

            val actorOrg = organizationRepository.findByAppUserIdAndPersonId(currentUser.id, currentUser.person?.id!!)
                ?: throw UnauthorizedException("User is not associated with an organization")

            if (actorOrg.id != orgId)
            {
                throw UnauthorizedException("User cannot view another organization's auth session policy")
            }

            val policy = if (!appUserId.isNullOrBlank())
            {
                val targetUserId = runCatching { UUID.fromString(appUserId) }
                    .getOrElse { throw IllegalArgumentException("Invalid app user ID format") }
                val targetUser = appUserService.getById(targetUserId)
                    ?: throw IllegalArgumentException("App user not found")

                if (actorOrg.appUsers.none { it.id == targetUser.id })
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
                    refreshTokenExpiryDays = policy.refreshTokenExpiryDays,
                    maxSessionDurationHours = policy.maxSessionDurationHours,
                )
            ).build().also {
                authAuditService.emit(
                    action = "ORG_AUTH_SESSION_POLICY_VIEW",
                    outcome = "SUCCESS",
                    actorId = actorId,
                    organizationId = resolvedOrgId,
                    requestId = requestId,
                    reason = if (appUserId.isNullOrBlank()) "Viewed effective organization auth session policy" else "Viewed effective app-user auth session policy",
                    afterSnapshot = "organizationId=$organizationId;appUserId=${appUserId ?: ""};accessTokenExpiryMinutes=${policy.accessTokenExpiryMinutes};refreshTokenExpiryDays=${policy.refreshTokenExpiryDays};maxSessionDurationHours=${policy.maxSessionDurationHours}",
                )
            }
        }
        catch (exception: Exception)
        {
            logger.error("Error fetching effective auth session policy", exception)
            authAuditService.emit(
                action = "ORG_AUTH_SESSION_POLICY_VIEW",
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
}


