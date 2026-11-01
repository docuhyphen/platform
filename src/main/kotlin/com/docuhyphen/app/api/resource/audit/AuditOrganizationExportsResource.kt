package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.AuditExportRequestDtoMapper
import com.docuhyphen.app.api.model.dto.AuditExportApprovalRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportDtoMapper
import com.docuhyphen.app.api.resource.audit.operations.AuditOrganizationExportsResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessException
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessService
import com.docuhyphen.app.api.service.audit.export.AuditExportNotFoundException
import com.docuhyphen.app.api.service.audit.export.AuditExportService
import com.docuhyphen.app.api.service.auth.authz.Action
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.inject.Inject
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

class AuditOrganizationExportsResource @Inject constructor(
    private val auditExportAccessService: AuditExportAccessService,
    private val auditExportService: AuditExportService,
) : AuditOrganizationExportsResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationExportsResource::class.java)
        private const val SUBSCRIPTION_REFUSAL_MESSAGE = "Organization audit-export request refused by subscription policy"
    }

    override fun requestOrganizationExport(
        organizationId: String,
        body: AuditExportCreateRequestDto,
    ): Response
    {
        return try
        {
            val access = auditExportAccessService.organization(
                UUID.fromString(organizationId),
                Action.ORG_REQUEST_AUDIT_EXPORT,
            )
            val export = auditExportService.requestExport(
                AuditExportRequestDtoMapper.toRequest(access.organizationId, body),
                access.actor,
            )
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error requesting organization audit export", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listOrganizationExports(organizationId: String): Response
    {
        return try
        {
            val access = auditExportAccessService.organization(UUID.fromString(organizationId), Action.ORG_READ_AUDIT)
            val exports = auditExportService.listForOrganization(access.organizationId, platformOnly = false)
            Response.ok(exports.map(AuditExportDtoMapper::toDto)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing organization audit exports", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun getOrganizationExport(
        organizationId: String,
        exportId: String,
    ): Response
    {
        return try
        {
            val access = auditExportAccessService.organization(UUID.fromString(organizationId), Action.ORG_READ_AUDIT)
            val export = auditExportAccessService.organizationExport(UUID.fromString(exportId), access.organizationId)
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error reading organization audit export", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun approveOrganizationExport(
        organizationId: String,
        exportId: String,
        body: AuditExportApprovalRequestDto?,
    ): Response
    {
        return try
        {
            val access = auditExportAccessService.organization(
                UUID.fromString(organizationId),
                Action.AUDIT_EXPORT_APPROVE,
            )
            val id = UUID.fromString(exportId)
            auditExportAccessService.organizationExport(id, access.organizationId)
            val export = auditExportService.approveExport(id, access.actor.principal.id, body?.note)
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error approving organization audit export", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listOrganizationExportApprovals(
        organizationId: String,
        exportId: String,
    ): Response
    {
        return try
        {
            val access = auditExportAccessService.organization(UUID.fromString(organizationId), Action.ORG_READ_AUDIT)
            val id = UUID.fromString(exportId)
            auditExportAccessService.organizationExport(id, access.organizationId)
            Response.ok(auditExportService.listApprovals(id).map(AuditExportDtoMapper::toApprovalDto)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing organization audit export approvals", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun downloadOrganizationExport(
        organizationId: String,
        exportId: String,
    ): Response
    {
        return try
        {
            val access = auditExportAccessService.organization(
                UUID.fromString(organizationId),
                Action.ORG_REQUEST_AUDIT_EXPORT,
            )
            val id = UUID.fromString(exportId)
            auditExportAccessService.organizationExport(id, access.organizationId)
            val bytes = auditExportService.downloadBundle(id, access.actor)
            Response.ok(bytes)
                .header("Content-Disposition", "attachment; filename=\"audit-export-$exportId.zip\"")
                .header("Content-Length", bytes.size)
                .build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error downloading organization audit export", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }
}
