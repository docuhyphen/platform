package com.docuhyphen.app.api.service.variable

import com.docuhyphen.app.api.model.dto.CreateVariableRequest
import com.docuhyphen.app.api.model.dto.UpdateVariableRequest
import com.docuhyphen.app.api.model.dto.VariableDefinitionDto
import com.docuhyphen.app.api.model.dto.toDto
import com.docuhyphen.app.api.model.entity.VariableDefinition
import com.docuhyphen.app.api.model.entity.VariableScope
import com.docuhyphen.app.api.repository.VariableDefinitionRepository
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.util.*

@ApplicationScoped
class VariableDefinitionService @Inject constructor(
    private val repository: VariableDefinitionRepository,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(VariableDefinitionService::class.java)
        private val KEY_REGEX = Regex("^[A-Z0-9_]{1,64}$")
    }

    fun listVariables(
        scope: VariableScope,
        organizationId: UUID?,
        userId: UUID,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): List<VariableDefinitionDto>
    {
        return when (scope)
        {
            VariableScope.ORG ->
            {
                if (organizationId == null) emptyList()
                else repository.findByScopeAndOrganizationIdAndIsDeletedFalse(scope, organizationId)
                    .map { it.toDto() }
            }
            VariableScope.PERSONAL ->
                repository.findByScopeAndCreatedByAppUserIdAndIsDeletedFalse(scope, userId)
                    .map { it.toDto() }
        }
    }

    @Transactional
    fun createVariable(
        request: CreateVariableRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): VariableDefinitionDto
    {
        val scope = runCatching { VariableScope.valueOf(request.scope.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Invalid scope '${request.scope}'") }

        val normalizedKey = request.key.trim().uppercase()
        if (!KEY_REGEX.matches(normalizedKey))
            throw IllegalArgumentException("Variable key must be 1–64 uppercase alphanumeric characters or underscores")

        when (scope)
        {
            VariableScope.ORG ->
            {
                if (!isOrgAdmin && !isAppAdmin)
                    throw ForbiddenException("Org admin role required to manage org variables")
                if (callerOrgId == null)
                    throw IllegalArgumentException("Organization context required for ORG scope")
                if (repository.findByOrganizationIdAndKeyAndIsDeletedFalse(callerOrgId, normalizedKey) != null)
                    throw IllegalArgumentException("Variable '$normalizedKey' already exists in this organization")
            }
            VariableScope.PERSONAL ->
            {
                if (repository.findByCreatedByAppUserIdAndKeyAndIsDeletedFalse(callerUserId, normalizedKey) != null)
                    throw IllegalArgumentException("Variable '$normalizedKey' already exists in your personal variables")
            }
        }

        val variable = VariableDefinition().apply {
            this.key = normalizedKey
            this.defaultValue = request.defaultValue
            this.scope = scope
            this.organizationId = if (scope == VariableScope.ORG) callerOrgId else null
            this.createdByAppUserId = callerUserId
        }
        return repository.save(variable).toDto()
    }

    @Transactional
    fun updateVariable(
        id: UUID,
        request: UpdateVariableRequest,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    ): VariableDefinitionDto
    {
        val variable = repository.findById(id) ?: throw IllegalArgumentException("Variable not found")
        checkWriteAccess(variable, callerUserId, callerOrgId, isOrgAdmin, isAppAdmin)
        request.defaultValue?.let { variable.defaultValue = it.takeIf { v -> v.isNotBlank() } }
        request.isActive?.let { variable.isActive = it }
        return repository.update(variable).toDto()
    }

    @Transactional
    fun deleteVariable(
        id: UUID,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    )
    {
        val variable = repository.findById(id) ?: throw IllegalArgumentException("Variable not found")
        checkWriteAccess(variable, callerUserId, callerOrgId, isOrgAdmin, isAppAdmin)
        variable.isDeleted = true
        variable.isActive = false
        repository.update(variable)
        logger.info("Variable {} soft-deleted by user {}", id, callerUserId)
    }

    private fun checkWriteAccess(
        variable: VariableDefinition,
        callerUserId: UUID,
        callerOrgId: UUID?,
        isOrgAdmin: Boolean,
        isAppAdmin: Boolean,
    )
    {
        if (isAppAdmin) return
        when (variable.scope)
        {
            VariableScope.PERSONAL ->
                if (variable.createdByAppUserId != callerUserId)
                    throw ForbiddenException("Access denied to variable ${variable.id}")
            VariableScope.ORG ->
                if (!isOrgAdmin || callerOrgId == null || variable.organizationId != callerOrgId)
                    throw ForbiddenException("Org admin role required to modify variable ${variable.id}")
        }
    }
}
