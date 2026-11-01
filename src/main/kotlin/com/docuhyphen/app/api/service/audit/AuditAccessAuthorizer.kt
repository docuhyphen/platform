package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditAccessActor
import com.docuhyphen.app.api.model.audit.AuditAccessDenial
import com.docuhyphen.app.api.model.audit.AuditOrganizationAccess
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AuditAccessAuthorizer @Inject constructor(
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    companion object
    {
        private const val UNAUTHORIZED_MESSAGE = "Unauthorized"
        private const val INSUFFICIENT_PRIVILEGES_MESSAGE = "Insufficient privileges"

        fun platformResource(): ResourceRef = ResourceRef(ResourceType.APPLICATION, UUID(0, 0))
    }

    fun currentActor(resource: ResourceRef = platformResource()): AuditAccessActor
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw UnauthorizedException(UNAUTHORIZED_MESSAGE)
        val context = authorizationContextFactory.currentContext()
        return AuditAccessActor(principal, context, authorizationService.capabilities(principal, resource, context))
    }

    fun authorizeOrganization(
        organizationId: UUID,
        action: Action,
        onDenied: (AuditAccessDenial) -> Unit = {},
    ): AuditOrganizationAccess
    {
        val actor = authorize(ResourceRef.organization(organizationId), action, organizationId, onDenied)
        return AuditOrganizationAccess(actor, organizationId)
    }

    fun authorizePlatform(action: Action, onDenied: (AuditAccessDenial) -> Unit = {}): AuditAccessActor =
        authorize(platformResource(), action, null, onDenied)

    private fun authorize(
        resource: ResourceRef,
        action: Action,
        organizationId: UUID?,
        onDenied: (AuditAccessDenial) -> Unit,
    ): AuditAccessActor
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw UnauthorizedException(UNAUTHORIZED_MESSAGE)
        val context = authorizationContextFactory.currentContext()
        val decision = authorizationService.authorize(principal, action, resource, context)
        val actor = AuditAccessActor(principal, context, authorizationService.capabilities(principal, resource, context))
        if (decision is Decision.Deny)
        {
            onDenied(AuditAccessDenial(actor, organizationId, decision.reasonCode))
            throw ForbiddenException(INSUFFICIENT_PRIVILEGES_MESSAGE)
        }
        return actor
    }
}
