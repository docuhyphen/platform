package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.AuditExportRequestDtoMapper
import com.docuhyphen.app.api.model.dto.AuditExportApprovalRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportDtoMapper
import com.docuhyphen.app.api.resource.audit.operations.AuditExportResourceOperations
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessException
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessService
import com.docuhyphen.app.api.service.audit.export.AuditExportNotFoundException
import com.docuhyphen.app.api.service.audit.export.AuditExportService
import com.docuhyphen.app.api.service.audit.export.AuditIntegrityService
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
class AuditExportResource @Inject constructor(
    private val auditExportAccessService: AuditExportAccessService,
    private val auditExportService: AuditExportService,
    private val auditIntegrityService: AuditIntegrityService,
) : AuditExportResourceOperations
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditExportResource::class.java)
    }

    override fun requestPlatformExport(body: AuditExportCreateRequestDto): Response
    {
        return try
        {
            val actor = auditExportAccessService.platform(Action.APP_REQUEST_AUDIT_EXPORT)
            val export = auditExportService.requestExport(AuditExportRequestDtoMapper.toRequest(null, body), actor)
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error requesting platform audit export", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listPlatformExports(): Response
    {
        return try
        {
            auditExportAccessService.platform(Action.APP_READ_AUDIT)
            val exports = auditExportService.listForOrganization(null, platformOnly = true)
            Response.ok(exports.map(AuditExportDtoMapper::toDto)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing platform audit exports", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun getPlatformExport(exportId: String): Response
    {
        return try
        {
            auditExportAccessService.platform(Action.APP_READ_AUDIT)
            val export = auditExportService.getPlatformExport(UUID.fromString(exportId))
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error reading platform audit export", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun approvePlatformExport(exportId: String, body: AuditExportApprovalRequestDto?): Response
    {
        return try
        {
            val actor = auditExportAccessService.platform(Action.AUDIT_EXPORT_APPROVE)
            val id = UUID.fromString(exportId)
            auditExportService.getPlatformExport(id)
            val export = auditExportService.approveExport(id, actor.principal.id, body?.note)
            Response.ok(AuditExportDtoMapper.toDto(export)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error approving platform audit export", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun listPlatformExportApprovals(exportId: String): Response
    {
        return try
        {
            auditExportAccessService.platform(Action.APP_READ_AUDIT)
            val id = UUID.fromString(exportId)
            auditExportService.getPlatformExport(id)
            Response.ok(auditExportService.listApprovals(id).map(AuditExportDtoMapper::toApprovalDto)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error listing platform audit export approvals", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun downloadPlatformExport(exportId: String): Response
    {
        return try
        {
            val actor = auditExportAccessService.platform(Action.APP_REQUEST_AUDIT_EXPORT)
            val id = UUID.fromString(exportId)
            auditExportService.getPlatformExport(id)
            val bytes = auditExportService.downloadBundle(id, actor)
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
                is SubscriptionDenialException -> throw exception
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
                    logger.error("Error downloading platform audit export", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }

    override fun getPlatformIntegrity(): Response
    {
        return try
        {
            val actor = auditExportAccessService.platform(Action.APP_READ_AUDIT)
            val report = auditIntegrityService.checkOrganization(
                null,
                platformOnly = true,
                requestedByUserId = actor.principal.id,
            )
            Response.ok(AuditExportDtoMapper.toIntegrityDto(report)).build()
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

                is AuditExportNotFoundException ->
                    Response.status(Response.Status.NOT_FOUND).entity(ResponseError(exception.message)).build()

                is AuditExportAccessException ->
                    Response.status(Response.Status.FORBIDDEN).entity(ResponseError(exception.message)).build()

                is IllegalArgumentException ->
                    Response.status(Response.Status.BAD_REQUEST).entity(ResponseError(exception.message)).build()

                else ->
                {
                    logger.error("Error checking platform audit integrity", exception)
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ResponseError("Request failed"))
                        .build()
                }
            }
        }
    }
}
