package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.model.audit.AuditAccessActor
import com.docuhyphen.app.api.model.audit.export.AuditExportRequest
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.AuditExportApprovalRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportCreateRequestDto
import com.docuhyphen.app.api.model.dto.AuditExportDtoMapper
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.catalog.AuditCategory
import com.docuhyphen.app.api.service.audit.export.AuditExportAccessException
import com.docuhyphen.app.api.service.audit.export.AuditExportNotFoundException
import com.docuhyphen.app.api.service.audit.export.AuditExportService
import com.docuhyphen.app.api.service.audit.export.AuditIntegrityService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.util.*

@jakarta.enterprise.context.RequestScoped
class AuditExportResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditExportService: AuditExportService,
    private val auditIntegrityService: AuditIntegrityService,
) : com.docuhyphen.app.api.resource.audit.operations.AuditExportResourceOperations {
    private val logger = org.slf4j.LoggerFactory.getLogger(AuditExportResource::class.java)

    override fun requestPlatformExport(body: AuditExportCreateRequestDto): Response {
        return try {
            withAuthorizedPlatform(Action.APP_REQUEST_AUDIT_EXPORT) { actor ->
                val export = auditExportService.requestExport(toRequest(null, body), actor)
                Response.ok(AuditExportDtoMapper.toDto(export)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditExportResource requestPlatformExport failed", exception)
        }
    }

    override fun listPlatformExports(): Response {
        return try {
            withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
                val exports = auditExportService.listForOrganization(null, platformOnly = true)
                Response.ok(exports.map(AuditExportDtoMapper::toDto)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditExportResource listPlatformExports failed", exception)
        }
    }

    override fun getPlatformExport(exportId: String): Response {
        return try {
            withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
                val export = auditExportService.getPlatformExport(parseUuid(exportId))
                Response.ok(AuditExportDtoMapper.toDto(export)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditExportResource getPlatformExport failed", exception)
        }
    }

    override fun approvePlatformExport(exportId: String, body: AuditExportApprovalRequestDto?): Response {
        return try {
            withAuthorizedPlatform(Action.AUDIT_EXPORT_APPROVE) { actor ->
                val id = parseUuid(exportId)
                auditExportService.getPlatformExport(id)
                val export = auditExportService.approveExport(id, actor.principal.id, body?.note)
                Response.ok(AuditExportDtoMapper.toDto(export)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditExportResource approvePlatformExport failed", exception)
        }
    }

    override fun listPlatformExportApprovals(exportId: String): Response {
        return try {
            withAuthorizedPlatform(Action.APP_READ_AUDIT) { _ ->
                val id = parseUuid(exportId)
                auditExportService.getPlatformExport(id)
                Response.ok(auditExportService.listApprovals(id).map(AuditExportDtoMapper::toApprovalDto)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditExportResource listPlatformExportApprovals failed", exception)
        }
    }

    override fun downloadPlatformExport(exportId: String): Response {
        return try {
            withAuthorizedPlatform(Action.APP_REQUEST_AUDIT_EXPORT) { actor ->
                val id = parseUuid(exportId)
                auditExportService.getPlatformExport(id)
                val bytes = auditExportService.downloadBundle(id, actor)
                Response.ok(bytes)
                    .header("Content-Disposition", "attachment; filename=\"audit-export-$exportId.zip\"")
                    .header("Content-Length", bytes.size)
                    .build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditExportResource downloadPlatformExport failed", exception)
        }
    }

    override fun getPlatformIntegrity(): Response {
        return try {
            withAuthorizedPlatform(Action.APP_READ_AUDIT) { actor ->
                val report =
                    auditIntegrityService.checkOrganization(
                        null,
                        platformOnly = true,
                        requestedByUserId = actor.principal.id
                    )
                Response.ok(AuditExportDtoMapper.toIntegrityDto(report)).build()
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditExportResource getPlatformIntegrity failed", exception)
        }
    }

    private fun toRequest(organizationId: UUID?, body: AuditExportCreateRequestDto): AuditExportRequest =
        AuditExportRequest(
            organizationId = organizationId,
            categories = body.categories.map { AuditCategory.valueOf(it.uppercase()) }.toSet(),
            occurredAfter = parseAuditInstant(body.occurredAfter, "occurredAfter"),
            occurredBefore = parseAuditInstant(body.occurredBefore, "occurredBefore"),
            purpose = body.purpose,
            caseReference = body.caseReference,
            legalBasis = body.legalBasis,
            downloadLimit = body.downloadLimit,
        )

    private fun withAuthorizedPlatform(action: Action, block: (AuditAccessActor) -> Response): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val decision = authorizationService.authorize(principal, action, platformRef(), context)
        if (decision is Decision.Deny)
        {
            auditExportService.recordDeniedAttempt(principal.id, null, "platform", decision.reasonCode)
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        val capabilities = authorizationService.capabilities(principal, platformRef(), context)
        val actor = AuditAccessActor(principal, context, capabilities)
        return runGuarded { block(actor) }
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
        catch (e: AuditExportNotFoundException)
        {
            Response.status(Response.Status.NOT_FOUND).entity(ResponseError(e.message)).build()
        }
        catch (e: AuditExportAccessException)
        {
            Response.status(Response.Status.FORBIDDEN).entity(ResponseError(e.message)).build()
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)

    private fun platformRef(): ResourceRef = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))
    private fun failedAuditRequest(message: String, exception: Exception): Response {
        if (exception is jakarta.ws.rs.WebApplicationException) throw exception
        if (exception is com.docuhyphen.app.api.exception.SubscriptionDenialException) throw exception
        logger.error(message, exception)
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
    }

}
