package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.RecordPreservationException
import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditLegalHoldCreateRequestDto
import com.docuhyphen.app.api.resource.audit.handler.AuditLegalHoldRequestHandler
import com.docuhyphen.app.api.resource.audit.operations.AuditOrganizationLegalHoldResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.resource.recordpreservation.RecordPreservationHttp
import com.docuhyphen.app.api.service.audit.AuditLegalHoldAccessService
import com.docuhyphen.app.api.service.audit.AuditLegalHoldNotFoundException
import com.docuhyphen.app.api.service.audit.AuditLegalHoldService
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
class AuditOrganizationLegalHoldResource @Inject constructor(
    private val auditLegalHoldAccessService: AuditLegalHoldAccessService,
    private val auditLegalHoldService: AuditLegalHoldService,
) : AuditOrganizationLegalHoldResourceOperations
{
    private val legalHolds = AuditLegalHoldRequestHandler(auditLegalHoldService)

    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationLegalHoldResource::class.java)
        private const val SUBSCRIPTION_REFUSAL_MESSAGE = "Organization audit legal-hold change refused by subscription policy"
        private const val LEGAL_HOLD_CONFLICT_MESSAGE = "Organization audit legal-hold conflict refused"
    }

    override fun placeOrganizationLegalHold(
        organizationId: String,
        body: AuditLegalHoldCreateRequestDto,
    ): Response
    {
        return try
        {
            val access = auditLegalHoldAccessService.organization(
                UUID.fromString(organizationId),
                Action.AUDIT_LEGAL_HOLD_MANAGE,
            )
            legalHolds.place(access.organizationId, access.actor.principal.id, body)
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

                is AuditLegalHoldNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is RecordPreservationException ->
                    RecordPreservationHttp.refused(logger, LEGAL_HOLD_CONFLICT_MESSAGE, exception)

                else ->
                {
                    logger.error("Error placing organization audit legal hold", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listOrganizationLegalHolds(organizationId: String): Response
    {
        return try
        {
            val access = auditLegalHoldAccessService.organization(UUID.fromString(organizationId), Action.ORG_READ_AUDIT)
            legalHolds.list(access.organizationId)
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

                is AuditLegalHoldNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is RecordPreservationException ->
                    RecordPreservationHttp.refused(logger, LEGAL_HOLD_CONFLICT_MESSAGE, exception)

                else ->
                {
                    logger.error("Error listing organization audit legal holds", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun releaseOrganizationLegalHold(
        organizationId: String,
        holdId: String,
    ): Response
    {
        return try
        {
            val access = auditLegalHoldAccessService.organization(
                UUID.fromString(organizationId),
                Action.AUDIT_LEGAL_HOLD_MANAGE,
            )
            legalHolds.release(access.organizationId, UUID.fromString(holdId), access.actor.principal.id)
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

                is AuditLegalHoldNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                is RecordPreservationException ->
                    RecordPreservationHttp.refused(logger, LEGAL_HOLD_CONFLICT_MESSAGE, exception)

                else ->
                {
                    logger.error("Error releasing organization audit legal hold", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }
}
