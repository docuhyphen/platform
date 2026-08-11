package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.dto.FieldContractDto
import com.docuhyphen.app.api.model.dto.FieldDefinitionDto
import com.docuhyphen.app.api.model.dto.FieldTypeInfoDto
import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.repository.FieldContractRepository
import com.docuhyphen.app.api.repository.FieldDefinitionRepository
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.auth.authz.RoleCapabilities
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Business logic for Field Definitions and their immutable Field Contract versions.
 * Org admins author org-scoped fields; app admins may author platform-scoped fields.
 * A field's value type is fixed at contract v1 and cannot change across versions.
 */
@ApplicationScoped
class FieldDefinitionService @Inject constructor(
    private val fieldDefinitionRepository: FieldDefinitionRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val validator: FieldValueValidator,
    private val typeRegistry: FieldTypeRegistry,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
    private val auditRecorder: AuditRecorder,
    private val subscriptionGuard: BusinessFieldsSubscriptionGuard,
)
{
    private val logger = org.slf4j.LoggerFactory.getLogger(FieldDefinitionService::class.java)
    private val keyPattern = Regex("^[a-z0-9][a-z0-9-]*$")

    // ── Reads ─────────────────────────────────────────────────────────────────

    fun listDefinitions(scopeKind: FieldScopeKind? = null): List<FieldDefinitionDto>
    {
        val principal = currentPrincipal()
        if (scopeKind == FieldScopeKind.PLATFORM)
        {
            if (!userRoleService.isAppAdmin(principal.id))
                throw ForbiddenException("Platform field configuration requires App Administrator access")
            return fieldDefinitionRepository.findAllPlatform().map { it.toDto() }
        }
        val orgId = currentContext().activeOrgId
        val definitions = if (orgId != null && hasOrganizationAccess(principal, orgId, Action.FIELD_CONFIG_VIEW))
            fieldDefinitionRepository.findAllForOrganization(orgId)
        else if (userRoleService.isAppAdmin(principal.id))
            fieldDefinitionRepository.findAllPlatform()
        else
            throw ForbiddenException("Access denied to field configuration")
        return definitions.map { it.toDto() }
    }

    fun getDefinition(id: UUID): FieldDefinitionDto
    {
        val def = fieldDefinitionRepository.findById(id)
            ?: throw IllegalArgumentException("Field definition not found: $id")
        requireScopeAccess(def, Action.FIELD_CONFIG_VIEW)
        return def.toDto()
    }

    fun listContracts(definitionId: UUID): List<FieldContractDto>
    {
        val definition = fieldDefinitionRepository.findById(definitionId)
            ?: throw IllegalArgumentException("Field definition not found: $definitionId")
        requireScopeAccess(definition, Action.FIELD_CONFIG_VIEW)
        return fieldContractRepository.findByDefinition(definitionId).map { it.toDto() }
    }

    fun listTypes(): List<FieldTypeInfoDto> =
        typeRegistry.supportedTypes().map { type ->
            val contract = typeRegistry.contractFor(type)
            FieldTypeInfoDto(
                type = type,
                supportsOptions = type == FieldValueType.SINGLE_SELECT || type == FieldValueType.MULTI_SELECT,
                supportedOperators = contract.supportedOperators.toList(),
            )
        }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    fun createDefinition(request: CreateFieldDefinitionRequest): FieldDefinitionDto
    {
        val principal = currentPrincipal()
        val scopeKind = resolveCreateScope(request.scopeKind, principal)
        val scopeOrgId = if (scopeKind == FieldScopeKind.ORGANIZATION)
            requireActiveOrganizationAccess(principal, Action.FIELD_CONFIG_EDIT)
        else
            null
        subscriptionGuard.requireConfigurationMutation(scopeKind, scopeOrgId)

        val namespace = request.namespace.trim().lowercase()
        val fieldKey = request.fieldKey.trim().lowercase()
        validateKey(namespace, "namespace")
        validateKey(fieldKey, "fieldKey")

        if (fieldDefinitionRepository.findByKey(scopeKind, scopeOrgId, namespace, fieldKey) != null)
            throw FieldValidationException("A field with key $namespace:$fieldKey already exists in this scope")

        val definition = FieldDefinition().apply {
            this.scopeKind = scopeKind
            this.scopeOrgId = scopeOrgId
            this.namespace = namespace
            this.fieldKey = fieldKey
            this.status = FieldLifecycleStatus.PUBLISHED
            this.createdByAppUserId = principal.id
        }
        fieldDefinitionRepository.save(definition)

        buildContract(definition.id, 1, request.contract)
        recordFieldEvent(AuditEventType.FIELD_DEFINITION_CREATE, definition.id, "${definition.namespace}:${definition.fieldKey}", principal.id, scopeOrgId)
        return definition.toDto()
    }

    /** Adds a new immutable contract version. The value type must match version 1. */
    @Transactional
    fun addContractVersion(definitionId: UUID, request: FieldContractRequest): FieldContractDto
    {
        val definition = fieldDefinitionRepository.findById(definitionId)
            ?: throw IllegalArgumentException("Field definition not found: $definitionId")
        requireScopeAccess(definition, Action.FIELD_CONFIG_EDIT)
        subscriptionGuard.requireConfigurationMutation(definition.scopeKind, definition.scopeOrgId)

        val existing = fieldContractRepository.findByDefinition(definitionId)
        val firstType = existing.minByOrNull { it.contractVersion }?.valueType
        if (firstType != null && firstType != request.valueType)
            throw FieldValidationException("A field's value type is immutable; it cannot change from $firstType")

        val nextVersion = fieldContractRepository.findMaxVersion(definitionId) + 1
        val contract = buildContract(definitionId, nextVersion, request)
        definition.updatedAt = Timestamp.from(Instant.now())
        fieldDefinitionRepository.update(definition)
        return contract.toDto()
    }

    @Transactional
    fun retireDefinition(id: UUID): FieldDefinitionDto
    {
        val principal = currentPrincipal()
        val def = fieldDefinitionRepository.findById(id)
            ?: throw IllegalArgumentException("Field definition not found: $id")
        requireScopeAccess(def, Action.FIELD_CONFIG_EDIT)
        subscriptionGuard.requireConfigurationMutation(def.scopeKind, def.scopeOrgId)
        def.status = FieldLifecycleStatus.RETIRED
        def.updatedAt = Timestamp.from(Instant.now())
        val updated = fieldDefinitionRepository.update(def)
        recordFieldEvent(
            AuditEventType.FIELD_DEFINITION_RETIRE,
            updated.id,
            "${updated.namespace}:${updated.fieldKey}",
            principal.id,
            updated.scopeOrgId,
        )
        return updated.toDto()
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun buildContract(definitionId: UUID, version: Int, request: FieldContractRequest): FieldContract
    {
        typeRegistry.contractFor(request.valueType) // fail closed on unknown type
        val contract = FieldContract().apply {
            this.fieldDefinitionId = definitionId
            this.contractVersion = version
            this.valueType = request.valueType
            this.typeContractVersion = typeRegistry.contractFor(request.valueType).typeContractVersion
            this.label = request.label.trim().ifBlank {
                throw FieldValidationException("label is required")
            }
            this.description = request.description?.trim()?.ifBlank { null }
            this.helpText = request.helpText?.trim()?.ifBlank { null }
            this.constraintsJson = request.constraintsJson?.ifBlank { "{}" } ?: "{}"
            this.optionsJson = if (request.options.isNotEmpty())
                FieldOption.encodeList(request.options) else "[]"
            this.dataClassification = request.dataClassification
            this.isSearchable = request.isSearchable
            this.isFilterable = request.isFilterable
            this.isSortable = request.isSortable
            this.isReportable = request.isReportable
        }
        validator.validateContractConfiguration(contract)
        return fieldContractRepository.save(contract)
    }

    private fun resolveCreateScope(requested: FieldScopeKind?, principal: PrincipalRef): FieldScopeKind
    {
        val activeOrgId = currentContext().activeOrgId
        val hasOrgEdit = activeOrgId != null &&
            hasOrganizationAccess(principal, activeOrgId, Action.FIELD_CONFIG_EDIT)
        val scope = requested ?: if (hasOrgEdit) FieldScopeKind.ORGANIZATION else FieldScopeKind.PLATFORM
        if (scope == FieldScopeKind.PLATFORM && !userRoleService.isAppAdmin(principal.id))
            throw ForbiddenException("Only platform administrators may author platform-scoped fields")
        return scope
    }

    private fun validateKey(value: String, label: String)
    {
        if (!keyPattern.matches(value))
            throw FieldValidationException("$label must be lowercase alphanumeric with hyphens (e.g. customer-reference)")
    }

    private fun requireScopeAccess(definition: FieldDefinition, action: Action)
    {
        val principal = currentPrincipal()
        when (definition.scopeKind)
        {
            FieldScopeKind.PLATFORM ->
            {
                if (action == Action.FIELD_CONFIG_VIEW)
                {
                    val activeOrgId = currentContext().activeOrgId
                    if (userRoleService.isAppAdmin(principal.id) ||
                        (activeOrgId != null &&
                            hasOrganizationAccess(principal, activeOrgId, Action.FIELD_CONFIG_VIEW)))
                        return
                }
                else if (userRoleService.isAppAdmin(principal.id))
                    return
            }
            FieldScopeKind.ORGANIZATION ->
            {
                val organizationId = definition.scopeOrgId
                    ?: throw ForbiddenException("Organization-scoped field has no owner")
                if (currentContext().activeOrgId == organizationId &&
                    hasOrganizationAccess(principal, organizationId, action))
                    return
            }
        }
        throw ForbiddenException("Access denied to field configuration")
    }

    private fun requireActiveOrganizationAccess(principal: PrincipalRef, action: Action): UUID
    {
        val orgId = currentContext().activeOrgId
            ?: throw ForbiddenException("An active organization is required")
        if (!hasOrganizationAccess(principal, orgId, action))
            throw ForbiddenException("Access denied to edit field configuration")
        return orgId
    }

    private fun hasOrganizationAccess(principal: PrincipalRef, organizationId: UUID, action: Action): Boolean
    {
        val hasOrganizationCapability = userRoleService.orgRolesIn(principal.id, organizationId)
            .any { action.required in RoleCapabilities.forOrganizationRole(it) }
        if (!hasOrganizationCapability) return false
        return authorizationService.authorize(
            principal,
            action,
            ResourceRef.organization(organizationId),
            currentContext(),
        ) is Decision.Allow
    }

    private fun currentPrincipal(): PrincipalRef =
        authorizationContextFactory.currentPrincipal() ?: throw ForbiddenException("Not authenticated")

    private fun currentContext(): AuthorizationContext =
        authorizationContextFactory.currentContext()

    /**
     * Captures Field Definition lifecycle mutations onto the ledger.
     * targetType uses the literal "FIELD_DEFINITION" (no dedicated ResourceType entry exists
     * for this resource today) per the no-business-FK / denormalized-string rule.
     */
    private fun recordFieldEvent(
        eventType: AuditEventType,
        definitionId: UUID,
        definitionLabel: String?,
        actorId: UUID,
        organizationId: UUID?,
    )
    {
        try
        {
            auditRecorder.record(
                AuditEventDraft(
                    eventTypeKey = eventType.key,
                    outcome = AuditOutcome.SUCCESS,
                    actorId = actorId,
                    actorKind = AuditActorKind.HUMAN,
                    targetType = "FIELD_DEFINITION",
                    targetId = definitionId.toString(),
                    targetLabel = definitionLabel,
                    owner = organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("FieldDefinitionService: AuditRecorder rejected {} draft: {}", eventType.key, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("FieldDefinitionService: AuditRecorder capture failed for {}: {}", eventType.key, e.message, e)
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun FieldDefinition.toDto(): FieldDefinitionDto
    {
        val contracts = fieldContractRepository.findByDefinition(id)
        return FieldDefinitionDto(
            id = id,
            scopeKind = scopeKind,
            scopeOrgId = scopeOrgId,
            namespace = namespace,
            fieldKey = fieldKey,
            status = status,
            contractCount = contracts.size,
            latestContract = contracts.maxByOrNull { it.contractVersion }?.toDto(),
            createdAt = createdAt,
        )
    }

    private fun FieldContract.toDto(): FieldContractDto = FieldContractDto(
        id = id,
        fieldDefinitionId = fieldDefinitionId,
        contractVersion = contractVersion,
        valueType = valueType,
        typeContractVersion = typeContractVersion,
        label = label,
        description = description,
        helpText = helpText,
        constraints = FieldConstraints.parse(constraintsJson),
        options = FieldOption.parseList(optionsJson),
        dataClassification = dataClassification,
        isSearchable = isSearchable,
        isFilterable = isFilterable,
        isSortable = isSortable,
        isReportable = isReportable,
        createdAt = createdAt,
    )
}

// ── Request DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class FieldContractRequest(
    val valueType: FieldValueType,
    val label: String,
    val description: String? = null,
    val helpText: String? = null,
    val constraintsJson: String? = null,
    val options: List<FieldOption> = emptyList(),
    val dataClassification: FieldDataClassification = FieldDataClassification.INTERNAL,
    val isSearchable: Boolean = false,
    val isFilterable: Boolean = false,
    val isSortable: Boolean = false,
    val isReportable: Boolean = false,
)

@Serializable
data class CreateFieldDefinitionRequest(
    val namespace: String,
    val fieldKey: String,
    val scopeKind: FieldScopeKind? = null,
    val contract: FieldContractRequest,
)
