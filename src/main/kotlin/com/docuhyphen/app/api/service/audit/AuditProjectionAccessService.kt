package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.model.audit.AuditAccessActor
import com.docuhyphen.app.api.model.audit.AuditOrganizationAccess
import com.docuhyphen.app.api.model.audit.AuditWorkflowDefinitionAccess
import com.docuhyphen.app.api.model.auth.authz.OwnerContext
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.util.*

@ApplicationScoped
class AuditProjectionAccessService @Inject constructor(
    private val auditAccessAuthorizer: AuditAccessAuthorizer,
    private val auditSearchProjectionService: AuditSearchProjectionService,
    private val resourceAuthorizationContextRegistry: ResourceAuthorizationContextRegistry,
)
{
    companion object
    {
        private const val ORGANIZATION_TARGET_TYPE = "ORGANIZATION"
        private const val PLATFORM_TARGET_TYPE = "PLATFORM"
        private const val PLATFORM_TARGET_ID = "platform"
    }

    fun organizationRead(organizationId: UUID): AuditOrganizationAccess =
        auditAccessAuthorizer.authorizeOrganization(organizationId, Action.ORG_READ_AUDIT) { denial ->
            auditSearchProjectionService.recordDeniedAttempt(
                denial.actor,
                organizationId,
                ORGANIZATION_TARGET_TYPE,
                organizationId.toString(),
                denial.reasonCode,
            )
        }

    fun platformRead(): AuditAccessActor =
        auditAccessAuthorizer.authorizePlatform(Action.APP_READ_AUDIT) { denial ->
            auditSearchProjectionService.recordDeniedAttempt(
                denial.actor,
                null,
                PLATFORM_TARGET_TYPE,
                PLATFORM_TARGET_ID,
                denial.reasonCode,
            )
        }

    fun personalActor(): AuditAccessActor = auditAccessAuthorizer.currentActor()

    fun exchangeRead(exchangeId: UUID): AuditOrganizationAccess
    {
        val owner = resourceAuthorizationContextRegistry.resolve(ResourceRef.exchange(exchangeId))?.ownerContext
            ?: throw AuditProjectionNotFoundException("Exchange not found")
        if (owner !is OwnerContext.Organization)
        {
            throw AuditProjectionAccessDeniedException("Exchange audit is not available for this scope")
        }
        return organizationRead(owner.organizationId)
    }

    fun workflowDefinitionRead(definitionId: UUID): AuditWorkflowDefinitionAccess
    {
        val owner = resourceAuthorizationContextRegistry.resolve(ResourceRef.workflowDefinition(definitionId))?.ownerContext
            ?: throw AuditProjectionNotFoundException("Workflow Definition not found")
        return when (owner)
        {
            is OwnerContext.Organization ->
            {
                val access = organizationRead(owner.organizationId)
                AuditWorkflowDefinitionAccess(access.actor, access.organizationId, platformOnly = false)
            }

            OwnerContext.Platform -> AuditWorkflowDefinitionAccess(platformRead(), null, platformOnly = true)

            is OwnerContext.Personal ->
                throw AuditProjectionAccessDeniedException("Workflow Definition audit is not available for personal scope")
        }
    }
}
