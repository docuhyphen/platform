package com.docuhyphen.app.api.resource.audit

import com.docuhyphen.app.api.exception.SubscriptionDenialException
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.dto.AuditLegalHoldCreateRequestDto
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.audit.AuditLegalHoldNotFoundException
import com.docuhyphen.app.api.service.audit.AuditLegalHoldService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import java.util.*

@jakarta.enterprise.context.RequestScoped
class AuditOrganizationLegalHoldResource @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val auditLegalHoldService: AuditLegalHoldService,
) : com.docuhyphen.app.api.resource.audit.operations.AuditOrganizationLegalHoldResourceOperations
{
    private val legalHolds =
        com.docuhyphen.app.api.resource.audit.handler.AuditLegalHoldRequestHandler(auditLegalHoldService)

    companion object
    {
        private val logger = LoggerFactory.getLogger(AuditOrganizationLegalHoldResource::class.java)
    }

    override fun placeOrganizationLegalHold(
        organizationId: String,
        body: AuditLegalHoldCreateRequestDto,
    ): Response {
        return try {
            withAuthorizedOrg(organizationId, Action.AUDIT_LEGAL_HOLD_MANAGE) { principal, orgId ->
                legalHolds.place(orgId, principal, body)
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditOrganizationLegalHoldResource placeOrganizationLegalHold failed", exception)
        }
    }

    override fun listOrganizationLegalHolds(organizationId: String): Response {
        return try {
            withAuthorizedOrg(organizationId, Action.ORG_READ_AUDIT) { _, orgId ->
                legalHolds.list(orgId)
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditOrganizationLegalHoldResource listOrganizationLegalHolds failed", exception)
        }
    }

    override fun releaseOrganizationLegalHold(
        organizationId: String,
        holdId: String,
    ): Response {
        return try {
            withAuthorizedOrg(organizationId, Action.AUDIT_LEGAL_HOLD_MANAGE) { principal, orgId ->
                legalHolds.release(orgId, parseUuid(holdId), principal)
            }
        } catch (exception: Exception) {
            failedAuditRequest("AuditOrganizationLegalHoldResource releaseOrganizationLegalHold failed", exception)
        }
    }

    private fun withAuthorizedOrg(
        organizationId: String,
        action: Action,
        block: (UUID, UUID) -> Response,
    ): Response
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: return Response.status(Response.Status.UNAUTHORIZED).entity(ResponseError("Unauthorized")).build()
        val context = authorizationContextFactory.currentContext()
        val orgId = parseUuid(organizationId)
        val decision = authorizationService.authorize(principal, action, ResourceRef.organization(orgId), context)
        if (decision is Decision.Deny)
        {
            auditLegalHoldService.recordDeniedAttempt(principal.id, orgId, decision.reasonCode)
            return Response.status(Response.Status.FORBIDDEN).entity(ResponseError("Insufficient privileges")).build()
        }
        return runGuarded { block(principal.id, orgId) }
    }

    private fun runGuarded(block: () -> Response): Response
    {
        return try
        {
            block()
        }
        catch (e: SubscriptionDenialException)
        {
            logger.warn("Organization audit legal-hold change refused by subscription policy", e)
            throw e
        }
        catch (e: IllegalArgumentException)
        {
            val status =
                if (e is AuditLegalHoldNotFoundException) Response.Status.NOT_FOUND else Response.Status.BAD_REQUEST
            Response.status(status).entity(ResponseError(e.message)).build()
        } catch (e: com.docuhyphen.app.api.exception.RecordPreservationException) {
            com.docuhyphen.app.api.resource.recordpreservation.RecordPreservationHttp.refused(
                logger, "Organization audit legal-hold conflict refused", e,
            )
        }
    }

    private fun parseUuid(raw: String): UUID = UUID.fromString(raw)
    private fun failedAuditRequest(message: String, exception: Exception): Response {
        if (exception is jakarta.ws.rs.WebApplicationException) throw exception
        if (exception is SubscriptionDenialException) throw exception
        logger.error(message, exception)
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ResponseError("Request failed")).build()
    }

}
