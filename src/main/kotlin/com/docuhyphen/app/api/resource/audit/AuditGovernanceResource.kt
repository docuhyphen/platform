package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.*
import com.docuhyphen.app.api.model.entity.AuditIdentityTreatment
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.util.*

@jakarta.enterprise.context.RequestScoped
class AuditGovernanceResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditRetentionPolicyService: AuditRetentionPolicyService,
    private val auditLegalHoldService: AuditLegalHoldService,
    private val auditAnalyticsReconciliationService: AuditAnalyticsReconciliationService,
    private val auditEngagementService: AuditEngagementService,
) : com.docuhyphen.app.api.resource.audit.operations.AuditGovernanceResourceOperations {
    private val legalHolds =
        com.docuhyphen.app.api.resource.audit.handler.AuditLegalHoldRequestHandler(auditLegalHoldService)

    override fun listPlatformRetentionPolicies(): Response {
        return try {
            withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
                Response.ok(
                    auditRetentionPolicyService.listEffectivePolicies(PLATFORM_POLICY_SCOPE)
                        .map(AuditGovernanceDtoMapper::toDto)
                ).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource listPlatformRetentionPolicies failed", exception)
        }
    }

    override fun upsertPlatformRetentionPolicy(
        category: String,
        body: AuditRetentionPolicyUpdateRequestDto,
    ): Response {
        return try {
            withAuthorizedPlatform(Action.AUDIT_RETENTION_MANAGE) { principal ->
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
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource upsertPlatformRetentionPolicy failed", exception)
        }
    }

    override fun placePlatformLegalHold(body: AuditLegalHoldCreateRequestDto): Response {
        return try {
            withAuthorizedPlatform(Action.AUDIT_LEGAL_HOLD_MANAGE) { principal ->
                legalHolds.place(null, principal, body)
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource placePlatformLegalHold failed", exception)
        }
    }

    override fun listPlatformLegalHolds(): Response {
        return try {
            withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
                legalHolds.list(null)
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource listPlatformLegalHolds failed", exception)
        }
    }

    override fun releasePlatformLegalHold(holdId: String): Response {
        return try {
            withAuthorizedPlatform(Action.AUDIT_LEGAL_HOLD_MANAGE) { principal ->
                legalHolds.release(null, parseUuid(holdId), principal)
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource releasePlatformLegalHold failed", exception)
        }
    }

    override fun getPlatformAnalyticsReconciliation(): Response {
        return try {
            withAuthorizedPlatform(Action.AUDIT_INTEGRITY_VERIFY) { principal ->
                val report =
                    auditAnalyticsReconciliationService.reconcile(
                        null,
                        platformOnly = true,
                        requestedByUserId = principal
                    )
                Response.ok(AuditGovernanceDtoMapper.toDto(report)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource getPlatformAnalyticsReconciliation failed", exception)
        }
    }

    override fun requestPlatformEngagement(body: AuditEngagementCreateRequestDto): Response {
        return try {
            withAuthorizedPlatform(Action.AUDIT_ENGAGEMENT_MANAGE) { principal ->
                val engagement = auditEngagementService.requestEngagement(
                    request = AuditEngagementDtoMapper.toRequest(null, body),
                    requestedByUserId = principal,
                )
                Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource requestPlatformEngagement failed", exception)
        }
    }

    override fun listPlatformEngagements(): Response {
        return try {
            withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
                Response.ok(auditEngagementService.listForOrganization(null).map(AuditEngagementDtoMapper::toDto))
                    .build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource listPlatformEngagements failed", exception)
        }
    }

    override fun approvePlatformEngagement(engagementId: String): Response {
        return try {
            withAuthorizedPlatform(Action.AUDIT_ENGAGEMENT_MANAGE) { principal ->
                val engagement = auditEngagementService.approveEngagement(parseUuid(engagementId), null, principal)
                Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource approvePlatformEngagement failed", exception)
        }
    }

    override fun revokePlatformEngagement(engagementId: String): Response {
        return try {
            withAuthorizedPlatform(Action.AUDIT_ENGAGEMENT_MANAGE) { principal ->
                val engagement = auditEngagementService.revokeEngagement(parseUuid(engagementId), null, principal)
                Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditGovernanceResource revokePlatformEngagement failed", exception)
        }
    }

    private fun parseCategory(raw: String): AuditCategory = runCatching { AuditCategory.valueOf(raw.uppercase()) }
        .getOrElse { throw IllegalArgumentException("Unknown audit category: $raw") }

    private fun parseIdentityTreatment(raw: String): AuditIdentityTreatment =
        runCatching { AuditIdentityTreatment.valueOf(raw.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Unknown identity treatment: $raw") }

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
            val status = if (e is AuditLegalHoldNotFoundException || e is AuditEngagementNotFoundException)
            {
                Response.Status.NOT_FOUND
            }
            else
            {
                Response.Status.BAD_REQUEST
            }
            Response.status(status).entity(ResponseError(e.message)).build()
        } catch (e: com.docuhyphen.app.api.exception.RecordPreservationException) {
            com.docuhyphen.app.api.resource.recordpreservation.RecordPreservationHttp.refused(
                logger, "Platform audit legal-hold conflict refused", e,
            )
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)

    private fun platformRef(): ResourceRef = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))

    companion object
    {
        private val logger = org.slf4j.LoggerFactory.getLogger(AuditGovernanceResource::class.java)
        private val PLATFORM_POLICY_SCOPE: UUID = UUID(0, 0)
    }

    private fun failedAuditRequest(message: String, exception: Exception): Response {
        if (exception is jakarta.ws.rs.WebApplicationException) throw exception
        if (exception is com.docuhyphen.app.api.exception.SubscriptionDenialException) throw exception
        logger.error(message, exception)
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
    }

}
