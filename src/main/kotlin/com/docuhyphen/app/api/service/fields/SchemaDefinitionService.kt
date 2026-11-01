package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.SchemaDefinitionDtoMapper
import com.docuhyphen.app.api.model.SchemaFieldBindingDtoMapper
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.model.dto.ResolvedSchemaViewDto
import com.docuhyphen.app.api.model.dto.SchemaDefinitionDto
import com.docuhyphen.app.api.model.dto.SchemaFieldBindingDto
import com.docuhyphen.app.api.model.dto.SchemaVersionDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.FieldConstraints
import com.docuhyphen.app.api.model.fields.ValidatedSchemaDefault
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.resource.model.BindingRequest
import com.docuhyphen.app.api.resource.model.CreateSchemaRequest
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
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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

    private companion object {
        const val SECTION_LIMIT = 128
    }

    fun listSchemas(scopeKind: FieldScopeKind? = null): List<SchemaDefinitionDto>
    {
        val principal = currentPrincipal()
        if (scopeKind == FieldScopeKind.PERSONAL)
        {
            return schemaDefinitionRepository.findAllForUser(principal.id).map(::definitionDto)
        }
        if (scopeKind == FieldScopeKind.PLATFORM)
        {
            if (!userRoleService.isAppAdmin(principal.id))
                throw ForbiddenException("Platform schema configuration requires App Administrator access")
            return schemaDefinitionRepository.findAllPlatform().map(::definitionDto)
        }
        val orgId = currentContext().activeOrgId
        val schemas = if (orgId != null && hasOrganizationAccess(principal, orgId, Action.FIELD_CONFIG_VIEW))
            schemaDefinitionRepository.findAllForOrganization(orgId)
        else if (userRoleService.isAppAdmin(principal.id))
            schemaDefinitionRepository.findAllPlatform()
        else
            throw ForbiddenException("Access denied to schema configuration")
        return schemas.map(::definitionDto)
    }

    fun getSchema(id: UUID): SchemaDefinitionDto
    {
        val def = schemaDefinitionRepository.findById(id)
            ?: throw IllegalArgumentException("Schema not found: $id")
        requireReadAccess(def, null)
        return definitionDto(def)
    }

    fun getVersion(versionId: UUID): SchemaVersionDto {
        val version = schemaVersionRepository.findById(versionId)
            ?: throw IllegalArgumentException("Schema version not found: $versionId")
        val definition = schemaDefinitionRepository.findById(version.schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: ${version.schemaDefinitionId}")
        requireReadAccess(definition, version)
        return versionDto(version)
    }

    fun getResolvedLatestPublished(schemaDefinitionId: UUID): ResolvedSchemaViewDto
    {
        val def = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        requireScopeAccess(def, Action.FIELD_CONFIG_VIEW)
        val version = schemaVersionRepository.findLatestPublished(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema has no published version")
        return resolvedView(def, version)
    }

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

        recordSchemaEvent(
            AuditEventType.SCHEMA_DEFINITION_CREATE,
            definition.id,
            definition.displayName,
            principal.id,
            scopeOrgId
        )
        return definitionDto(definition)
    }

    @Transactional
    fun updateDraftBindings(schemaDefinitionId: UUID, bindings: List<BindingRequest>): SchemaDefinitionDto
    {
        val def = schemaDefinitionRepository.findByIdForUpdate(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        requireScopeAccess(def, Action.FIELD_CONFIG_EDIT)
        subscriptionGuard.requireConfigurationMutation(def.scopeKind, def.scopeOrgId, def.scopeUserId)
        val draft = schemaVersionRepository.findDraft(schemaDefinitionId)
            ?: throw IllegalStateException("Schema has no editable draft version")
        bindingRepository.deleteByVersion(draft.id)
        replaceBindings(draft, def.scopeKind, def.scopeOrgId, bindings)
        def.updatedAt = Timestamp.from(Instant.now())
        schemaDefinitionRepository.update(def)
        return definitionDto(def)
    }

    @Transactional
    fun createDraftVersion(schemaDefinitionId: UUID): SchemaDefinitionDto
    {
        val def = schemaDefinitionRepository.findByIdForUpdate(schemaDefinitionId)
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
        return definitionDto(def)
    }

    @Transactional
    fun publishDraft(schemaDefinitionId: UUID, compatibility: SchemaCompatibility?): SchemaDefinitionDto
    {
        val principal = currentPrincipal()
        val def = schemaDefinitionRepository.findByIdForUpdate(schemaDefinitionId)
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
        recordSchemaEvent(
            AuditEventType.SCHEMA_DEFINITION_PUBLISH,
            def.id,
            def.displayName,
            principal.id,
            def.scopeOrgId
        )
        return definitionDto(def)
    }

    @Transactional
    fun retireSchema(schemaDefinitionId: UUID): SchemaDefinitionDto
    {
        val principal = currentPrincipal()
        val def = schemaDefinitionRepository.findByIdForUpdate(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        requireScopeAccess(def, Action.FIELD_CONFIG_PUBLISH)
        subscriptionGuard.requireConfigurationMutation(def.scopeKind, def.scopeOrgId, def.scopeUserId)
        def.status = FieldLifecycleStatus.RETIRED
        def.updatedAt = Timestamp.from(Instant.now())
        val updated = schemaDefinitionRepository.update(def)
        recordSchemaEvent(
            AuditEventType.SCHEMA_DEFINITION_RETIRE,
            updated.id,
            updated.displayName,
            principal.id,
            updated.scopeOrgId
        )
        return definitionDto(updated)
    }

    private fun replaceBindings(
        version: SchemaVersion,
        scopeKind: FieldScopeKind,
        scopeOrgId: UUID?,
        bindings: List<BindingRequest>,
    )
    {
        if (bindings.map { it.fieldContractId }.toSet().size != bindings.size)
            throw FieldValidationException("A field may only be bound once per schema version")

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
            val visible = fieldDef.scopeKind == FieldScopeKind.PLATFORM ||
                    (scopeKind == FieldScopeKind.ORGANIZATION &&
                            fieldDef.scopeKind == FieldScopeKind.ORGANIZATION &&
                            fieldDef.scopeOrgId == scopeOrgId)
            if (!visible)
                throw FieldValidationException("Field ${fieldDef.namespace}:${fieldDef.fieldKey} is not available in this scope")
            val section = req.section?.trim()?.ifBlank { null }
            if (section != null && section.length > SECTION_LIMIT)
                throw FieldValidationException(
                    "The section of ${fieldDef.namespace}:${fieldDef.fieldKey} must be at most $SECTION_LIMIT characters",
                )
            val defaultValueJson = canonicalDefault(contract, fieldDef, req.defaultValueJson)

            bindingRepository.save(SchemaFieldBinding().apply {
                this.schemaVersionId = version.id
                this.fieldContractId = contract.id
                this.fieldDefinitionId = fieldDef.id
                this.displayOrder = if (req.displayOrder >= 0) req.displayOrder else index
                this.section = section
                this.isRequired = req.isRequired
                this.isReadOnly = req.isReadOnly
                this.defaultValueJson = defaultValueJson
                this.visibility = req.visibility ?: contract.dataClassification
            })
        }
    }

    private fun canonicalDefault(contract: FieldContract, fieldDef: FieldDefinition, raw: String?): String? {
        val supplied = raw?.takeIf { it.isNotBlank() } ?: return null
        val key = "${fieldDef.namespace}:${fieldDef.fieldKey}"
        val parsed = try {
            Json.parseToJsonElement(supplied).takeIf(::isWellFormed)
        } catch (refused: SerializationException) {
            null
        } ?: throw FieldValidationException("The default value of $key is not valid JSON")
        val canonical = try {
            fieldValueValidator.canonicalize(contract, parsed)
        } catch (refused: FieldValidationException) {
            throw FieldValidationException("The default value of $key is not valid: ${refused.message}")
        }
        if (canonical.isEmpty) return null
        return CanonicalValueCodec.toJson(canonical, FieldConstraints.parse(contract.constraintsJson).scale).toString()
    }

    private fun isWellFormed(element: JsonElement): Boolean = when (element) {
        is JsonNull -> true
        is JsonPrimitive -> element.isString || element.booleanOrNull != null || element.content.toBigDecimalOrNull() != null
        is JsonArray -> element.all(::isWellFormed)
        is JsonObject -> element.values.all(::isWellFormed)
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

    private fun requireReadAccess(definition: SchemaDefinition, version: SchemaVersion?) {
        val publishedByPlatform = definition.scopeKind == FieldScopeKind.PLATFORM &&
                version?.status != FieldLifecycleStatus.DRAFT
        if (publishedByPlatform) {
            currentPrincipal()
            return
        }
        requireScopeAccess(definition, Action.FIELD_CONFIG_VIEW)
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
                                hasOrganizationAccess(principal, activeOrgId, Action.FIELD_CONFIG_VIEW))
                    )
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
                    hasOrganizationAccess(principal, organizationId, action)
                )
                    return
            }
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
        return hasOrganizationCapability && authorizationService.authorize(
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
            logger.error(
                "SchemaDefinitionService: AuditRecorder capture failed for {}: {}",
                eventType.key,
                e.message,
                e
            )
        }
    }

    private fun definitionDto(definition: SchemaDefinition): SchemaDefinitionDto =
        SchemaDefinitionDtoMapper.toDto(
            definition,
            schemaVersionRepository.findDraft(definition.id)?.let(::versionDto),
            schemaVersionRepository.findLatestPublished(definition.id)?.let(::versionDto),
        )

    private fun versionDto(version: SchemaVersion): SchemaVersionDto =
        SchemaDefinitionDtoMapper.toVersionDto(version, mapBindings(version.id))

    private fun resolvedView(def: SchemaDefinition, version: SchemaVersion): ResolvedSchemaViewDto =
        SchemaDefinitionDtoMapper.toResolvedViewDto(def, version, mapBindings(version.id))

    private fun mapBindings(schemaVersionId: UUID): List<SchemaFieldBindingDto> =
        projectionLoader.resolveBindings(schemaVersionId).map(SchemaFieldBindingDtoMapper::toDto)
}

