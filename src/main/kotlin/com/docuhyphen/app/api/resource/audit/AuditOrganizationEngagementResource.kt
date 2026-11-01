package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditEngagementCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditEngagementDtoMapper
import com.docuhyphen.app.api.resource.audit.operations.AuditOrganizationEngagementResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditAccessAuthorizer
import com.docuhyphen.app.api.service.audit.AuditEngagementNotFoundException
import com.docuhyphen.app.api.service.audit.AuditEngagementService
import com.docuhyphen.app.api.service.auth.authz.Action
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

class AuditOrganizationEngagementResource @Inject constructor(
    private val auditAccessAuthorizer: AuditAccessAuthorizer,
    private val auditEngagementService: AuditEngagementService,
) : AuditOrganizationEngagementResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationEngagementResource::class.java)
        private const val SUBSCRIPTION_REFUSAL_MESSAGE =
            "An organization audit engagement mutation was refused by the subscription plan check: plan={}"
    }

    override fun requestOrganizationEngagement(
        organizationId: String,
        body: AuditEngagementCreateRequestDto,
    ): Response
    {
        return try
        {
            val access = auditAccessAuthorizer.authorizeOrganization(
                UUID.fromString(organizationId),
                Action.AUDIT_ENGAGEMENT_MANAGE,
            )
            val engagement = auditEngagementService.requestEngagement(
                request = AuditEngagementDtoMapper.toRequest(access.organizationId, body),
                requestedByUserId = access.actor.principal.id,
            )
            Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception.denial.planCode)
                    throw exception
                }

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
                    logger.error("Error requesting organization audit engagement", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listOrganizationEngagements(organizationId: String): Response
    {
        return try
        {
            val access = auditAccessAuthorizer.authorizeOrganization(
                UUID.fromString(organizationId),
                Action.ORG_READ_AUDIT,
            )
            Response.ok(
                auditEngagementService.listForOrganization(access.organizationId).map(AuditEngagementDtoMapper::toDto)
            ).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception.denial.planCode)
                    throw exception
                }

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
                    logger.error("Error listing organization audit engagements", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun approveOrganizationEngagement(
        organizationId: String,
        engagementId: String,
    ): Response
    {
        return try
        {
            val access = auditAccessAuthorizer.authorizeOrganization(
                UUID.fromString(organizationId),
                Action.AUDIT_ENGAGEMENT_MANAGE,
            )
            val engagement = auditEngagementService.approveEngagement(
                UUID.fromString(engagementId),
                access.organizationId,
                access.actor.principal.id,
            )
            Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception.denial.planCode)
                    throw exception
                }

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
                    logger.error("Error approving organization audit engagement", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun revokeOrganizationEngagement(
        organizationId: String,
        engagementId: String,
    ): Response
    {
        return try
        {
            val access = auditAccessAuthorizer.authorizeOrganization(
                UUID.fromString(organizationId),
                Action.AUDIT_ENGAGEMENT_MANAGE,
            )
            val engagement = auditEngagementService.revokeEngagement(
                UUID.fromString(engagementId),
                access.organizationId,
                access.actor.principal.id,
            )
            Response.ok(AuditEngagementDtoMapper.toDto(engagement)).build()
        }
        catch (exception: Exception)
        {
            when (exception)
            {
                is WebApplicationException -> throw exception
                is SubscriptionDenialException ->
                {
                    logger.warn(SUBSCRIPTION_REFUSAL_MESSAGE, exception.denial.planCode)
                    throw exception
                }

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
                    logger.error("Error revoking organization audit engagement", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }
}
