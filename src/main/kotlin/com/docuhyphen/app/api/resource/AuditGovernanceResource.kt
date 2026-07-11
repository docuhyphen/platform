package com.docuhyphen.app.api.resource

import com.docuhyphen.app.api.model.dto.AuditGovernanceDtoMapper
import com.docuhyphen.app.api.model.dto.AuditLegalHoldCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditRetentionPolicyUpdateRequestDto
import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditAnalyticsReconciliationService
import com.docuhyphen.app.api.service.audit.AuditLegalHoldService
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
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

/**
 * Thin REST adapter for platform-scope retention, legal hold, analytics, and assurance operations. All business logic lives in
 * [AuditRetentionPolicyService]/[AuditLegalHoldService]/[AuditAnalyticsReconciliationService];
 * this class only validates input, authorizes, and maps results. The organization-scope routes
 * live in [AuditOrganizationRetentionPolicyResource]/[AuditOrganizationLegalHoldResource]/
 * [AuditOrganizationAnalyticsResource] instead of here, so that no two resource classes share a
 * leading path-param segment with different literal suffixes (see
 * AuditOrganizationEventsResource for why that matters).
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AuditGovernanceResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditRetentionPolicyService: AuditRetentionPolicyService,
    private val auditLegalHoldService: AuditLegalHoldService,
    private val auditAnalyticsReconciliationService: AuditAnalyticsReconciliationService,
)
{
    @GET
    @Path("/platform/audit-retention-policies")
    fun listPlatformRetentionPolicies(): Response = withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
        Response.ok(auditRetentionPolicyService.listEffectivePolicies(PLATFORM_POLICY_SCOPE).map(AuditGovernanceDtoMapper::toDto)).build()
    }

    @PUT
    @Path("/platform/audit-retention-policies/{category}")
    fun upsertPlatformRetentionPolicy(
        @PathParam("category") category: String,
        body: AuditRetentionPolicyUpdateRequestDto,
    ): Response = withAuthorizedPlatform(Action.AUDIT_RETENTION_MANAGE) { principal ->
        val spec = auditRetentionPolicyService.upsertOverride(
            organizationId = PLATFORM_POLICY_SCOPE,
            category = parseCategory(category),
            ledgerRetentionDays = body.ledgerRetentionDays,
            archiveRetentionDays = body.archiveRetentionDays,
            legalHoldEligible = body.legalHoldEligible,
            identityTreatment = parseIdentityTreatment(body.identityTreatment),
            updatedByUserId = principal,
        )
        Response.ok(AuditGovernanceDtoMapper.toDto(spec)).build()
    }

    @POST
    @Path("/platform/audit-legal-holds")
    fun placePlatformLegalHold(body: AuditLegalHoldCreateRequestDto): Response =
        withAuthorizedPlatform(Action.AUDIT_LEGAL_HOLD_MANAGE) { principal ->
            val hold = auditLegalHoldService.placeHold(null, body.resourceType, body.resourceId, body.reason, body.caseReference, principal)
            Response.ok(AuditGovernanceDtoMapper.toDto(hold)).build()
        }

    @GET
    @Path("/platform/audit-legal-holds")
    fun listPlatformLegalHolds(): Response = withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
        Response.ok(auditLegalHoldService.listActiveHolds(null).map(AuditGovernanceDtoMapper::toDto)).build()
    }

    @POST
    @Path("/platform/audit-legal-holds/{holdId}/release")
    fun releasePlatformLegalHold(@PathParam("holdId") holdId: String): Response =
        withAuthorizedPlatform(Action.AUDIT_LEGAL_HOLD_MANAGE) { principal ->
            val hold = auditLegalHoldService.releaseHold(parseUuid(holdId), principal)
            requireOrgMatch(hold.organizationId, null)
            Response.ok(AuditGovernanceDtoMapper.toDto(hold)).build()
        }

    @GET
    @Path("/platform/audit-analytics/reconciliation")
    fun getPlatformAnalyticsReconciliation(): Response = withAuthorizedPlatform(Action.AUDIT_INTEGRITY_VERIFY) { principal ->
        val report = auditAnalyticsReconciliationService.reconcile(null, platformOnly = true, requestedByUserId = principal)
        Response.ok(AuditGovernanceDtoMapper.toDto(report)).build()
    }

    private fun parseCategory(raw: String): AuditCategory = runCatching { AuditCategory.valueOf(raw.uppercase()) }
        .getOrElse { throw IllegalArgumentException("Unknown audit category: $raw") }

    private fun parseIdentityTreatment(raw: String): AuditIdentityTreatment =
        runCatching { AuditIdentityTreatment.valueOf(raw.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unknown identity treatment: $raw") }

    private fun requireOrgMatch(holdOrganizationId: UUID?, expectedOrganizationId: UUID?)
    {
        if (holdOrganizationId != expectedOrganizationId)
        {
            throw IllegalArgumentException("Legal hold not found")
        }
    }

    private fun withAuthorizedPlatform(action: Action, block: (UUID) -> Response): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val decision = authorizationService.authorize(principal, action, platformRef(), context)
        if (decision is Decision.Deny)
        {
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        return runGuarded { block(principal.id) }
    }

    private fun runGuarded(block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: IllegalArgumentException)
        {
            Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(e.message)).build()
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)

    private fun platformRef(): ResourceRef = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))

    companion object
    {
        /** Platform-scope retention overrides are keyed under this fixed sentinel id, mirroring [platformRef]. */
        private val PLATFORM_POLICY_SCOPE: UUID = UUID(0, 0)
    }
}
