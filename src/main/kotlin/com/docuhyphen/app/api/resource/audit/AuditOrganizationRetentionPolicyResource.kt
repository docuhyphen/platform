package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditGovernanceDtoMapper
import com.docuhyphen.app.api.model.dto.AuditRetentionPolicyUpdateRequestDto
import com.docuhyphen.app.api.resource.audit.operations.AuditOrganizationRetentionPolicyResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditAccessAuthorizer
import com.docuhyphen.app.api.service.audit.AuditRetentionPolicyService
import com.docuhyphen.app.api.service.auth.authz.Action
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

class AuditOrganizationRetentionPolicyResource @Inject constructor(
    private val auditAccessAuthorizer: AuditAccessAuthorizer,
    private val auditRetentionPolicyService: AuditRetentionPolicyService,
) : AuditOrganizationRetentionPolicyResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationRetentionPolicyResource::class.java)
        private const val SUBSCRIPTION_REFUSAL_MESSAGE = "Organization audit-retention change refused by subscription policy"
    }

    override fun listOrganizationRetentionPolicies(organizationId: String): Response
    {
        return try
        {
            val access = auditAccessAuthorizer.authorizeOrganization(UUID.fromString(organizationId), Action.ORG_READ_AUDIT)
            Response.ok(
                auditRetentionPolicyService.listEffectivePolicies(access.organizationId).map(AuditGovernanceDtoMapper::toDto)
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing organization audit retention policies", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun upsertOrganizationRetentionPolicy(
        organizationId: String,
        category: String,
        body: AuditRetentionPolicyUpdateRequestDto,
    ): Response
    {
        return try
        {
            val access = auditAccessAuthorizer.authorizeOrganization(
                UUID.fromString(organizationId),
                Action.AUDIT_RETENTION_MANAGE,
            )
            val spec = auditRetentionPolicyService.upsertOverride(
                organizationId = access.organizationId,
                category = parseAuditCategory(category),
                ledgerRetentionDays = body.ledgerRetentionDays,
                archiveRetentionDays = body.archiveRetentionDays,
                legalHoldEligible = body.legalHoldEligible,
                identityTreatment = parseAuditIdentityTreatment(body.identityTreatment),
                updatedByUserId = access.actor.principal.id,
            )
            Response.ok(AuditGovernanceDtoMapper.toDto(spec)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception)
                    throw exception
                }

                is UnauthorizedException ->
                    Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError(exception.message)).build()

                is ForbiddenException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error updating organization audit retention policy", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }
}
