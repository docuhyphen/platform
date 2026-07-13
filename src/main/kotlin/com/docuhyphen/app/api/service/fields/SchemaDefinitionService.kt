package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.dto.ResolvedSchemaViewDto
import com.docuhyphen.app.api.model.dto.SchemaDefinitionDto
import com.docuhyphen.app.api.model.dto.SchemaFieldBindingDto
import com.docuhyphen.app.api.model.dto.SchemaVersionDto
import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaCompatibility
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.FieldContractRepository
import com.docuhyphen.app.api.repository.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.SchemaVersionRepository
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.service.auth.authz.Decision
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceRef
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
import kotlinx.serialization.json.JsonElement
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

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
)
{
    private val logger = org.slf4j.LoggerFactory.getLogger(SchemaDefinitionService::class.java)
    private val keyPattern = Regex("^[a-z0-9][a-z0-9-]*$")

    // ── Reads ─────────────────────────────────────────────────────────────────

    fun listSchemas(): List<SchemaDefinitionDto>
    {
        val orgId = requireOrgConfigView()
        return schemaDefinitionRepository.findAllForOrganization(orgId).map { it.toDto() }
    }

    fun getSchema(id: UUID): SchemaDefinitionDto
    {
        requireOrgConfigView()
        val def = schemaDefinitionRepository.findById(id)
            ?: throw IllegalArgumentException("Schema not found: $id")
        return def.toDto()
    }

    /** Resolved view of the latest PUBLISHED version, for assignment selection and value entry. */
    fun getResolvedLatestPublished(schemaDefinitionId: UUID): ResolvedSchemaViewDto
    {
        requireOrgConfigView()
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        val version = schemaVersionRepository.findLatestPublished(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema has no published version")
        return resolvedView(def, version)
    }

    /**
     * Validates blueprint default values (keyed by the stable [FieldDefinition] id) against the
     * schema's latest published version. Confirms the schema targets EXCHANGE, has a published
     * version, and that each supplied field belongs to that version; canonicalizes each value
     * through its type contract (throwing [FieldValidationException] on an invalid value). Returns
     * the validated defaults with their value type for persistence. Unknown fields are rejected at
     * authoring time (callers drop unknowns at apply time instead).
     */
    fun validateDefaultsForSchema(
        schemaDefinitionId: UUID,
        values: List<Pair<UUID, JsonElement>>,
    ): List<ValidatedSchemaDefault>
    {
        if (values.isEmpty()) return emptyList()
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        if (def.targetResourceType != ResourceType.EXCHANGE.name)
            throw FieldValidationException("Schema targets ${def.targetResourceType}, not an Exchange")
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
        val (principal, orgId) = requireOrgConfigEdit()
        val scopeKind = resolveScope(request.scopeKind, principal)
        val scopeOrgId = if (scopeKind == FieldScopeKind.ORGANIZATION) orgId else null

        val namespace = request.namespace.trim().lowercase()
        val schemaKey = request.schemaKey.trim().lowercase()
        validateKey(namespace, "namespace")
        validateKey(schemaKey, "schemaKey")
        if (request.displayName.isBlank()) throw FieldValidationException("displayName is required")

        if (schemaDefinitionRepository.findByKey(scopeKind, scopeOrgId, namespace, schemaKey) != null)
            throw FieldValidationException("A schema with key $namespace:$schemaKey already exists in this scope")

        val definition = SchemaDefinition().apply {
            this.scopeKind = scopeKind
            this.scopeOrgId = scopeOrgId
            this.namespace = namespace
            this.schemaKey = schemaKey
            this.displayName = request.displayName.trim()
            this.description = request.description?.trim()?.ifBlank { null }
            this.targetResourceType = "EXCHANGE"
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
        replaceBindings(version, orgId, scopeKind, scopeOrgId, request.bindings)

        recordSchemaEvent(AuditEventType.SCHEMA_DEFINITION_CREATE, definition.id, definition.displayName, principal.id, scopeOrgId)
        return definition.toDto()
    }

    /** Replaces the bindings of the current DRAFT version. Fails if there is no draft. */
    @Transactional
    fun updateDraftBindings(schemaDefinitionId: UUID, bindings: List<BindingRequest>): SchemaDefinitionDto
    {
        val (_, orgId) = requireOrgConfigEdit()
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        val draft = schemaVersionRepository.findDraft(schemaDefinitionId)
            ?: throw IllegalStateException("Schema has no editable draft version")
        bindingRepository.deleteByVersion(draft.id)
        replaceBindings(draft, orgId, def.scopeKind, def.scopeOrgId, bindings)
        def.updatedAt = Timestamp.from(Instant.now())
        schemaDefinitionRepository.update(def)
        return def.toDto()
    }

    /** Creates a new DRAFT version cloning the latest published version's bindings. */
    @Transactional
    fun createDraftVersion(schemaDefinitionId: UUID): SchemaDefinitionDto
    {
        requireOrgConfigEdit()
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
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
        requirePublish()
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
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
        requirePublish()
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        def.status = FieldLifecycleStatus.RETIRED
        def.updatedAt = Timestamp.from(Instant.now())
        val updated = schemaDefinitionRepository.update(def)
        recordSchemaEvent(AuditEventType.SCHEMA_DEFINITION_RETIRE, updated.id, updated.displayName, principal.id, updated.scopeOrgId)
        return updated.toDto()
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun replaceBindings(
        version: SchemaVersion,
        orgId: UUID,
        scopeKind: FieldScopeKind,
        scopeOrgId: UUID?,
        bindings: List<BindingRequest>,
    )
    {
        if (bindings.map { it.fieldContractId }.toSet().size != bindings.size)
            throw FieldValidationException("A field may only be bound once per schema version")

        bindings.forEachIndexed { index, req ->
            val contract = fieldContractRepository.findById(req.fieldContractId)
                ?: throw FieldValidationException("Unknown field contract: ${req.fieldContractId}")
            val fieldDef = fieldDefinitionRepository.findById(contract.fieldDefinitionId)
                ?: throw FieldValidationException("Field definition missing for contract ${contract.id}")
            if (fieldDef.status == FieldLifecycleStatus.RETIRED)
                throw FieldValidationException("Field ${fieldDef.namespace}:${fieldDef.fieldKey} is retired")
            // A field must be visible in the schema's scope (its own org, or platform).
            val visible = fieldDef.scopeKind == FieldScopeKind.PLATFORM ||
                (fieldDef.scopeKind == FieldScopeKind.ORGANIZATION && fieldDef.scopeOrgId == scopeOrgId)
            if (scopeKind == FieldScopeKind.ORGANIZATION && !visible)
                throw FieldValidationException("Field ${fieldDef.namespace}:${fieldDef.fieldKey} is not available in this scope")

            bindingRepository.save(SchemaFieldBinding().apply {
                this.schemaVersionId = version.id
                this.fieldContractId = contract.id
                this.displayOrder = if (req.displayOrder >= 0) req.displayOrder else index
                this.section = req.section?.trim()?.ifBlank { null }
                this.isRequired = req.isRequired
                this.isReadOnly = req.isReadOnly
                this.defaultValueJson = req.defaultValueJson?.ifBlank { null }
                this.visibility = req.visibility ?: contract.dataClassification
            })
        }
    }

    private fun resolveScope(requested: FieldScopeKind?, principal: PrincipalRef): FieldScopeKind
    {
        val scope = requested ?: FieldScopeKind.ORGANIZATION
        if (scope == FieldScopeKind.PLATFORM && !userRoleService.isAppAdmin(principal.id))
            throw ForbiddenException("Only platform administrators may author platform-scoped schemas")
        return scope
    }

    private fun validateKey(value: String, label: String)
    {
        if (!keyPattern.matches(value))
            throw FieldValidationException("$label must be lowercase alphanumeric with hyphens (e.g. customer-case)")
    }

    private fun requireOrgConfigView(): UUID
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val orgId = context.activeOrgId ?: throw ForbiddenException("An active organization is required")
        val decision = authorizationService.authorize(
            principal, Action.FIELD_CONFIG_VIEW, ResourceRef.organization(orgId), context,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to schema configuration")
        return orgId
    }

    private fun requireOrgConfigEdit(): Pair<PrincipalRef, UUID>
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val orgId = context.activeOrgId ?: throw ForbiddenException("An active organization is required")
        val decision = authorizationService.authorize(
            principal, Action.FIELD_CONFIG_EDIT, ResourceRef.organization(orgId), context,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to edit schema configuration")
        return principal to orgId
    }

    private fun requirePublish(): UUID
    {
        val principal = currentPrincipal()
        val context = currentContext()
        val orgId = context.activeOrgId ?: throw ForbiddenException("An active organization is required")
        val decision = authorizationService.authorize(
            principal, Action.FIELD_CONFIG_PUBLISH, ResourceRef.organization(orgId), context,
        )
        if (decision is Decision.Deny) throw ForbiddenException("Access denied to publish schemas")
        return orgId
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
        bindings = mapBindings(bindingRepository.findByVersion(id)),
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
            fields = mapBindings(bindingRepository.findByVersion(version.id)),
        )

    private fun mapBindings(bindings: List<SchemaFieldBinding>): List<SchemaFieldBindingDto>
    {
        if (bindings.isEmpty()) return emptyList()
        val contracts = fieldContractRepository.findByIds(bindings.map { it.fieldContractId })
            .associateBy { it.id }
        val definitions = fieldDefinitionRepository.let { repo ->
            contracts.values.mapNotNull { repo.findById(it.fieldDefinitionId) }.associateBy { it.id }
        }
        return bindings.sortedBy { it.displayOrder }.mapNotNull { binding ->
            val contract = contracts[binding.fieldContractId] ?: return@mapNotNull null
            val fieldDef = definitions[contract.fieldDefinitionId] ?: return@mapNotNull null
            SchemaFieldBindingDto(
                id = binding.id,
                fieldContractId = contract.id,
                fieldDefinitionId = fieldDef.id,
                namespace = fieldDef.namespace,
                fieldKey = fieldDef.fieldKey,
                label = contract.label,
                valueType = contract.valueType,
                displayOrder = binding.displayOrder,
                section = binding.section,
                isRequired = binding.isRequired,
                isReadOnly = binding.isReadOnly,
                defaultValueJson = binding.defaultValueJson,
                visibility = binding.visibility,
                description = contract.description,
                helpText = contract.helpText,
                constraints = FieldConstraints.parse(contract.constraintsJson),
                options = FieldOption.parseList(contract.optionsJson),
            )
        }
    }
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
