package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditEngagementCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditEngagementDtoMapper
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditEngagementNotFoundException
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.UUID

/** Given its own class-level path; see AuditOrganizationEventsResource for why this class is not merged with others. */
@Path("/organizations/{organizationId}/audit-engagements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditOrganizationEngagementResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditEngagementService: AuditEngagementService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationEngagementResource::class.java)
    }

    @POST
    fun requestOrganizationEngagement(
        @PathParam("organizationId") organizationId: String,
        body: AuditEngagementCreateRequestDto,
    ): Response = withAuthorizedOrg(organizationId, Action.AUDIT_ENGAGEMENT_MANAGE) { principal, orgId ->
        val engagement = auditEngagementService.requestEngagement(
            request = AuditEngagementDtoMapper.toRequest(orgId, body),
            requestedByUserId = principal,
        )
        Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
    }

    @GET
    fun listOrganizationEngagements(@PathParam("organizationId") organizationId: String): Response =
        withAuthorizedOrg(organizationId, Action.ORG_READ_AUDIT) { _, orgId ->
            Response.ok(auditEngagementService.listForOrganization(orgId).map(AuditEngagementDtoMapper::toDto)).build()
        }

    @POST
    @Path("/{engagementId}/approve")
    fun approveOrganizationEngagement(
        @PathParam("organizationId") organizationId: String,
        @PathParam("engagementId") engagementId: String,
    ): Response = withAuthorizedOrg(organizationId, Action.AUDIT_ENGAGEMENT_MANAGE) { principal, orgId ->
        val engagement = auditEngagementService.approveEngagement(parseUuid(engagementId), orgId, principal)
        Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
    }

    @POST
    @Path("/{engagementId}/revoke")
    fun revokeOrganizationEngagement(
        @PathParam("organizationId") organizationId: String,
        @PathParam("engagementId") engagementId: String,
    ): Response = withAuthorizedOrg(organizationId, Action.AUDIT_ENGAGEMENT_MANAGE) { principal, orgId ->
        val engagement = auditEngagementService.revokeEngagement(parseUuid(engagementId), orgId, principal)
        Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
    }

    private fun withAuthorizedOrg(
        organizationId: String,
        action: Action,
        block: (UUID, UUID) -> Response,
    ): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val orgId = parseUuid(organizationId)
        val decision = authorizationService.authorize(principal, action, ResourceRef.organization(orgId), context)
        if (decision is Decision.Deny)
        {
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        return runGuarded { block(principal.id, orgId) }
    }

    private fun runGuarded(block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn("An organization audit engagement mutation was refused by the subscription plan check: plan={}", e.denial.planCode)
            throw e
        }
        catch (e: IllegalArgumentException)
        {
            val status = if (e is AuditEngagementNotFoundException) Response.Status.NOT_FOUND else Response.Status.BAD_REQUEST
            Response.status(status).entity(ResponseError(e.message)).build()
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)
}
