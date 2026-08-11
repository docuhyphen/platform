package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditGovernanceDtoMapper
import com.docuhyphen.app.api.model.dto.AuditRetentionPolicyUpdateRequestDto
import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditRetentionPolicyService
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID
import org.slf4j.LoggerFactory

/** Given its own class-level path; see AuditOrganizationEventsResource for why this class is not merged with others. */
@Path("/organizations/{organizationId}/audit-retention-policies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditOrganizationRetentionPolicyResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditRetentionPolicyService: AuditRetentionPolicyService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationRetentionPolicyResource::class.java)
    }
    @GET
    fun listOrganizationRetentionPolicies(@PathParam("organizationId") organizationId: String): Response =
        withAuthorizedOrg(organizationId, Action.ORG_READ_AUDIT) { _, orgId ->
            Response.ok(auditRetentionPolicyService.listEffectivePolicies(orgId).map(AuditGovernanceDtoMapper::toDto)).build()
        }

    @PUT
    @Path("/{category}")
    fun upsertOrganizationRetentionPolicy(
        @PathParam("organizationId") organizationId: String,
        @PathParam("category") category: String,
        body: AuditRetentionPolicyUpdateRequestDto,
    ): Response = withAuthorizedOrg(organizationId, Action.AUDIT_RETENTION_MANAGE) { principal, orgId ->
        val spec = auditRetentionPolicyService.upsertOverride(
            organizationId = orgId,
            category = parseCategory(category),
            ledgerRetentionDays = body.ledgerRetentionDays,
            archiveRetentionDays = body.archiveRetentionDays,
            legalHoldEligible = body.legalHoldEligible,
            identityTreatment = parseIdentityTreatment(body.identityTreatment),
            updatedByUserId = principal,
        )
        Response.ok(AuditGovernanceDtoMapper.toDto(spec)).build()
    }

    private fun parseCategory(raw: String): AuditCategory = runCatching { AuditCategory.valueOf(raw.uppercase()) }
        .getOrElse { throw IllegalArgumentException("Unknown audit category: $raw") }

    private fun parseIdentityTreatment(raw: String): AuditIdentityTreatment =
        runCatching { AuditIdentityTreatment.valueOf(raw.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unknown identity treatment: $raw") }

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
            logger.warn("Organization audit-retention change refused by subscription policy", e)
            throw e
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)
}
