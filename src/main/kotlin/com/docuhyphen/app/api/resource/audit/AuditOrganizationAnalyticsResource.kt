package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditGovernanceDtoMapper
import com.docuhyphen.app.api.resource.audit.operations.AuditOrganizationAnalyticsResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditAccessAuthorizer
import com.docuhyphen.app.api.service.audit.AuditAnalyticsReconciliationService
import com.docuhyphen.app.api.service.auth.authz.Action
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

class AuditOrganizationAnalyticsResource @Inject constructor(
    private val auditAccessAuthorizer: AuditAccessAuthorizer,
    private val auditAnalyticsReconciliationService: AuditAnalyticsReconciliationService,
) : AuditOrganizationAnalyticsResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationAnalyticsResource::class.java)
    }

    override fun getOrganizationAnalyticsReconciliation(organizationId: String): Response
    {
        return try
        {
            val access = auditAccessAuthorizer.authorizeOrganization(
                UUID.fromString(organizationId),
                Action.AUDIT_INTEGRITY_VERIFY,
            )
            val report = auditAnalyticsReconciliationService.reconcile(
                access.organizationId,
                platformOnly = false,
                requestedByUserId = access.actor.principal.id,
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
                    logger.error("Error reconciling organization audit analytics", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }
}
