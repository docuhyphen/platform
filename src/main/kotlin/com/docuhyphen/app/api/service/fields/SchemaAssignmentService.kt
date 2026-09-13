package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.SchemaAssignmentDtoMapper
import com.docuhyphen.app.api.model.dto.SchemaAssignmentDto
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.util.*

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

    // ── Reads ─────────────────────────────────────────────────────────────────

    /** Current assignment and resolved values for a resource, or null if none is assigned. */
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

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Chooses the Schema that governs a resource, or removes the one that does, according to the
     * operation the command names. Answers with the assignment the resource now has, or null where
     * the command left it with none.
     */
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
    fun assignPublishedSchemaVersion(command: PublishedSchemaAssignmentCommand): SchemaAssignmentDto
    {
        val resourceType = command.resource.resourceType
        val resourceId = command.resource.resourceId
        val schemaVersionId = command.schemaVersionId
        val principal = command.access.principal
        val context = command.access.authorization
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        adapter.authorizeManageFields(resourceId, principal, context)
        adapter.authorizeManageSchema(resourceId, principal, context)
        if (!adapter.schemaAssignmentMutable(resourceId))
            throw IllegalStateException("This resource can no longer have its schema changed")

        val existing = assignmentRepository.findByResource(resourceType, resourceId)
        command.precondition.requireSatisfiedBy(existing?.let(SchemaAssignmentETag::of))
        if (existing != null)
            throw IllegalStateException("A schema is already assigned; remove it before assigning another")

        val version = schemaVersionRepository.findById(schemaVersionId)
            ?: throw FieldValidationException("Schema version not found: $schemaVersionId")
        if (version.status != FieldLifecycleStatus.PUBLISHED)
            throw FieldValidationException("Schema version $schemaVersionId is not published")

        val definition = schemaDefinitionRepository.findById(version.schemaDefinitionId)
            ?: throw FieldValidationException("Schema version $schemaVersionId belongs to a schema that no longer exists")
        if (definition.status == FieldLifecycleStatus.RETIRED)
            throw FieldValidationException("Schema is retired and cannot be assigned")
        if (definition.targetResourceType != resourceType)
            throw FieldValidationException("Schema targets ${definition.targetResourceType}, not $resourceType")
        assertSchemaVisibleToResource(adapter, resourceId, definition)
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
        val existing = assignmentRepository.findByResource(resourceType, resourceId)
        command.precondition.requireSatisfiedBy(existing?.let(SchemaAssignmentETag::of))
        if (existing != null)
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
        materializeConfiguredDefaults(assignment, rootValueSet, resourceType, resourceId, provenance)
        auditTrail.schemaAssigned(assignment, adapter.ownerScope(resourceId), provenance, definition.displayName)
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

        val assignment = assignmentRepository.findByResource(resourceType, resourceId)
            ?: throw IllegalArgumentException("No schema is assigned")
        command.precondition.requireSatisfiedBy(SchemaAssignmentETag.of(assignment))
        val schemaDisplayName = schemaDisplayNameOf(assignment)
        // Recorded revisions are deliberately left in place. What a resource once answered stays
        // part of the record even after its Schema is removed, which is what later records pointing
        // at an exact answer rely on.
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

    /**
     * Upserts typed values into the set the command addresses. The resource's own policy decides each
     * binding, so an entry naming a binding the caller may not change is refused for the reason that
     * policy gives, exactly like an entry naming a binding of another schema.
     *
     * The set is resolved under a write lock and its state is checked against the one the caller says
     * it read, before anything is stored. Two saves arriving together therefore run one after the
     * other, and the second is judged against what the first committed.
     */
    @Transactional
    fun clearValues(command: com.docuhyphen.app.api.model.fields.FieldValueClearCommand): SchemaAssignmentDto =
        writeValues(FieldValueWriteCommand(command.resource, command.access,
            command.fieldContractIds.map { FieldValueEntry(it, kotlinx.serialization.json.JsonNull) },
            command.valueSet, command.precondition), allowRequiredClear = true)

    @Transactional
    fun setValues(command: FieldValueWriteCommand): SchemaAssignmentDto = writeValues(command, allowRequiredClear = false)

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

        var changedCount = 0
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
                changedCount++
                storedAt = stored.updatedAt
                if (canonical.isEmpty) clearedCount++
            }
        }
        storedAt?.let { advanceValueSet(valueSet, it) }
        if (changedCount > 0)
            auditTrail.valuesUpdated(
                assignment, adapter.ownerScope(resourceId), provenance, schemaDisplayNameOf(assignment),
                changedCount, clearedCount,
            )
        return project(assignment, valueSet, gate)
    }

    @Transactional
    fun applyBlueprintFieldDefaults(command: BlueprintFieldDefaultsCommand): SchemaAssignmentDto?
    {
        if (command.defaults.isEmpty()) return null

        val resourceType = command.resource.resourceType
        val resourceId = command.resource.resourceId
        val principal = command.access.principal
        val context = command.access.authorization
        val adapter = adapterRegistry.adapterFor(resourceType)
        if (!adapter.exists(resourceId)) throw IllegalArgumentException("Resource not found: $resourceId")
        adapter.authorizeManageFields(resourceId, principal, context)
        if (!adapter.schemaAssignmentMutable(resourceId))
            throw IllegalStateException("This resource can no longer accept Blueprint field defaults")

        val assignment = assignmentRepository.findByResource(resourceType, resourceId) ?: return null
        val valueSet = valueSetForWrite(assignment, FieldValueSetRef.Root)
        val provenance = FieldPrincipalProvenance.of(principal, context)
        val bindingsByFieldDefinition = bindingRepository.findByVersion(assignment.schemaVersionId)
            .associateBy { it.fieldDefinitionId }

        var storedAt: Timestamp? = null
        command.defaults.forEach { default ->
            val binding = bindingsByFieldDefinition[default.fieldDefinitionId] ?: return@forEach
            val contract = fieldContractRepository.findById(binding.fieldContractId) ?: return@forEach
            val canonical = validator.canonicalize(contract, default.value)
            if (canonical.isEmpty) return@forEach
            val stored = upsertValue(
                valueSet, resourceType, resourceId, contract, binding, canonical, provenance,
                FieldValueProvenance.BLUEPRINT_DEFAULT,
            )
            if (stored != null) storedAt = stored.updatedAt
        }
        storedAt?.let { advanceValueSet(valueSet, it) }
        return project(
            assignment,
            valueSet,
            gateFor(adapter, command.resource, command.access, FieldValueSetRef.Root),
        )
    }

    /**
     * Creates the Field Value Set one runtime repetition of a repeatable group stores its answers in,
     * unless it already exists. Nothing creates a repetition set on demand from a value write --
     * see [valueSetForWrite] -- so whatever provisions the repetition itself must call this first.
     */
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

    // ── Internals ───────────────────────────────────────────────────────────────

    /**
     * Stores a value for every binding of the freshly assigned version that configures a default.
     * This is the only path that may write a read-only binding: the default is part of the schema
     * configuration rather than an answer supplied by the caller, so a read-only field ends up with
     * the value its author intended instead of staying permanently empty.
     *
     * A default that cannot be canonicalized against its own field contract is skipped and logged.
     * Published versions are immutable, so failing the assignment would leave the resource with no
     * way forward, while a skipped default only leaves that one field empty.
     */
    private fun materializeConfiguredDefaults(
        assignment: SchemaAssignment,
        valueSet: FieldValueSet,
        resourceType: String,
        resourceId: UUID,
        provenance: FieldPrincipalProvenance,
    )
    {
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
            val stored = upsertValue(
                valueSet, resourceType, resourceId, contract, binding, canonical, provenance,
                FieldValueProvenance.SCHEMA_DEFAULT,
            )
            if (stored != null) storedAt = stored.updatedAt
        }
        storedAt?.let { advanceValueSet(valueSet, it) }
    }

    /**
     * Stores one answer and records the change. Returns the stored answer, or null when nothing was
     * stored: a write that would leave the answer exactly as it already stands, by the same
     * principal in the same session and from the same origin, stores nothing and records nothing.
     * The client posts every editable field on each save, so treating those repeats as changes would
     * fill the history with entries that record nothing having happened.
     *
     * A stored change always leaves the live answer and its newest recorded revision saying the same
     * thing. Advancing the containing Value Set is left to the caller, because one save is one
     * change to the set however many of its questions that save answered.
     */
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

    /**
     * Moves a Value Set on to the state its newest answer left it in. The revision is what a client
     * names to say which state it edited, so it counts saves that stored something rather than
     * answers written, and the set's timestamp follows its youngest answer.
     */
    private fun advanceValueSet(valueSet: FieldValueSet, storedAt: Timestamp)
    {
        valueSet.updatedAt = storedAt
        valueSet.revision += 1
        fieldValueSetRepository.update(valueSet)
    }

    /**
     * Whether the stored answer already says exactly what this write would store, including who is
     * recorded as having left it and where it came from. Numbers are compared by value because the
     * column pads every answer to its own scale, so a stored answer read back is rarely equal to the
     * canonical form of the same number.
     */
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

        // Only a select-typed answer can hold option codes, so nothing else pays for the lookup.
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

    /** Display name of the Schema an assignment points at, for a record that names it in passing. */
    private fun schemaDisplayNameOf(assignment: SchemaAssignment): String?
    {
        val version = schemaVersionRepository.findById(assignment.schemaVersionId) ?: return null
        return schemaDefinitionRepository.findById(version.schemaDefinitionId)?.displayName
    }

    /** The decisions one command may ask of the policy that governs its resource. */
    private fun gateFor(
        adapter: FieldResourceAdapter,
        resource: FieldsResourceRef,
        access: FieldsAccessContext,
        valueSet: FieldValueSetRef,
    ) = FieldBindingGate(adapter.bindingPolicy, resource, access, valueSet)

    /** Refuses an entry the resource's policy will not accept, in the terms that policy gave. */
    private fun refuseUnlessWritable(gate: FieldBindingGate, binding: SchemaFieldBinding, fieldContractId: UUID)
    {
        val decision = gate.decide(binding, FieldValueOperation.WRITE)
        if (decision !is FieldBindingDecision.Deny) return
        throw when (decision.reason)
        {
            // A binding the caller may not even be shown is refused as if it belonged to another
            // schema, so a refusal never tells a caller that a hidden question exists.
            FieldBindingDenial.OUT_OF_AUDIENCE ->
                FieldValidationException("Field $fieldContractId is not part of the assigned schema")

            FieldBindingDenial.READ_ONLY ->
                FieldValidationException("Field $fieldContractId is read-only")
        }
    }

    /** The set a read projects, or null where the resource has stored no answers in it yet. */
    private fun valueSetForRead(assignment: SchemaAssignment, ref: FieldValueSetRef): FieldValueSet? = when (ref)
    {
        FieldValueSetRef.Root -> fieldValueSetRepository.findRoot(assignment.id)
        is FieldValueSetRef.Occurrence ->
            fieldValueSetRepository.findOccurrence(assignment.id, ref.occurrencePath)
    }

    /**
     * The set a save stores into, locked for the rest of the transaction so competing saves serialize.
     *
     * The root set is created together with the Schema Assignment, so a save that finds none is
     * completing an assignment that predates the set: restoring it is what keeps the answer storable,
     * and the one-root-per-assignment rule keeps the result single. A repetition is not created on
     * demand, because a repetition exists only where something added it.
     */
    private fun valueSetForWrite(assignment: SchemaAssignment, ref: FieldValueSetRef): FieldValueSet = when (ref)
    {
        FieldValueSetRef.Root ->
            fieldValueSetRepository.findRootForUpdate(assignment.id) ?: createRootValueSet(assignment)

        is FieldValueSetRef.Occurrence ->
            fieldValueSetRepository.findOccurrenceForUpdate(assignment.id, ref.occurrencePath)
                ?: throw FieldValidationException("This resource has no repetition named ${ref.occurrencePath}")
    }

    private fun createRootValueSet(assignment: SchemaAssignment): FieldValueSet =
        fieldValueSetRepository.save(
            FieldValueSet().apply {
                this.schemaAssignmentId = assignment.id
                this.setKind = FieldValueSetKind.ROOT
            },
        )

    /**
     * Whether the chosen Schema is one this resource's owner may govern it with. What the platform
     * publishes governs every resource; anything else governs only what its own owner holds.
     *
     * Ownership is not a hierarchy, so this is one question asked of whichever owner the resource
     * has rather than an organization question with every other owner treated as an absence. A
     * person's Schema is as far out of an organization's reach as an organization's is out of a
     * person's, and a resource whose owner cannot be resolved is governed only by the platform.
     */
    private fun assertSchemaVisibleToResource(
        adapter: FieldResourceAdapter,
        resourceId: UUID,
        definition: SchemaDefinition,
    )
    {
        if (definition.scopeKind == FieldScopeKind.PLATFORM) return

        val resourceOwner = adapter.ownerScope(resourceId)
        val sameOwner = when (resourceOwner)
        {
            is ScopeReference.Organization ->
                definition.scopeKind == FieldScopeKind.ORGANIZATION &&
                        definition.scopeOrgId == resourceOwner.organizationId

            is ScopeReference.Personal ->
                definition.scopeKind == FieldScopeKind.PERSONAL &&
                        definition.scopeUserId == resourceOwner.userId
            // The platform governs a resource only through what it publishes, which returned above.
            ScopeReference.Platform, null -> false
        }
        if (!sameOwner)
            throw FieldValidationException("Schema belongs to a different owner and cannot govern this resource")
    }

    /**
     * The projection of an assignment and the answers one set holds, filtered to the questions this
     * caller may be shown. The decision comes from the same policy the write path consults, so a
     * response never discloses a binding the same caller would be refused permission to address.
     *
     * One narrowing produces both what the projection describes and what it answers, so a consumer
     * builds its editors from this projection alone rather than pairing it with a separately
     * authorized view of the schema configuration, which could disclose a question whose answer is
     * withheld here.
     *
     * Only the addressed set is projected. A resource answers as itself in its root set, so an answer
     * belonging to a repetition of a group is that repetition's, not the resource's.
     */
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
