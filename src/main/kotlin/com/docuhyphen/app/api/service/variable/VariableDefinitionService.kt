package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.dto.CreateVariableRequest
import com.docuhyphen.app.api.model.dto.UpdateVariableRequest
import com.docuhyphen.app.api.model.dto.VariableDefinitionDto
import com.docuhyphen.app.api.model.dto.toDto
import com.docuhyphen.app.api.model.entity.VariableDefinition
import com.docuhyphen.app.api.model.entity.VariableScope
import com.docuhyphen.app.api.repository.VariableDefinitionRepository
import com.docuhyphen.app.api.service.auth.AdminActionGuardService
import com.docuhyphen.app.api.service.auth.AdminApprovalContext
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class VariableDefinitionService @Inject constructor(
    private val repository: VariableDefinitionRepository,
    private val adminActionGuardService: AdminActionGuardService,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
    private val variableSubscriptionGuard: VariableSubscriptionGuard,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(VariableDefinitionService::class.java)
        private val KEY_REGEX = Regex("^[A-Z0-9_]{1,64}$")
    }

    fun listVariables(scope: VariableScope): List<VariableDefinitionDto>
    {
        val principal = currentPrincipal()
        val activeOrgId = currentContext().activeOrgId
        return when (scope)
        {
            VariableScope.ORG ->
            {
                if (activeOrgId == null) emptyList()
                else repository.findByScopeAndOrganizationIdAndIsDeletedFalse(scope, activeOrgId)
                    .map { it.toDto() }
            }
            VariableScope.PERSONAL ->
                repository.findByScopeAndCreatedByAppUserIdAndIsDeletedFalse(scope, principal.id)
                    .map { it.toDto() }
        }
    }

    @Transactional
    fun createVariable(request: CreateVariableRequest, context: AdminApprovalContext): VariableDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val activeOrgId = authContext.activeOrgId

        val scope = runCatching { VariableScope.valueOf(request.scope.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Invalid scope '${request.scope}'") }

        val normalizedKey = request.key.trim().uppercase()
        if (!KEY_REGEX.matches(normalizedKey))
            throw IllegalArgumentException("Variable key must be 1–64 uppercase alphanumeric characters or underscores")

        when (scope)
        {
            VariableScope.ORG ->
            {
                val organizationId = activeOrgId
                    ?: throw IllegalArgumentException("Organization context required for ORG scope")
                if (!userRoleService.isOrgAdminIn(principal.id, organizationId))
                    throw ForbiddenException("Org admin role required to manage org variables")
                if (repository.findByOrganizationIdAndKeyAndIsDeletedFalse(organizationId, normalizedKey) != null)
                    throw IllegalArgumentException("Variable '$normalizedKey' already exists in this organization")
            }
            VariableScope.PERSONAL ->
            {
                if (repository.findByCreatedByAppUserIdAndKeyAndIsDeletedFalse(principal.id, normalizedKey) != null)
                    throw IllegalArgumentException("Variable '$normalizedKey' already exists in your personal variables")
            }
        }

        adminActionGuardService.enforce(
            action = actionFor(scope, "CREATE"),
            actorId = principal.id,
            context = context,
        )
        requirePlanAllowance(scope, principal.id, activeOrgId)

        val variable = VariableDefinition().apply {
            this.key = normalizedKey
            this.defaultValue = request.defaultValue
            this.scope = scope
            this.organizationId = if (scope == VariableScope.ORG) activeOrgId else null
            this.createdByAppUserId = principal.id
        }
        return repository.save(variable).toDto()
    }

    @Transactional
    fun updateVariable(id: UUID, request: UpdateVariableRequest, context: AdminApprovalContext): VariableDefinitionDto
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val variable = repository.findById(id) ?: throw IllegalArgumentException("Variable not found")
        checkWriteAccess(variable, principal, authContext)
        adminActionGuardService.enforce(
            action = actionFor(variable.scope, "UPDATE"),
            actorId = principal.id,
            context = context,
        )
        requirePlanAllowance(variable.scope, variable.createdByAppUserId, variable.organizationId)
        request.defaultValue?.let { variable.defaultValue = it.takeIf { v -> v.isNotBlank() } }
        request.isActive?.let { variable.isActive = it }
        return repository.update(variable).toDto()
    }

    @Transactional
    fun deleteVariable(id: UUID, context: AdminApprovalContext)
    {
        val principal = currentPrincipal()
        val authContext = currentContext()
        val variable = repository.findById(id) ?: throw IllegalArgumentException("Variable not found")
        checkWriteAccess(variable, principal, authContext)
        adminActionGuardService.enforce(
            action = actionFor(variable.scope, "DELETE"),
            actorId = principal.id,
            context = context,
        )
        requirePlanAllowance(variable.scope, variable.createdByAppUserId, variable.organizationId)
        variable.isDeleted = true
        variable.isActive = false
        repository.update(variable)
        logger.info("Variable {} soft-deleted by user {}", id, principal.id)
    }

    // ── Access control ────────────────────────────────────────────────────────

    private fun checkWriteAccess(variable: VariableDefinition, principal: PrincipalRef, context: AuthorizationContext)
    {
        when (variable.scope)
        {
            VariableScope.PERSONAL ->
                if (variable.createdByAppUserId != principal.id)
                    throw ForbiddenException("Access denied to variable ${variable.id}")
            VariableScope.ORG ->
            {
                val decision = authorizationService.authorize(
                    principal, Action.VARIABLE_EDIT, ResourceRef.variable(variable.id), context,
                )
                if (decision is Decision.Deny)
                    throw ForbiddenException("Org admin role required to modify variable ${variable.id}")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Charges the change to whoever owns the variable, so an organization variable is always paid
     * for by that organization and a personal one by the person who created it.
     */
    private fun requirePlanAllowance(scope: VariableScope, ownerUserId: UUID?, organizationId: UUID?)
    {
        when (scope)
        {
            VariableScope.ORG -> organizationId?.let(variableSubscriptionGuard::requireOrganizationManagement)
            VariableScope.PERSONAL -> ownerUserId?.let(variableSubscriptionGuard::requirePersonalManagement)
        }
    }

    private fun currentPrincipal(): PrincipalRef =
        authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Not authenticated")

    private fun currentContext(): AuthorizationContext =
        authorizationContextFactory.currentContext()

    private fun actionFor(scope: VariableScope, operation: String): String =
        when (scope)
        {
            VariableScope.ORG -> "ORG_VARIABLE_$operation"
            VariableScope.PERSONAL -> "PERSONAL_VARIABLE_$operation"
        }
}
