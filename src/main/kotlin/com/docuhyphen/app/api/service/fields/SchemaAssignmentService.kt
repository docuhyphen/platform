package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.SchemaAssignmentDtoMapper
import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.dto.SchemaAssignmentStateDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.repository.fields.*
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@ApplicationScoped
class SchemaAssignmentService @Inject constructor(
    private val adapterRegistry: FieldResourceAdapterRegistry,
    private val assignmentRepository: SchemaAssignmentRepository,
    private val schemaDefinitionRepository: SchemaDefinitionRepository,
    private val schemaVersionRepository: SchemaVersionRepository,
    private val bindingRepository: SchemaFieldBindingRepository,
    private val fieldContractRepository: FieldContractRepository,
    private val fieldValueRepository: FieldValueRepository,
    private val fieldValueSetRepository: FieldValueSetRepository,
    private val selectionRepository: FieldValueSelectionRepository,
    private val projectionLoader: FieldsProjectionLoader,
    private val validator: FieldValueValidator,
    private val subscriptionGuard: BusinessFieldsSubscriptionGuard,
    private val revisionRecorder: FieldValueRevisionRecorder,
    private val auditTrail: SchemaAssignmentAuditTrail,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(SchemaAssignmentService::class.java)
    }

    fun getAssignment(command: FieldValueReadCommand): SchemaAssignmentDto?
    {
        val resourceType = command.resource.resourceType
        val resourceId = command.resource.resourceId
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        adapter.authorizeViewFields(resourceId, command.access.principal, command.access.authorization)

        val assignment = assignmentRepository.findByResource(resourceType, resourceId) ?: return null
        return project(
            assignment,
            valueSetForRead(assignment, command.valueSet),
            gateFor(adapter, command.resource, command.access, command.valueSet),
        )
    }

    fun getAssignmentState(command: FieldValueReadCommand): SchemaAssignmentStateDto {
        val assignment = getAssignment(command)
        val adapter = adapterRegistry.adapterFor(command.resource.resourceType)
        return SchemaAssignmentDtoMapper.toStateDto(
            assignment,
            capabilitiesOf(adapter, command.resource.resourceId, command.access),
        )
    }

    @Transactional
    fun applySchemaAssignment(command: SchemaAssignmentCommand): SchemaAssignmentDto? =
        when (command.operation)
        {
            SchemaAssignmentOperation.ASSIGN -> assignSchema(command)
            SchemaAssignmentOperation.UNASSIGN ->
            {
                unassignSchema(command)
                null
            }
        }

    @Transactional
    fun assignSchemaVersionForCreation(command: PublishedSchemaAssignmentCommand): SchemaAssignmentDto
    {
        val adapter = adapterRegistry.adapterFor(command.resource.resourceType)
        if (!adapter.exists(command.resource.resourceId)) throw IllegalArgumentException("Resource not found: ${command.resource.resourceId}")
        return assignPublishedVersion(command, adapter)
    }

    private fun assignPublishedVersion(
        command: PublishedSchemaAssignmentCommand,
        adapter: FieldResourceAdapter
    ): SchemaAssignmentDto
    {
        val resourceType = command.resource.resourceType
        val resourceId = command.resource.resourceId
        val schemaVersionId = command.schemaVersionId
        if (!adapter.schemaAssignmentMutable(resourceId))
            throw IllegalStateException("This resource can no longer have its schema changed")
        requireNoAssignment(resourceType, resourceId, command.precondition)

        val version = schemaVersionRepository.findById(schemaVersionId)
            ?: throw FieldValidationException("Schema version not found: $schemaVersionId")
        val definition = schemaDefinitionRepository.findById(version.schemaDefinitionId)
            ?: throw FieldValidationException("Schema version $schemaVersionId belongs to a schema that no longer exists")
        assertSchemaVisibleToResource(adapter, resourceId, definition)
        if (version.status != FieldLifecycleStatus.PUBLISHED)
            throw FieldValidationException("Schema version $schemaVersionId is not published")
        if (definition.status == FieldLifecycleStatus.RETIRED)
            throw FieldValidationException("Schema is retired and cannot be assigned")
        if (definition.targetResourceType != resourceType)
            throw FieldValidationException("Schema targets ${definition.targetResourceType}, not $resourceType")
        adapter.validateSchemaVersionAssignment(resourceId, version.id)

        return storeAssignment(
            resourceType = resourceType,
            resourceId = resourceId,
            source = command.source,
            access = command.access,
            definition = definition,
            schemaVersionId = version.id,
            adapter = adapter,
        )
    }

    private fun assignSchema(command: SchemaAssignmentCommand): SchemaAssignmentDto
    {
        val resourceType = command.resource.resourceType
        val resourceId = command.resource.resourceId
        val schemaDefinitionId = command.schemaDefinitionId
            ?: throw FieldValidationException("A schema must be named to assign one")
        val source = command.source
        val principal = command.access.principal
        val context = command.access.authorization
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        adapter.authorizeManageFields(resourceId, principal, context)
        adapter.authorizeManageSchema(resourceId, principal, context)
        if (!adapter.schemaAssignmentMutable(resourceId))
            throw IllegalStateException("This resource can no longer have its schema changed")
        if (!adapter.mutationEntitlementFrozen(resourceId))
            subscriptionGuard.requireResourceMutation(adapter.subscriptionContext(resourceId))
        requireNoAssignment(resourceType, resourceId, command.precondition)

        val definition = schemaDefinitionRepository.findById(schemaDefinitionId)
            ?: throw IllegalArgumentException("Schema not found: $schemaDefinitionId")
        assertSchemaVisibleToResource(adapter, resourceId, definition)
        if (definition.status == FieldLifecycleStatus.RETIRED)
            throw FieldValidationException("Schema is retired and cannot be assigned")
        if (definition.targetResourceType != resourceType)
            throw FieldValidationException("Schema targets ${definition.targetResourceType}, not $resourceType")

        val version = schemaVersionRepository.findLatestPublished(schemaDefinitionId)
            ?: throw FieldValidationException("Schema has no published version to assign")
        adapter.validateSchemaVersionAssignment(resourceId, version.id)

        return storeAssignment(
            resourceType = resourceType,
            resourceId = resourceId,
            source = source,
            access = command.access,
            definition = definition,
            schemaVersionId = version.id,
            adapter = adapter,
        )
    }

    private fun storeAssignment(
        resourceType: String,
        resourceId: UUID,
        source: SchemaAssignmentSource,
        access: FieldsAccessContext,
        definition: SchemaDefinition,
        schemaVersionId: UUID,
        adapter: FieldResourceAdapter,
    ): SchemaAssignmentDto
    {
        val provenance = FieldPrincipalProvenance.of(access.principal, access.authorization)
        val assignment = SchemaAssignment().apply {
            this.resourceType = resourceType
            this.resourceId = resourceId
            this.schemaVersionId = schemaVersionId
            this.scopeKind = definition.scopeKind
            this.scopeOrgId = definition.scopeOrgId
            this.scopeUserId = definition.scopeUserId
            this.assignmentSource = source
        }
        provenance.recordOn(assignment)
        assignmentRepository.save(assignment)
        val rootValueSet = createRootValueSet(assignment)
        val defaulted = materializeConfiguredDefaults(assignment, rootValueSet, resourceType, resourceId, provenance)
        val ownerScope = adapter.ownerScope(resourceId)
        auditTrail.schemaAssigned(assignment, ownerScope, provenance, definition.displayName, defaulted.size)
        if (defaulted.isNotEmpty())
            auditTrail.valuesUpdated(
                assignment, ownerScope, provenance, definition.displayName, FieldValueProvenance.SCHEMA_DEFAULT,
                defaulted.size, 0, fieldKeysOf(assignment, defaulted),
            )
        return project(
            assignment,
            rootValueSet,
            gateFor(adapter, FieldsResourceRef(resourceType, resourceId), access, FieldValueSetRef.Root),
        )
    }

    private fun unassignSchema(command: SchemaAssignmentCommand)
    {
        val resourceType = command.resource.resourceType
        val resourceId = command.resource.resourceId
        val principal = command.access.principal
        val context = command.access.authorization
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        adapter.authorizeManageFields(resourceId, principal, context)
        adapter.authorizeManageSchema(resourceId, principal, context)
        if (!adapter.schemaAssignmentMutable(resourceId))
            throw IllegalStateException("This resource can no longer have its schema removed")
        if (!adapter.mutationEntitlementFrozen(resourceId))
            subscriptionGuard.requireResourceMutation(adapter.subscriptionContext(resourceId))

        assignmentRepository.lockResource(resourceType, resourceId)
        val assignment = assignmentRepository.findByResource(resourceType, resourceId)
            ?: throw IllegalArgumentException("No schema is assigned")
        command.precondition.requireSatisfiedBy(answersETagOf(assignment))
        val schemaDisplayName = schemaDisplayNameOf(assignment)
        val removedValues = fieldValueRepository.findByAssignment(assignment.id)
        removedValues.forEach { value ->
            selectionRepository.deleteByValue(value.id)
            fieldValueRepository.delete(value)
        }
        fieldValueSetRepository.findByAssignment(assignment.id).forEach(fieldValueSetRepository::delete)
        assignmentRepository.delete(assignment)
        auditTrail.schemaUnassigned(
            assignment, adapter.ownerScope(resourceId), FieldPrincipalProvenance.of(principal, context),
            schemaDisplayName, removedValues.size,
        )
    }

    @Transactional
    fun clearValues(command: com.docuhyphen.app.api.model.fields.FieldValueClearCommand): SchemaAssignmentDto =
        writeValues(
            FieldValueWriteCommand(
                command.resource, command.access,
                command.fieldContractIds.map { FieldValueEntry(it, kotlinx.serialization.json.JsonNull) },
                command.valueSet, command.precondition
            ), allowRequiredClear = true
        )

    @Transactional
    fun setValues(command: FieldValueWriteCommand): SchemaAssignmentDto =
        writeValues(command, allowRequiredClear = false)

    private fun writeValues(command: FieldValueWriteCommand, allowRequiredClear: Boolean): SchemaAssignmentDto
    {
        val resourceType = command.resource.resourceType
        val resourceId = command.resource.resourceId
        val values = command.entries
        val principal = command.access.principal
        val context = command.access.authorization
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        adapter.authorizeManageFields(resourceId, principal, context)
        if (!adapter.valuesEditable(resourceId))
            throw IllegalStateException("Field values can no longer be edited for this resource")
        if (!adapter.mutationEntitlementFrozen(resourceId))
            subscriptionGuard.requireResourceMutation(adapter.subscriptionContext(resourceId))

        val assignment = assignmentRepository.findByResource(resourceType, resourceId)
            ?: throw IllegalArgumentException("No schema is assigned")
        val valueSet = valueSetForWrite(assignment, command.valueSet)
        command.precondition.requireSatisfiedBy(FieldValueSetETag.of(valueSet))
        val gate = gateFor(adapter, command.resource, command.access, command.valueSet)
        val provenance = FieldPrincipalProvenance.of(principal, context)
        val bindings = bindingRepository.findByVersion(assignment.schemaVersionId)
            .associateBy { it.fieldContractId }

        val changed = mutableListOf<UUID>()
        var clearedCount = 0
        var storedAt: Timestamp? = null
        for (entry in values)
        {
            val binding = bindings[entry.fieldContractId]
                ?: throw FieldValidationException("Field ${entry.fieldContractId} is not part of the assigned schema")
            refuseUnlessWritable(gate, binding, entry.fieldContractId)
            val contract = fieldContractRepository.findById(entry.fieldContractId)
                ?: throw FieldValidationException("Unknown field contract: ${entry.fieldContractId}")

            val canonical = validator.canonicalize(contract, entry.value)
            if (canonical.isEmpty && binding.isRequired && !allowRequiredClear)
                throw FieldValidationException("${contract.label} is required")

            val stored = upsertValue(
                valueSet, resourceType, resourceId, contract, binding, canonical, provenance,
                FieldValueProvenance.USER,
            )
            if (stored != null)
            {
                changed += contract.id
                storedAt = stored.updatedAt
                if (canonical.isEmpty) clearedCount++
            }
        }
        storedAt?.let { advanceValueSet(valueSet, it) }
        if (changed.isNotEmpty())
            auditTrail.valuesUpdated(
                assignment, adapter.ownerScope(resourceId), provenance, schemaDisplayNameOf(assignment),
                FieldValueProvenance.USER, changed.size, clearedCount, fieldKeysOf(assignment, changed),
            )
        return project(assignment, valueSet, gate)
    }

    @Transactional
    fun applyBlueprintFieldDefaultsForCreation(command: BlueprintFieldDefaultsCommand): SchemaAssignmentDto?
    {
        if (command.defaults.isEmpty()) return null

        val resourceType = command.resource.resourceType
        val resourceId = command.resource.resourceId
        val principal = command.access.principal
        val context = command.access.authorization
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        if (!adapter.schemaAssignmentMutable(resourceId))
            throw IllegalStateException("This resource can no longer accept Blueprint field defaults")

        val assignment = assignmentRepository.findByResource(resourceType, resourceId) ?: return null
        val valueSet = valueSetForWrite(assignment, FieldValueSetRef.Root)
        val provenance = FieldPrincipalProvenance.of(principal, context)
        val bindingsByFieldDefinition = bindingRepository.findByVersion(assignment.schemaVersionId)
            .associateBy { it.fieldDefinitionId }

        val stored = mutableListOf<UUID>()
        var storedAt: Timestamp? = null
        command.defaults.forEach { default ->
            val binding = bindingsByFieldDefinition[default.fieldDefinitionId] ?: return@forEach
            if (binding.isReadOnly) return@forEach
            val contract = fieldContractRepository.findById(binding.fieldContractId) ?: return@forEach
            val canonical = try {
                validator.canonicalize(contract, default.value)
            } catch (e: FieldValidationException) {
                logger.warn(
                    "Skipping a Blueprint default that schema version {} binding {} cannot hold: {}",
                    assignment.schemaVersionId, binding.id, e.message,
                )
                return@forEach
            }
            if (canonical.isEmpty) return@forEach
            val value = upsertValue(
                valueSet, resourceType, resourceId, contract, binding, canonical, provenance,
                FieldValueProvenance.BLUEPRINT_DEFAULT,
            ) ?: return@forEach
            stored += contract.id
            storedAt = value.updatedAt
        }
        storedAt?.let { advanceValueSet(valueSet, it) }
        if (stored.isNotEmpty())
            auditTrail.valuesUpdated(
                assignment, adapter.ownerScope(resourceId), provenance, schemaDisplayNameOf(assignment),
                FieldValueProvenance.BLUEPRINT_DEFAULT, stored.size, 0, fieldKeysOf(assignment, stored),
            )
        return project(
            assignment,
            valueSet,
            gateFor(adapter, command.resource, command.access, FieldValueSetRef.Root),
        )
    }

    @Transactional
    fun createOccurrenceValueSet(resource: FieldsResourceRef, occurrencePath: String): FieldValueSetRef
    {
        val assignment = assignmentRepository.findByResource(resource.resourceType, resource.resourceId)
            ?: throw IllegalStateException("Resource has no schema assignment: ${resource.resourceId}")
        fieldValueSetRepository.findOccurrence(assignment.id, occurrencePath)
            ?: fieldValueSetRepository.save(
                FieldValueSet().apply {
                    schemaAssignmentId = assignment.id
                    setKind = FieldValueSetKind.OCCURRENCE
                    this.occurrencePath = occurrencePath
                },
            )
        return FieldValueSetRef.Occurrence(occurrencePath)
    }

    private fun materializeConfiguredDefaults(
        assignment: SchemaAssignment,
        valueSet: FieldValueSet,
        resourceType: String,
        resourceId: UUID,
        provenance: FieldPrincipalProvenance,
    ): List<UUID>
    {
        val stored = mutableListOf<UUID>()
        var storedAt: Timestamp? = null
        for (binding in bindingRepository.findByVersion(assignment.schemaVersionId))
        {
            val configured = binding.defaultValueJson?.takeIf { it.isNotBlank() } ?: continue
            val contract = fieldContractRepository.findById(binding.fieldContractId) ?: continue
            val canonical = try
            {
                validator.canonicalize(contract, FieldsJson.instance.parseToJsonElement(configured))
            }
            catch (e: Exception)
            {
                logger.warn(
                    "Skipping unusable default value on schema version {} binding {}: {}",
                    assignment.schemaVersionId, binding.id, e.message,
                )
                continue
            }
            if (canonical.isEmpty) continue
            val value = upsertValue(
                valueSet, resourceType, resourceId, contract, binding, canonical, provenance,
                FieldValueProvenance.SCHEMA_DEFAULT,
            ) ?: continue
            stored += contract.id
            storedAt = value.updatedAt
        }
        storedAt?.let { advanceValueSet(valueSet, it) }
        return stored
    }

    private fun upsertValue(
        valueSet: FieldValueSet,
        resourceType: String,
        resourceId: UUID,
        contract: FieldContract,
        binding: SchemaFieldBinding,
        canonical: CanonicalFieldValue,
        provenance: FieldPrincipalProvenance,
        valueProvenance: FieldValueProvenance,
    ): FieldValue?
    {
        val existing = fieldValueRepository.findByValueSetAndContract(valueSet.id, contract.id)
        if (existing != null && recordsSameState(existing, canonical, provenance, valueProvenance)) return null

        val value = existing ?: FieldValue().apply {
            this.fieldValueSetId = valueSet.id
            this.schemaAssignmentId = valueSet.schemaAssignmentId
            this.schemaFieldBindingId = binding.id
            this.fieldContractId = contract.id
            this.resourceType = resourceType
            this.resourceId = resourceId
        }
        CanonicalValueCodec.applyTo(value, canonical)
        value.schemaFieldBindingId = binding.id
        value.provenance = valueProvenance
        value.updatedAt = Timestamp.from(Instant.now())
        provenance.recordOn(value)
        val saved = if (existing != null) fieldValueRepository.update(value) else fieldValueRepository.save(value)

        selectionRepository.deleteByValue(saved.id)
        canonical.selectionCodes.forEachIndexed { index, code ->
            selectionRepository.save(FieldValueSelection().apply {
                this.fieldValueId = saved.id
                this.optionCode = code
                this.displayOrder = index
            })
        }

        revisionRecorder.record(saved, canonical, provenance)
        return saved
    }

    private fun advanceValueSet(valueSet: FieldValueSet, storedAt: Timestamp)
    {
        valueSet.updatedAt = storedAt
        valueSet.revision += 1
        fieldValueSetRepository.update(valueSet)
    }

    private fun recordsSameState(
        stored: FieldValue,
        canonical: CanonicalFieldValue,
        provenance: FieldPrincipalProvenance,
        valueProvenance: FieldValueProvenance,
    ): Boolean
    {
        if (stored.valueType != canonical.type) return false
        if (stored.provenance != valueProvenance) return false
        if (!provenance.matches(stored)) return false

        val storedCodes = if (holdsSelections(stored.valueType))
            selectionRepository.findByValue(stored.id).map { it.optionCode }
        else emptyList()
        if (storedCodes != canonical.selectionCodes) return false
        if (CanonicalValueCodec.isEmpty(stored, storedCodes) != canonical.isEmpty) return false

        return stored.textValue == canonical.textValue &&
                numbersMatch(stored.numberValue, canonical.numberValue) &&
                stored.boolValue == canonical.boolValue &&
                stored.dateValue == canonical.dateValue &&
                stored.datetimeValue?.toInstant() == canonical.datetimeValue &&
                stored.datetimeOffsetMinutes == canonical.datetimeOffsetMinutes
    }

    private fun holdsSelections(valueType: FieldValueType): Boolean =
        valueType == FieldValueType.SINGLE_SELECT || valueType == FieldValueType.MULTI_SELECT

    private fun numbersMatch(stored: BigDecimal?, canonical: BigDecimal?): Boolean = when
    {
        stored == null || canonical == null -> stored == null && canonical == null
        else -> stored.compareTo(canonical) == 0
    }

    private fun requireNoAssignment(resourceType: String, resourceId: UUID, precondition: FieldsPrecondition) {
        assignmentRepository.lockResource(resourceType, resourceId)
        val existing = assignmentRepository.findByResource(resourceType, resourceId)
        precondition.requireSatisfiedBy(existing?.let(::answersETagOf))
        if (existing != null)
            throw SchemaAssignmentConflictException(
                "A schema is already assigned; remove it before assigning another",
                SchemaAssignmentConflictException.SCHEMA_ALREADY_ASSIGNED,
            )
    }

    private fun capabilitiesOf(
        adapter: FieldResourceAdapter,
        resourceId: UUID,
        access: FieldsAccessContext,
    ): SchemaAssignmentCapabilities {
        val mayManageFields =
            permits { adapter.authorizeManageFields(resourceId, access.principal, access.authorization) }
        return SchemaAssignmentCapabilities(
            canManageSchema = mayManageFields && adapter.schemaAssignmentMutable(resourceId) &&
                    permits { adapter.authorizeManageSchema(resourceId, access.principal, access.authorization) },
            canEditValues = mayManageFields && adapter.valuesEditable(resourceId),
        )
    }

    private fun permits(check: () -> Unit): Boolean =
        try {
            check()
            true
        } catch (refused: ForbiddenException) {
            false
        }

    private fun fieldKeysOf(assignment: SchemaAssignment, contractIds: Collection<UUID>): List<String> =
        projectionLoader.resolveBindings(assignment.schemaVersionId) { it.fieldContractId in contractIds }
            .map { "${it.definition.namespace}:${it.definition.fieldKey}" }
            .sorted()

    private fun schemaDisplayNameOf(assignment: SchemaAssignment): String?
    {
        val version = schemaVersionRepository.findById(assignment.schemaVersionId) ?: return null
        return schemaDefinitionRepository.findById(version.schemaDefinitionId)?.displayName
    }

    private fun gateFor(
        adapter: FieldResourceAdapter,
        resource: FieldsResourceRef,
        access: FieldsAccessContext,
        valueSet: FieldValueSetRef,
    ) = FieldBindingGate(adapter.bindingPolicy, resource, access, valueSet)

    private fun refuseUnlessWritable(gate: FieldBindingGate, binding: SchemaFieldBinding, fieldContractId: UUID)
    {
        val decision = gate.decide(binding, FieldValueOperation.WRITE)
        if (decision !is FieldBindingDecision.Deny) return
        throw when (decision.reason)
        {
            FieldBindingDenial.OUT_OF_AUDIENCE ->
                FieldValidationException("Field $fieldContractId is not part of the assigned schema")

            FieldBindingDenial.READ_ONLY ->
                FieldValidationException("Field $fieldContractId is read-only")
        }
    }

    private fun valueSetForRead(assignment: SchemaAssignment, ref: FieldValueSetRef): FieldValueSet? = when (ref)
    {
        FieldValueSetRef.Root -> fieldValueSetRepository.findRoot(assignment.id) ?: throw missingRootSet(assignment)
        is FieldValueSetRef.Occurrence ->
            fieldValueSetRepository.findOccurrence(assignment.id, ref.occurrencePath)
    }

    private fun valueSetForWrite(assignment: SchemaAssignment, ref: FieldValueSetRef): FieldValueSet = when (ref)
    {
        FieldValueSetRef.Root ->
            fieldValueSetRepository.findRootForUpdate(assignment.id) ?: throw missingRootSet(assignment)

        is FieldValueSetRef.Occurrence ->
            fieldValueSetRepository.findOccurrenceForUpdate(assignment.id, ref.occurrencePath)
                ?: throw FieldValidationException("This resource has no repetition named ${ref.occurrencePath}")
    }

    private fun answersETagOf(assignment: SchemaAssignment): String =
        FieldValueSetETag.of(
            fieldValueSetRepository.findRootForUpdate(assignment.id) ?: throw missingRootSet(assignment),
        )

    private fun missingRootSet(assignment: SchemaAssignment) =
        IllegalStateException("Schema assignment ${assignment.id} has no root value set")

    private fun createRootValueSet(assignment: SchemaAssignment): FieldValueSet =
        fieldValueSetRepository.save(
            FieldValueSet().apply {
                this.schemaAssignmentId = assignment.id
                this.setKind = FieldValueSetKind.ROOT
            },
        )

    private fun assertSchemaVisibleToResource(
        adapter: FieldResourceAdapter,
        resourceId: UUID,
        definition: SchemaDefinition,
    )
    {
        if (definition.scopeKind == FieldScopeKind.PLATFORM) return

        val sameOwner = when (val resourceOwner = adapter.ownerScope(resourceId))
        {
            is ScopeReference.Organization ->
                definition.scopeKind == FieldScopeKind.ORGANIZATION &&
                        definition.scopeOrgId == resourceOwner.organizationId

            is ScopeReference.Personal ->
                definition.scopeKind == FieldScopeKind.PERSONAL &&
                        definition.scopeUserId == resourceOwner.userId
            ScopeReference.Platform, null -> false
        }
        if (!sameOwner)
            throw FieldValidationException("Schema belongs to a different owner and cannot govern this resource")
    }

    private fun project(
        assignment: SchemaAssignment,
        valueSet: FieldValueSet?,
        gate: FieldBindingGate,
    ): SchemaAssignmentDto
    {
        val version = schemaVersionRepository.findById(assignment.schemaVersionId)
            ?: throw IllegalStateException("Assigned schema version missing")
        val definition = schemaDefinitionRepository.findById(version.schemaDefinitionId)
            ?: throw IllegalStateException("Schema definition missing")
        val questions = projectionLoader
            .resolveValues(version.id, valueSet) { gate.permits(it, FieldValueOperation.READ) }
        return SchemaAssignmentDtoMapper.toDto(assignment, definition, version, questions, valueSet)
    }
}
