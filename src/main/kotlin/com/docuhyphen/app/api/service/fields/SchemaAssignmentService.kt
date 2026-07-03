package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.dto.FieldValueDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueProvenance
import com.docuhyphen.app.api.model.entity.FieldValueSelection
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.FieldContractRepository
import com.docuhyphen.app.api.repository.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.FieldValueRepository
import com.docuhyphen.app.api.repository.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.SchemaVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContextFactory
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
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
 * Assigns a published Schema Version to a resource (Exchange in the first release) and stores typed
 * field values against that assignment. Existence, authorization, and the editable-lifecycle rule
 * are delegated to the resource's [FieldResourceAdapter]; value validation to [FieldValueValidator].
 */
@ApplicationScoped
class SchemaAssignmentService @Inject constructor(
    private val adapterRegistry: FieldResourceAdapterRegistry,
    private val assignmentRepository: SchemaAssignmentRepository,
    private val schemaDefinitionRepository: SchemaDefinitionRepository,
    private val schemaVersionRepository: SchemaVersionRepository,
    private val bindingRepository: SchemaFieldBindingRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val fieldDefinitionRepository: FieldDefinitionRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val selectionRepository: FieldValueSelectionRepository,
    private val validator: FieldValueValidator,
    private val authorizationContextFactory: AuthorizationContextFactory,
)
{
    // ── Reads ─────────────────────────────────────────────────────────────────

    /** Current assignment and resolved values for a resource, or null if none is assigned. */
    fun getAssignment(resourceType: String, resourceId: UUID): SchemaAssignmentDto?
    {
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        val (principal, context) = principalAndContext()
        adapter.authorizeViewFields(resourceId, principal, context)

        val assignment = assignmentRepository.findByResource(resourceType, resourceId) ?: return null
        return assignment.toDto()
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    fun assignSchema(resourceType: String, resourceId: UUID, schemaDefinitionId: UUID): SchemaAssignmentDto =
        assignSchema(resourceType, resourceId, schemaDefinitionId, SchemaAssignmentSource.MANUAL)

    fun assignSchema(
        resourceType: String,
        resourceId: UUID,
        schemaDefinitionId: UUID,
        source: SchemaAssignmentSource,
    ): SchemaAssignmentDto
    {
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        val (principal, context) = principalAndContext()
        adapter.authorizeManageFields(resourceId, principal, context)
        if (!adapter.valuesEditable(resourceId))
            throw IllegalStateException("This resource can no longer have its schema changed")
        if (assignmentRepository.findByResource(resourceType, resourceId) != null)
            throw IllegalStateException("A schema is already assigned; remove it before assigning another")

        val definition = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        if (definition.status == FieldLifecycleStatus.RETIRED)
            throw FieldValidationException("Schema is retired and cannot be assigned")
        if (definition.targetResourceType != resourceType)
            throw FieldValidationException("Schema targets ${definition.targetResourceType}, not $resourceType")
        assertSchemaVisibleToResource(adapter, resourceId, definition)

        val version = schemaVersionRepository.findLatestPublished(schemaDefinitionId)
            ?: throw FieldValidationException("Schema has no published version to assign")

        val assignment = SchemaAssignment().apply {
            this.resourceType = resourceType
            this.resourceId = resourceId
            this.schemaVersionId = version.id
            this.scopeKind = definition.scopeKind
            this.scopeOrgId = definition.scopeOrgId
            this.assignmentSource = source
            this.assignedByAppUserId = principal.id
        }
        assignmentRepository.save(assignment)
        return assignment.toDto()
    }

    @Transactional
    fun unassignSchema(resourceType: String, resourceId: UUID)
    {
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        val (principal, context) = principalAndContext()
        adapter.authorizeManageFields(resourceId, principal, context)
        if (!adapter.valuesEditable(resourceId))
            throw IllegalStateException("This resource can no longer have its schema removed")

        val assignment = assignmentRepository.findByResource(resourceType, resourceId)
            ?: throw IllegalArgumentException("No schema is assigned")
        fieldValueRepository.findByAssignment(assignment.id).forEach { value ->
            selectionRepository.deleteByValue(value.id)
            fieldValueRepository.delete(value)
        }
        assignmentRepository.delete(assignment)
    }

    /** Upserts typed values for the resource's assigned schema. Read-only bindings are rejected. */
    @Transactional
    fun setValues(resourceType: String, resourceId: UUID, values: List<FieldValueEntry>): SchemaAssignmentDto
    {
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        val (principal, context) = principalAndContext()
        adapter.authorizeManageFields(resourceId, principal, context)
        if (!adapter.valuesEditable(resourceId))
            throw IllegalStateException("Field values can no longer be edited for this resource")

        val assignment = assignmentRepository.findByResource(resourceType, resourceId)
            ?: throw IllegalArgumentException("No schema is assigned")
        val bindings = bindingRepository.findByVersion(assignment.schemaVersionId)
            .associateBy { it.fieldContractId }

        for (entry in values)
        {
            val binding = bindings[entry.fieldContractId]
                ?: throw FieldValidationException("Field ${entry.fieldContractId} is not part of the assigned schema")
            if (binding.isReadOnly)
                throw FieldValidationException("Field ${entry.fieldContractId} is read-only")
            val contract = fieldContractRepository.findById(entry.fieldContractId)
                ?: throw FieldValidationException("Unknown field contract: ${entry.fieldContractId}")

            val canonical = validator.canonicalize(contract, entry.value)
            if (canonical.isEmpty && binding.isRequired)
                throw FieldValidationException("${contract.label} is required")

            upsertValue(assignment, resourceType, resourceId, contract, binding, canonical, principal)
        }
        return assignment.toDto()
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun upsertValue(
        assignment: SchemaAssignment,
        resourceType: String,
        resourceId: UUID,
        contract: FieldContract,
        binding: SchemaFieldBinding,
        canonical: CanonicalFieldValue,
        principal: PrincipalRef,
    )
    {
        val existing = fieldValueRepository.findByAssignmentAndContract(assignment.id, contract.id)
        val value = existing ?: FieldValue().apply {
            this.schemaAssignmentId = assignment.id
            this.schemaFieldBindingId = binding.id
            this.fieldContractId = contract.id
            this.resourceType = resourceType
            this.resourceId = resourceId
        }
        CanonicalValueCodec.applyTo(value, canonical)
        value.schemaFieldBindingId = binding.id
        value.provenance = FieldValueProvenance.USER
        value.updatedAt = Timestamp.from(Instant.now())
        value.updatedByAppUserId = principal.id
        val saved = if (existing != null) fieldValueRepository.update(value) else fieldValueRepository.save(value)

        selectionRepository.deleteByValue(saved.id)
        canonical.selectionCodes.forEachIndexed { index, code ->
            selectionRepository.save(FieldValueSelection().apply {
                this.fieldValueId = saved.id
                this.optionCode = code
                this.displayOrder = index
            })
        }
    }

    private fun assertSchemaVisibleToResource(
        adapter: FieldResourceAdapter,
        resourceId: UUID,
        definition: SchemaDefinition,
    )
    {
        if (definition.scopeKind == FieldScopeKind.PLATFORM) return
        val scope = adapter.ownerScope(resourceId)
        val orgId = (scope as? com.docuhyphen.app.api.service.auth.authz.ScopeReference.Organization)?.organizationId
        if (orgId == null || definition.scopeOrgId != orgId)
            throw FieldValidationException("Schema is not available for this resource's organization")
    }

    private fun principalAndContext(): Pair<PrincipalRef, AuthorizationContext>
    {
        val principal = authorizationContextFactory.currentPrincipal()
            ?: throw ForbiddenException("Not authenticated")
        return principal to authorizationContextFactory.currentContext()
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private fun SchemaAssignment.toDto(): SchemaAssignmentDto
    {
        val version = schemaVersionRepository.findById(schemaVersionId)
            ?: throw IllegalStateException("Assigned schema version missing")
        val definition = schemaDefinitionRepository.findById(version.schemaDefinitionId)
            ?: throw IllegalStateException("Schema definition missing")
        return SchemaAssignmentDto(
            id = id,
            resourceType = resourceType,
            resourceId = resourceId,
            schemaVersionId = schemaVersionId,
            schemaDefinitionId = definition.id,
            schemaKey = definition.schemaKey,
            displayName = definition.displayName,
            versionNumber = version.versionNumber,
            assignmentSource = assignmentSource,
            assignedAt = assignedAt,
            fields = resolveValues(this, version),
        )
    }

    /** One [FieldValueDto] per binding in the assigned version, empty where no value is stored. */
    private fun resolveValues(assignment: SchemaAssignment, version: SchemaVersion): List<FieldValueDto>
    {
        val bindings = bindingRepository.findByVersion(version.id)
        if (bindings.isEmpty()) return emptyList()
        val contracts = fieldContractRepository.findByIds(bindings.map { it.fieldContractId })
            .associateBy { it.id }
        val definitions = contracts.values
            .mapNotNull { fieldDefinitionRepository.findById(it.fieldDefinitionId) }
            .associateBy { it.id }
        val valuesByContract = fieldValueRepository.findByAssignment(assignment.id)
            .associateBy { it.fieldContractId }

        return bindings.sortedBy { it.displayOrder }.mapNotNull { binding ->
            val contract = contracts[binding.fieldContractId] ?: return@mapNotNull null
            val fieldDef = definitions[contract.fieldDefinitionId] ?: return@mapNotNull null
            val stored = valuesByContract[contract.id]
            val codes = stored?.let { selectionRepository.findByValue(it.id).map { s -> s.optionCode } } ?: emptyList()
            val json: JsonElement = stored?.let { CanonicalValueCodec.toJson(it, codes) }
                ?: kotlinx.serialization.json.JsonNull
            val isEmpty = stored == null || CanonicalValueCodec.isEmpty(stored, codes)
            FieldValueDto(
                fieldContractId = contract.id,
                schemaFieldBindingId = binding.id,
                namespace = fieldDef.namespace,
                fieldKey = fieldDef.fieldKey,
                label = contract.label,
                valueType = contract.valueType,
                isEmpty = isEmpty,
                value = json,
            )
        }
    }
}

// ── Request DTOs ─────────────────────────────────────────────────────────────

@Serializable
data class FieldValueEntry(
    @Serializable(with = com.docuhyphen.app.api.serializer.UUIDSerializer::class)
    val fieldContractId: UUID,
    val value: JsonElement,
)

@Serializable
data class SetFieldValuesRequest(
    val values: List<FieldValueEntry> = emptyList(),
)

@Serializable
data class AssignSchemaRequest(
    @Serializable(with = com.docuhyphen.app.api.serializer.UUIDSerializer::class)
    val schemaDefinitionId: UUID,
)
