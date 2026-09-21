package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.SchemaFieldBindingDtoMapper
import com.docuhyphen.app.api.model.dto.ResolvedSchemaViewDto
import com.docuhyphen.app.api.model.dto.SchemaDefinitionDto
import com.docuhyphen.app.api.model.dto.SchemaFieldBindingDto
import com.docuhyphen.app.api.model.dto.SchemaVersionDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.*
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.time.Instant
import java.util.*

/**
 * Business logic for Schema Definitions, their immutable published Schema Versions, and the
 * bindings that compose each version. Publishing a draft freezes it; further changes require a
 * new draft version. Resource assignment and value entry live in [SchemaAssignmentService].
 */
@ApplicationScoped
class SchemaDefinitionService @Inject constructor(
    private val schemaDefinitionRepository: SchemaDefinitionRepository,
    private val schemaVersionRepository: SchemaVersionRepository,
    private val bindingRepository: SchemaFieldBindingRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val fieldDefinitionRepository: FieldDefinitionRepository,
    private val fieldValueValidator: FieldValueValidator,
    private val authorizationService: AuthorizationService,
    private val authorizationContextFactory: AuthorizationContextFactory,
    private val userRoleService: UserRoleService,
    private val auditRecorder: AuditRecorder,
    private val subscriptionGuard: BusinessFieldsSubscriptionGuard,
    private val projectionLoader: FieldsProjectionLoader,
    private val schemaTargets: SchemaTargetRegistry,
)
{
    private val logger = org.slf4j.LoggerFactory.getLogger(SchemaDefinitionService::class.java)
    private val keyPattern = Regex("^[a-z0-9][a-z0-9-]*$")

    // ── Reads ─────────────────────────────────────────────────────────────────

    fun listSchemas(scopeKind: FieldScopeKind? = null): List<SchemaDefinitionDto>
    {
        val principal = currentPrincipal()
        if (scopeKind == FieldScopeKind.PERSONAL)
        {
            return schemaDefinitionRepository.findAllForUser(principal.id).map { it.toDto() }
        }
        if (scopeKind == FieldScopeKind.PLATFORM)
        {
            if (!userRoleService.isAppAdmin(principal.id))
                throw ForbiddenException("Platform schema configuration requires App Administrator access")
            return schemaDefinitionRepository.findAllPlatform().map { it.toDto() }
        }
        val orgId = currentContext().activeOrgId
        val schemas = if (orgId != null && hasOrganizationAccess(principal, orgId, Action.FIELD_CONFIG_VIEW))
            schemaDefinitionRepository.findAllForOrganization(orgId)
        else if (userRoleService.isAppAdmin(principal.id))
            schemaDefinitionRepository.findAllPlatform()
        else
            throw ForbiddenException("Access denied to schema configuration")
        return schemas.map { it.toDto() }
    }

    fun getSchema(id: UUID): SchemaDefinitionDto
    {
        val def = schemaDefinitionRepository.findById(id)
            ?: throw IllegalArgumentException("Schema not found: $id")
        requireScopeAccess(def, Action.FIELD_CONFIG_VIEW)
        return def.toDto()
    }

    /** Resolved view of the latest PUBLISHED version, for assignment selection and value entry. */
    fun getResolvedLatestPublished(schemaDefinitionId: UUID): ResolvedSchemaViewDto
    {
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        requireScopeAccess(def, Action.FIELD_CONFIG_VIEW)
        val version = schemaVersionRepository.findLatestPublished(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema has no published version")
        return resolvedView(def, version)
    }

    /**
     * Validates configured default values (keyed by the stable [FieldDefinition] id) against the
     * schema's latest published version. Confirms the schema was written for [targetResourceType],
     * the resource the caller is configuring defaults for, has a published version, and that each
     * supplied field belongs to that version; canonicalizes each value through its type contract
     * (throwing [FieldValidationException] on an invalid value). Returns the validated defaults with
     * their value type for persistence. Unknown fields are rejected at authoring time (callers drop
     * unknowns at apply time instead).
     */
    fun validateDefaultsForSchema(
        schemaDefinitionId: UUID,
        values: List<Pair<UUID, JsonElement>>,
        targetResourceType: String,
    ): List<ValidatedSchemaDefault>
    {
        if (values.isEmpty()) return emptyList()
        val configuredTarget = schemaTargets.requireDeclared(targetResourceType)
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        if (def.targetResourceType != configuredTarget)
            throw FieldValidationException(
                "Schema targets ${def.targetResourceType}, not $configuredTarget"
            )
        val version = schemaVersionRepository.findLatestPublished(schemaDefinitionId)
            ?: throw FieldValidationException("Schema has no published version")

        val bindings = bindingRepository.findByVersion(version.id)
        val contractsById = fieldContractRepository.findByIds(bindings.map { it.fieldContractId })
            .associateBy { it.id }
        val contractByFieldDefinitionId = bindings
            .mapNotNull { contractsById[it.fieldContractId] }
            .associateBy { it.fieldDefinitionId }

        return values.map { (fieldDefinitionId, value) ->
            val contract = contractByFieldDefinitionId[fieldDefinitionId]
                ?: throw FieldValidationException("Field $fieldDefinitionId is not part of this schema")
            fieldValueValidator.canonicalize(contract, value)
            ValidatedSchemaDefault(fieldDefinitionId, contract.valueType, value)
        }
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    fun createSchema(request: CreateSchemaRequest): SchemaDefinitionDto
    {
        val principal = currentPrincipal()
        val targetResourceType = schemaTargets.resolveRequestedTarget(request.targetResourceType)
        val scopeKind = resolveCreateScope(request.scopeKind, principal)
        val scopeOrgId = if (scopeKind == FieldScopeKind.ORGANIZATION)
            requireActiveOrganizationAccess(principal, Action.FIELD_CONFIG_EDIT)
        else
            null
        subscriptionGuard.requireConfigurationMutation(scopeKind, scopeOrgId, null)

        val namespace = request.namespace.trim().lowercase()
        val schemaKey = request.schemaKey.trim().lowercase()
        validateKey(namespace, "namespace")
        validateKey(schemaKey, "schemaKey")
        if (request.displayName.isBlank()) throw FieldValidationException("displayName is required")

        if (schemaDefinitionRepository.findByKey(scopeKind, scopeOrgId, null, namespace, schemaKey) != null)
            throw FieldValidationException("A schema with key $namespace:$schemaKey already exists in this scope")

        val definition = SchemaDefinition().apply {
            this.scopeKind = scopeKind
            this.scopeOrgId = scopeOrgId
            this.namespace = namespace
            this.schemaKey = schemaKey
            this.displayName = request.displayName.trim()
            this.description = request.description?.trim()?.ifBlank { null }
            this.targetResourceType = targetResourceType
            this.status = FieldLifecycleStatus.DRAFT
            this.createdByAppUserId = principal.id
        }
        schemaDefinitionRepository.save(definition)

        val version = SchemaVersion().apply {
            this.schemaDefinitionId = definition.id
            this.versionNumber = 1
            this.status = FieldLifecycleStatus.DRAFT
        }
        schemaVersionRepository.save(version)
        replaceBindings(version, scopeKind, scopeOrgId, request.bindings)

        recordSchemaEvent(AuditEventType.SCHEMA_DEFINITION_CREATE, definition.id, definition.displayName, principal.id, scopeOrgId)
        return definition.toDto()
    }

    /** Replaces the bindings of the current DRAFT version. Fails if there is no draft. */
    @Transactional
    fun updateDraftBindings(schemaDefinitionId: UUID, bindings: List<BindingRequest>): SchemaDefinitionDto
    {
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        requireScopeAccess(def, Action.FIELD_CONFIG_EDIT)
        subscriptionGuard.requireConfigurationMutation(def.scopeKind, def.scopeOrgId, def.scopeUserId)
        val draft = schemaVersionRepository.findDraft(schemaDefinitionId)
            ?: throw IllegalStateException("Schema has no editable draft version")
        bindingRepository.deleteByVersion(draft.id)
        replaceBindings(draft, def.scopeKind, def.scopeOrgId, bindings)
        def.updatedAt = Timestamp.from(Instant.now())
        schemaDefinitionRepository.update(def)
        return def.toDto()
    }

    /** Creates a new DRAFT version cloning the latest published version's bindings. */
    @Transactional
    fun createDraftVersion(schemaDefinitionId: UUID): SchemaDefinitionDto
    {
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        requireScopeAccess(def, Action.FIELD_CONFIG_EDIT)
        subscriptionGuard.requireConfigurationMutation(def.scopeKind, def.scopeOrgId, def.scopeUserId)
        if (schemaVersionRepository.findDraft(schemaDefinitionId) != null)
            throw IllegalStateException("Schema already has an open draft version")

        val nextNumber = schemaVersionRepository.findMaxVersion(schemaDefinitionId) + 1
        val draft = SchemaVersion().apply {
            this.schemaDefinitionId = schemaDefinitionId
            this.versionNumber = nextNumber
            this.status = FieldLifecycleStatus.DRAFT
        }
        schemaVersionRepository.save(draft)

        schemaVersionRepository.findLatestPublished(schemaDefinitionId)?.let { published ->
            bindingRepository.findByVersion(published.id).forEach { source ->
                bindingRepository.save(SchemaFieldBinding().apply {
                    this.schemaVersionId = draft.id
                    this.fieldContractId = source.fieldContractId
                    this.fieldDefinitionId = source.fieldDefinitionId
                    this.displayOrder = source.displayOrder
                    this.section = source.section
                    this.isRequired = source.isRequired
                    this.isReadOnly = source.isReadOnly
                    this.defaultValueJson = source.defaultValueJson
                    this.visibility = source.visibility
                })
            }
        }
        return def.toDto()
    }

    /** Publishes the current DRAFT version, freezing it and marking the schema PUBLISHED. */
    @Transactional
    fun publishDraft(schemaDefinitionId: UUID, compatibility: SchemaCompatibility?): SchemaDefinitionDto
    {
        val principal = currentPrincipal()
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        requireScopeAccess(def, Action.FIELD_CONFIG_PUBLISH)
        subscriptionGuard.requireConfigurationMutation(def.scopeKind, def.scopeOrgId, def.scopeUserId)
        val draft = schemaVersionRepository.findDraft(schemaDefinitionId)
            ?: throw IllegalStateException("Schema has no draft version to publish")
        val bindings = bindingRepository.findByVersion(draft.id)
        if (bindings.isEmpty())
            throw FieldValidationException("A schema version must contain at least one field before publishing")

        draft.status = FieldLifecycleStatus.PUBLISHED
        draft.compatibility = compatibility
        draft.publishedAt = Timestamp.from(Instant.now())
        draft.publishedByAppUserId = principal.id
        schemaVersionRepository.update(draft)

        def.status = FieldLifecycleStatus.PUBLISHED
        def.updatedAt = Timestamp.from(Instant.now())
        schemaDefinitionRepository.update(def)
        recordSchemaEvent(AuditEventType.SCHEMA_DEFINITION_PUBLISH, def.id, def.displayName, principal.id, def.scopeOrgId)
        return def.toDto()
    }

    @Transactional
    fun retireSchema(schemaDefinitionId: UUID): SchemaDefinitionDto
    {
        val principal = currentPrincipal()
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        requireScopeAccess(def, Action.FIELD_CONFIG_PUBLISH)
        subscriptionGuard.requireConfigurationMutation(def.scopeKind, def.scopeOrgId, def.scopeUserId)
        def.status = FieldLifecycleStatus.RETIRED
        def.updatedAt = Timestamp.from(Instant.now())
        val updated = schemaDefinitionRepository.update(def)
        recordSchemaEvent(AuditEventType.SCHEMA_DEFINITION_RETIRE, updated.id, updated.displayName, principal.id, updated.scopeOrgId)
        return updated.toDto()
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun replaceBindings(
        version: SchemaVersion,
        scopeKind: FieldScopeKind,
        scopeOrgId: UUID?,
        bindings: List<BindingRequest>,
    )
    {
        if (bindings.map { it.fieldContractId }.toSet().size != bindings.size)
            throw FieldValidationException("A field may only be bound once per schema version")

        // Two contracts of one definition are two versions of the same field. Binding both would
        // leave every consumer that addresses a field by its stable id with two answers.
        val boundFieldDefinitionIds = mutableSetOf<UUID>()

        bindings.forEachIndexed { index, req ->
            val contract = fieldContractRepository.findById(req.fieldContractId)
                ?: throw FieldValidationException("Unknown field contract: ${req.fieldContractId}")
            val fieldDef = fieldDefinitionRepository.findById(contract.fieldDefinitionId)
                ?: throw FieldValidationException("Field definition missing for contract ${contract.id}")
            if (!boundFieldDefinitionIds.add(fieldDef.id))
                throw FieldValidationException(
                    "Field ${fieldDef.namespace}:${fieldDef.fieldKey} is already bound in this schema version",
                )
            if (fieldDef.status == FieldLifecycleStatus.RETIRED)
                throw FieldValidationException("Field ${fieldDef.namespace}:${fieldDef.fieldKey} is retired")
            // A field must be visible in the schema's scope (its own org, or platform).
            val visible = fieldDef.scopeKind == FieldScopeKind.PLATFORM ||
                (scopeKind == FieldScopeKind.ORGANIZATION &&
                    fieldDef.scopeKind == FieldScopeKind.ORGANIZATION &&
                    fieldDef.scopeOrgId == scopeOrgId)
            if (!visible)
                throw FieldValidationException("Field ${fieldDef.namespace}:${fieldDef.fieldKey} is not available in this scope")

            bindingRepository.save(SchemaFieldBinding().apply {
                this.schemaVersionId = version.id
                this.fieldContractId = contract.id
                this.fieldDefinitionId = fieldDef.id
                this.displayOrder = if (req.displayOrder >= 0) req.displayOrder else index
                this.section = req.section?.trim()?.ifBlank { null }
                this.isRequired = req.isRequired
                this.isReadOnly = req.isReadOnly
                this.defaultValueJson = req.defaultValueJson?.ifBlank { null }
                this.visibility = req.visibility ?: contract.dataClassification
            })
        }
    }

    private fun resolveCreateScope(requested: FieldScopeKind?, principal: PrincipalRef): FieldScopeKind
    {
        val activeOrgId = currentContext().activeOrgId
        val hasOrgEdit = activeOrgId != null &&
            hasOrganizationAccess(principal, activeOrgId, Action.FIELD_CONFIG_EDIT)
        val scope = requested ?: if (hasOrgEdit) FieldScopeKind.ORGANIZATION else FieldScopeKind.PLATFORM
        if (scope == FieldScopeKind.PERSONAL)
            throw FieldValidationException("Personal-scoped schemas cannot be authored yet")
        if (scope == FieldScopeKind.PLATFORM && !userRoleService.isAppAdmin(principal.id))
            throw ForbiddenException("Only platform administrators may author platform-scoped schemas")
        return scope
    }

    private fun validateKey(value: String, label: String)
    {
        if (!keyPattern.matches(value))
            throw FieldValidationException("$label must be lowercase alphanumeric with hyphens (e.g. customer-case)")
    }

    private fun requireScopeAccess(definition: SchemaDefinition, action: Action)
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
                    ?: throw ForbiddenException("Organization-scoped schema has no owner")
                if (currentContext().activeOrgId == organizationId &&
                    hasOrganizationAccess(principal, organizationId, action))
                    return
            }
            // A personally owned schema is storable but has no resolvable owner in the configuration
            // scope model yet, so no caller reaches it. Falling through denies rather than guessing.
            FieldScopeKind.PERSONAL -> Unit
        }
        throw ForbiddenException("Access denied to schema configuration")
    }

    private fun requireActiveOrganizationAccess(principal: PrincipalRef, action: Action): UUID
    {
        val orgId = currentContext().activeOrgId
            ?: throw ForbiddenException("An active organization is required")
        if (!hasOrganizationAccess(principal, orgId, action))
            throw ForbiddenException("Access denied to edit schema configuration")
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
     * Captures Schema Definition lifecycle mutations onto the ledger.
     * targetType uses the literal "SCHEMA_DEFINITION" (no dedicated ResourceType entry exists
     * for this resource today) per the no-business-FK / denormalized-string rule.
     */
    private fun recordSchemaEvent(
        eventType: AuditEventType,
        schemaDefinitionId: UUID,
        schemaDisplayName: String?,
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
                    targetType = "SCHEMA_DEFINITION",
                    targetId = schemaDefinitionId.toString(),
                    targetLabel = schemaDisplayName,
                    owner = organizationId?.let(AuditOwnerScope::Organization) ?: AuditOwnerScope.Platform,
                )
            )
        }
        catch (e: AuditDraftInvalidException)
        {
            logger.warn("SchemaDefinitionService: AuditRecorder rejected {} draft: {}", eventType.key, e.message)
        }
        catch (e: AuditCaptureFailedException)
        {
            logger.error("SchemaDefinitionService: AuditRecorder capture failed for {}: {}", eventType.key, e.message, e)
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun SchemaDefinition.toDto(): SchemaDefinitionDto
    {
        val draft = schemaVersionRepository.findDraft(id)
        val published = schemaVersionRepository.findLatestPublished(id)
        return SchemaDefinitionDto(
            id = id,
            scopeKind = scopeKind,
            scopeOrgId = scopeOrgId,
            namespace = namespace,
            schemaKey = schemaKey,
            displayName = displayName,
            description = description,
            targetResourceType = targetResourceType,
            status = status,
            draftVersion = draft?.toDto(),
            latestPublishedVersion = published?.toDto(),
            createdAt = createdAt,
        )
    }

    private fun SchemaVersion.toDto(): SchemaVersionDto = SchemaVersionDto(
        id = id,
        schemaDefinitionId = schemaDefinitionId,
        versionNumber = versionNumber,
        status = status,
        compatibility = compatibility,
        bindings = mapBindings(id),
        publishedAt = publishedAt,
        createdAt = createdAt,
    )

    private fun resolvedView(def: SchemaDefinition, version: SchemaVersion): ResolvedSchemaViewDto =
        ResolvedSchemaViewDto(
            schemaDefinitionId = def.id,
            schemaKey = def.schemaKey,
            namespace = def.namespace,
            displayName = def.displayName,
            schemaVersionId = version.id,
            versionNumber = version.versionNumber,
            targetResourceType = def.targetResourceType,
            scopeKind = def.scopeKind,
            fields = mapBindings(version.id),
        )

    /** The questions a version asks, in the order it asks them, as an editor is built from them. */
    private fun mapBindings(schemaVersionId: UUID): List<SchemaFieldBindingDto> =
        projectionLoader.resolveBindings(schemaVersionId).map(SchemaFieldBindingDtoMapper::toDto)
}

// ── Request DTOs ─────────────────────────────────────────────────────────────

/** A blueprint default value validated against a schema's published version, ready to persist. */
data class ValidatedSchemaDefault(
    val fieldDefinitionId: UUID,
    val valueType: FieldValueType,
    val value: JsonElement,
)

@Serializable
data class BindingRequest(
    @Serializable(with = com.docuhyphen.app.api.serializer.UUIDSerializer::class)
    val fieldContractId: UUID,
    val displayOrder: Int = -1,
    val section: String? = null,
    val isRequired: Boolean = false,
    val isReadOnly: Boolean = false,
    val defaultValueJson: String? = null,
    val visibility: com.docuhyphen.app.api.model.entity.FieldDataClassification? = null,
)

@Serializable
data class CreateSchemaRequest(
    val namespace: String,
    val schemaKey: String,
    val displayName: String,
    val description: String? = null,
    val scopeKind: FieldScopeKind? = null,
    val targetResourceType: String? = null,
    val bindings: List<BindingRequest> = emptyList(),
)

@Serializable
data class PublishSchemaRequest(
    val compatibility: SchemaCompatibility? = null,
)

@Serializable
data class UpdateBindingsRequest(
    val bindings: List<BindingRequest> = emptyList(),
)
