package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.dto.AuditProjectionDtoMapper
import com.docuhyphen.app.api.resource.audit.operations.AuditProjectionResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditProjectionAccessDeniedException
import com.docuhyphen.app.api.service.audit.AuditProjectionAccessService
import com.docuhyphen.app.api.service.audit.AuditProjectionNotFoundException
import com.docuhyphen.app.api.service.audit.AuditSearchProjectionService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory

class AuditProjectionResource @Inject constructor(
    private val auditProjectionAccessService: AuditProjectionAccessService,
    private val auditSearchProjectionService: AuditSearchProjectionService,
) : AuditProjectionResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditProjectionResource::class.java)
    }

    override fun listMySecurityEvents(
        cursorOccurredAt: String?,
        cursorEventId: String?,
        limit: Int,
    ): Response
    {
        return try
        {
            val page = auditSearchProjectionService.listPersonalSecurityEvents(
                actor = auditProjectionAccessService.personalActor(),
                cursor = parseAuditCursor(cursorOccurredAt, cursorEventId),
                limit = clampAuditPageLimit(limit),
            )
            Response.ok(AuditProjectionDtoMapper.toPageDto(page)).build()
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

                is AuditProjectionNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditProjectionAccessDeniedException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing personal security audit events", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listPlatformAuditEvents(
        categories: String?,
        cursorOccurredAt: String?,
        cursorEventId: String?,
        occurredAfter: String?,
        occurredBefore: String?,
        limit: Int,
    ): Response
    {
        return try
        {
            val actor = auditProjectionAccessService.platformRead()
            val page = auditSearchProjectionService.listPlatformEvents(
                actor = actor,
                categories = parseAuditCategories(categories),
                cursor = parseAuditCursor(cursorOccurredAt, cursorEventId),
                limit = clampAuditPageLimit(limit),
                occurredAfter = occurredAfter?.let { parseAuditInstant(it, "occurredAfter") },
                occurredBefore = occurredBefore?.let { parseAuditInstant(it, "occurredBefore") },
            )
            Response.ok(AuditProjectionDtoMapper.toPageDto(page)).build()
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

                is AuditProjectionNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditProjectionAccessDeniedException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing platform audit events", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }
}
