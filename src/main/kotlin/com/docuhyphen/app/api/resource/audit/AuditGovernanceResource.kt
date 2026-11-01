package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditEngagementCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditEngagementDtoMapper
import com.docuhyphen.app.api.model.dto.AuditGovernanceDtoMapper
import com.docuhyphen.app.api.model.dto.AuditLegalHoldCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditRetentionPolicyUpdateRequestDto
import com.docuhyphen.app.api.resource.audit.handler.AuditLegalHoldRequestHandler
import com.docuhyphen.app.api.resource.audit.operations.AuditGovernanceResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.recordpreservation.RecordPreservationHttp
import com.docuhyphen.app.api.service.audit.AuditAccessAuthorizer
import com.docuhyphen.app.api.service.audit.AuditAnalyticsReconciliationService
import com.docuhyphen.app.api.service.audit.AuditEngagementNotFoundException
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.audit.AuditLegalHoldNotFoundException
import com.docuhyphen.app.api.service.audit.AuditLegalHoldService
import com.docuhyphen.app.api.service.audit.AuditRetentionPolicyService
import com.docuhyphen.app.api.service.auth.authz.Action
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

@RequestScoped
class AuditGovernanceResource @Inject constructor(
    private val auditAccessAuthorizer: AuditAccessAuthorizer,
    private val auditRetentionPolicyService: AuditRetentionPolicyService,
    private val auditLegalHoldService: AuditLegalHoldService,
    private val auditAnalyticsReconciliationService: AuditAnalyticsReconciliationService,
    private val auditEngagementService: AuditEngagementService,
) : AuditGovernanceResourceOperations
{
    private val legalHolds = AuditLegalHoldRequestHandler(auditLegalHoldService)

    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditGovernanceResource::class.java)
        private val PLATFORM_POLICY_SCOPE: UUID = UUID(0, 0)
        private const val LEGAL_HOLD_CONFLICT_MESSAGE = "Platform audit legal-hold conflict refused"
    }

    override fun listPlatformRetentionPolicies(): Response
    {
        return try
        {
            auditAccessAuthorizer.authorizePlatform(Action.APP_READ_AUDIT)
            Response.ok(
                auditRetentionPolicyService.listEffectivePolicies(PLATFORM_POLICY_SCOPE).map(AuditGovernanceDtoMapper::toDto)
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing platform audit retention policies", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun upsertPlatformRetentionPolicy(
        category: String,
        body: AuditRetentionPolicyUpdateRequestDto,
    ): Response
    {
        return try
        {
            val actor = auditAccessAuthorizer.authorizePlatform(Action.AUDIT_RETENTION_MANAGE)
            val spec = auditRetentionPolicyService.upsertOverride(
                organizationId = PLATFORM_POLICY_SCOPE,
                category = parseAuditCategory(category),
                ledgerRetentionDays = body.ledgerRetentionDays,
                archiveRetentionDays = body.archiveRetentionDays,
                legalHoldEligible = body.legalHoldEligible,
                identityTreatment = parseAuditIdentityTreatment(body.identityTreatment),
                updatedByUserId = actor.principal.id,
            )
            Response.ok(AuditGovernanceDtoMapper.toDto(spec)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating platform audit retention policy", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun placePlatformLegalHold(body: AuditLegalHoldCreateRequestDto): Response
    {
        return try
        {
            val actor = auditAccessAuthorizer.authorizePlatform(Action.AUDIT_LEGAL_HOLD_MANAGE)
            legalHolds.place(null, actor.principal.id, body)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AuditLegalHoldNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is RecordPreservationException ->
                    RecordPreservationHttp.refused(logger, LEGAL_HOLD_CONFLICT_MESSAGE, exception)

                else ->
                {
                    logger.error("Error placing platform audit legal hold", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listPlatformLegalHolds(): Response
    {
        return try
        {
            auditAccessAuthorizer.authorizePlatform(Action.APP_READ_AUDIT)
            legalHolds.list(null)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AuditLegalHoldNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is RecordPreservationException ->
                    RecordPreservationHttp.refused(logger, LEGAL_HOLD_CONFLICT_MESSAGE, exception)

                else ->
                {
                    logger.error("Error listing platform audit legal holds", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun releasePlatformLegalHold(holdId: String): Response
    {
        return try
        {
            val actor = auditAccessAuthorizer.authorizePlatform(Action.AUDIT_LEGAL_HOLD_MANAGE)
            legalHolds.release(null, UUID.fromString(holdId), actor.principal.id)
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AuditLegalHoldNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is RecordPreservationException ->
                    RecordPreservationHttp.refused(logger, LEGAL_HOLD_CONFLICT_MESSAGE, exception)

                else ->
                {
                    logger.error("Error releasing platform audit legal hold", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun getPlatformAnalyticsReconciliation(): Response
    {
        return try
        {
            val actor = auditAccessAuthorizer.authorizePlatform(Action.AUDIT_INTEGRITY_VERIFY)
            val report = auditAnalyticsReconciliationService.reconcile(
                null,
                platformOnly = true,
                requestedByUserId = actor.principal.id,
            )
            Response.ok(AuditGovernanceDtoMapper.toDto(report)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error reconciling platform audit analytics", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun requestPlatformEngagement(body: AuditEngagementCreateRequestDto): Response
    {
        return try
        {
            val actor = auditAccessAuthorizer.authorizePlatform(Action.AUDIT_ENGAGEMENT_MANAGE)
            val engagement = auditEngagementService.requestEngagement(
                request = AuditEngagementDtoMapper.toRequest(null, body),
                requestedByUserId = actor.principal.id,
            )
            Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AuditEngagementNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error requesting platform audit engagement", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listPlatformEngagements(): Response
    {
        return try
        {
            auditAccessAuthorizer.authorizePlatform(Action.APP_READ_AUDIT)
            Response.ok(auditEngagementService.listForOrganization(null).map(AuditEngagementDtoMapper::toDto)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AuditEngagementNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing platform audit engagements", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun approvePlatformEngagement(engagementId: String): Response
    {
        return try
        {
            val actor = auditAccessAuthorizer.authorizePlatform(Action.AUDIT_ENGAGEMENT_MANAGE)
            val engagement = auditEngagementService.approveEngagement(UUID.fromString(engagementId), null, actor.principal.id)
            Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AuditEngagementNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error approving platform audit engagement", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun revokePlatformEngagement(engagementId: String): Response
    {
        return try
        {
            val actor = auditAccessAuthorizer.authorizePlatform(Action.AUDIT_ENGAGEMENT_MANAGE)
            val engagement = auditEngagementService.revokeEngagement(UUID.fromString(engagementId), null, actor.principal.id)
            Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException -> throw exception
                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is AuditEngagementNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error revoking platform audit engagement", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }
}
