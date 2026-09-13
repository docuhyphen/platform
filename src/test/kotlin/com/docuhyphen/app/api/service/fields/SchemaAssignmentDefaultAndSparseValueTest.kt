package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueProvenance
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Value semantics for a resource's assigned Schema: configured binding defaults become stored
 * values when the Schema is assigned, a client may not write a read-only binding, and an update
 * remains sparse so an omitted binding keeps whatever it already held.
 *
 * The resource adapter is stubbed here because these rules belong to the Fields engine rather than
 * to any one resource domain; audience filtering and capability rules are covered separately.
 */
class SchemaAssignmentDefaultAndSparseValueTest
{
    private val resourceType = "EXCHANGE"
    private val resourceId = UUID.randomUUID()

    /** The caller every command in this test acts for. */
    private val access = FieldsAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())
    private val resource by lazy { FieldsResourceRef(resourceType, resourceId) }
    private val organizationId = UUID.randomUUID()
    private val schemaDefinitionId = UUID.randomUUID()
    private val schemaVersionId = UUID.randomUUID()
    private val assignmentId = UUID.randomUUID()
    private val rootValueSetId = UUID.randomUUID()

    private val optionalContractId = UUID.randomUUID()
    private val requiredContractId = UUID.randomUUID()
    private val readOnlyDefaultContractId = UUID.randomUUID()
    private val editableDefaultContractId = UUID.randomUUID()

    private val readOnlyDefaultText = "Reference copy"
    private val editableDefaultText = "Starting note"

    private data class Fixture(
        val service: SchemaAssignmentService,
        val stored: MutableMap<UUID, FieldValue>,
        val saved: MutableList<FieldValue>,
        val updated: MutableList<FieldValue>,
        val valueRepository: FieldValueRepository,
    )

    /** Reads the answers the resource gives as itself. */
    private fun Fixture.read() = service.getAssignment(FieldValueReadCommand(resource, access))

    /** Saves answers into the set the resource answers with as itself. */
    private fun Fixture.save(entries: List<FieldValueEntry>) =
        service.setValues(FieldValueWriteCommand(resource, access, entries))

    /** Chooses the published Schema for the resource. */
    private fun Fixture.assign() = requireNotNull(
        service.applySchemaAssignment(
            SchemaAssignmentCommand(
                resource, access, SchemaAssignmentOperation.ASSIGN, schemaDefinitionId,
            ),
        ),
    )

    /** Removes the Schema governing the resource. */
    private fun Fixture.unassign()
    {
        service.applySchemaAssignment(
            SchemaAssignmentCommand(resource, access, SchemaAssignmentOperation.UNASSIGN),
        )
    }

    /**
     * @param assigned whether a Schema Assignment already exists for the resource.
     * @param existingValues values already stored against the assignment, keyed by field contract.
     * @param malformedDefault when set, replaces the read-only binding's configured default with
     *   text that cannot be canonicalized against the field's contract.
     */
    private fun fixture(
        assigned: Boolean = true,
        existingValues: Map<UUID, String> = emptyMap(),
        malformedDefault: String? = null,
    ): Fixture
    {
        val adapter = mock<FieldResourceAdapter>()
        whenever(adapter.resourceType).thenReturn(resourceType)
        whenever(adapter.exists(resourceId)).thenReturn(true)
        whenever(adapter.schemaAssignmentMutable(resourceId)).thenReturn(true)
        whenever(adapter.valuesEditable(resourceId)).thenReturn(true)
        whenever(adapter.bindingPolicy).thenReturn(InternalCallerBindingPolicy)
        whenever(adapter.ownerScope(resourceId)).thenReturn(ScopeReference.Organization(organizationId))
        whenever(adapter.subscriptionContext(resourceId)).thenReturn(null)

        val registry = mock<FieldResourceAdapterRegistry>()
        whenever(registry.adapterFor(resourceType)).thenReturn(adapter)

        val principal = access.principal
        val assignment = SchemaAssignment().apply {
            id = assignmentId
            resourceType = this@SchemaAssignmentDefaultAndSparseValueTest.resourceType
            resourceId = this@SchemaAssignmentDefaultAndSparseValueTest.resourceId
            schemaVersionId = this@SchemaAssignmentDefaultAndSparseValueTest.schemaVersionId
            scopeKind = FieldScopeKind.ORGANIZATION
            scopeOrgId = organizationId
        }
        val assignmentRepository = mock<SchemaAssignmentRepository>()
        whenever(assignmentRepository.findByResource(resourceType, resourceId))
            .thenReturn(if (assigned) assignment else null)
        whenever(assignmentRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<SchemaAssignment>(0).also { it.id = assignmentId }
        }

        val definition = SchemaDefinition().apply {
            id = schemaDefinitionId
            scopeKind = FieldScopeKind.ORGANIZATION
            scopeOrgId = organizationId
            namespace = "process"
            schemaKey = "process-data"
            displayName = "Process data"
            targetResourceType = resourceType
            status = FieldLifecycleStatus.PUBLISHED
        }
        val definitionRepository = mock<SchemaDefinitionRepository>()
        whenever(definitionRepository.findById(schemaDefinitionId)).thenReturn(definition)

        val version = SchemaVersion().apply {
            id = schemaVersionId
            schemaDefinitionId = this@SchemaAssignmentDefaultAndSparseValueTest.schemaDefinitionId
            versionNumber = 1
            status = FieldLifecycleStatus.PUBLISHED
        }
        val versionRepository = mock<SchemaVersionRepository>()
        whenever(versionRepository.findById(schemaVersionId)).thenReturn(version)
        whenever(versionRepository.findLatestPublished(schemaDefinitionId)).thenReturn(version)

        val bindingRepository = mock<SchemaFieldBindingRepository>()
        whenever(bindingRepository.findByVersion(schemaVersionId)).thenReturn(
            listOf(
                binding(optionalContractId, 0),
                binding(requiredContractId, 1, required = true),
                binding(
                    readOnlyDefaultContractId,
                    2,
                    readOnly = true,
                    default = malformedDefault ?: quoted(readOnlyDefaultText),
                ),
                binding(editableDefaultContractId, 3, default = quoted(editableDefaultText)),
            ),
        )

        val contracts = listOf(
            contract(optionalContractId, "Optional note"),
            contract(requiredContractId, "Required note"),
            contract(readOnlyDefaultContractId, "Reference note"),
            contract(editableDefaultContractId, "Starting note"),
        )
        val contractRepository = mock<FieldContractRepository>()
        whenever(contractRepository.findByIds(any())).thenAnswer { invocation ->
            val ids = invocation.getArgument<Collection<UUID>>(0).toSet()
            contracts.filter { it.id in ids }
        }
        contracts.forEach { whenever(contractRepository.findById(it.id)).thenReturn(it) }

        val fieldDefinitionRepository = mock<FieldDefinitionRepository>()
        contracts.forEach { c ->
            whenever(fieldDefinitionRepository.findById(c.fieldDefinitionId)).thenReturn(
                FieldDefinition().apply {
                    id = c.fieldDefinitionId
                    namespace = "process"
                    fieldKey = "note-${c.id}"
                    status = FieldLifecycleStatus.PUBLISHED
                },
            )
        }

        val stored = existingValues
            .mapValues { (contractId, text) -> storedValue(contractId, text) }
            .toMutableMap()
        val saved = mutableListOf<FieldValue>()
        val updated = mutableListOf<FieldValue>()

        val valueRepository = mock<FieldValueRepository>()
        whenever(valueRepository.findByAssignment(assignmentId)).thenAnswer { stored.values.toList() }
        whenever(valueRepository.findByValueSet(any())).thenAnswer { stored.values.toList() }
        whenever(valueRepository.findByValueSetAndContract(any(), any())).thenAnswer { invocation ->
            stored[invocation.getArgument<UUID>(1)]
        }
        whenever(valueRepository.save(any())).thenAnswer { invocation ->
            val value = invocation.getArgument<FieldValue>(0)
            stored[value.fieldContractId] = value
            saved += value
            value
        }
        whenever(valueRepository.update(any())).thenAnswer { invocation ->
            val value = invocation.getArgument<FieldValue>(0)
            stored[value.fieldContractId] = value
            updated += value
            value
        }

        val rootValueSet = FieldValueSet().apply {
            id = rootValueSetId
            schemaAssignmentId = assignmentId
            setKind = FieldValueSetKind.ROOT
        }
        val valueSetRepository = mock<FieldValueSetRepository>()
        whenever(valueSetRepository.findRoot(assignmentId)).thenReturn(rootValueSet)
        whenever(valueSetRepository.findByAssignment(assignmentId)).thenReturn(listOf(rootValueSet))
        whenever(valueSetRepository.save(any())).thenAnswer { it.getArgument<FieldValueSet>(0) }

        val selectionRepository = mock<FieldValueSelectionRepository>()
        whenever(selectionRepository.findByValue(any())).thenReturn(emptyList())

        return Fixture(
            SchemaAssignmentService(
                adapterRegistry = registry,
                assignmentRepository = assignmentRepository,
                schemaDefinitionRepository = definitionRepository,
                schemaVersionRepository = versionRepository,
                bindingRepository = bindingRepository,
                fieldContractRepository = contractRepository,
                fieldValueRepository = valueRepository,
                fieldValueSetRepository = valueSetRepository,
                selectionRepository = selectionRepository,
                projectionLoader = FieldsProjectionLoader(
                    bindingRepository, contractRepository, fieldDefinitionRepository,
                    valueRepository, selectionRepository,
                ),
                validator = FieldValueValidator(FieldTypeRegistry()),
                subscriptionGuard = mock<BusinessFieldsSubscriptionGuard>(),
                revisionRecorder = mock<FieldValueRevisionRecorder>(),
                auditTrail = mock<SchemaAssignmentAuditTrail>(),
            ),
            stored,
            saved,
            updated,
            valueRepository,
        )
    }

    private fun quoted(text: String) = "\"$text\""

    private fun binding(
        contractId: UUID,
        order: Int,
        required: Boolean = false,
        readOnly: Boolean = false,
        default: String? = null,
    ) = SchemaFieldBinding().apply {
        id = UUID.randomUUID()
        schemaVersionId = this@SchemaAssignmentDefaultAndSparseValueTest.schemaVersionId
        fieldContractId = contractId
        displayOrder = order
        isRequired = required
        isReadOnly = readOnly
        defaultValueJson = default
        visibility = FieldDataClassification.INTERNAL
    }

    private fun contract(contractId: UUID, contractLabel: String) = FieldContract().apply {
        id = contractId
        fieldDefinitionId = UUID.randomUUID()
        valueType = FieldValueType.SHORT_TEXT
        label = contractLabel
    }

    private fun storedValue(contractId: UUID, text: String) = FieldValue().apply {
        id = UUID.randomUUID()
        schemaAssignmentId = assignmentId
        fieldContractId = contractId
        resourceType = this@SchemaAssignmentDefaultAndSparseValueTest.resourceType
        resourceId = this@SchemaAssignmentDefaultAndSparseValueTest.resourceId
        valueType = FieldValueType.SHORT_TEXT
        textValue = text
        provenance = FieldValueProvenance.USER
    }

    private fun entry(contractId: UUID, text: String?) =
        FieldValueEntry(contractId, if (text == null) JsonNull else JsonPrimitive(text))

    // ── Configured defaults ───────────────────────────────────────────────────

    @Test
    fun `assigning a schema stores the configured defaults including read-only bindings`()
    {
        val fixture = fixture(assigned = false)

        fixture.assign()

        assertEquals(readOnlyDefaultText, fixture.stored[readOnlyDefaultContractId]?.textValue)
        assertEquals(editableDefaultText, fixture.stored[editableDefaultContractId]?.textValue)
    }

    @Test
    fun `a stored default is not attributed to the caller as their own answer`()
    {
        val fixture = fixture(assigned = false)

        fixture.assign()

        val default = requireNotNull(fixture.stored[readOnlyDefaultContractId]) {
            "The configured default must be stored before its provenance can be judged"
        }
        assertNotEquals(FieldValueProvenance.USER, default.provenance)
    }

    @Test
    fun `assigning a schema stores nothing for a binding without a configured default`()
    {
        val fixture = fixture(assigned = false)

        fixture.assign()

        assertNull(fixture.stored[optionalContractId])
        assertNull(fixture.stored[requiredContractId])
    }

    @Test
    fun `a configured default that cannot be canonicalized does not block assignment`()
    {
        val fixture = fixture(assigned = false, malformedDefault = "{not valid json")

        val result = fixture.assign()

        assertEquals(schemaVersionId, result.schemaVersionId)
        assertNull(fixture.stored[readOnlyDefaultContractId])
        assertEquals(editableDefaultText, fixture.stored[editableDefaultContractId]?.textValue)
    }

    @Test
    fun `a stored default is reported as an answered value`()
    {
        val fixture = fixture(assigned = false)

        val result = fixture.assign()

        val projected = result.fields.single { it.fieldContractId == readOnlyDefaultContractId }
        assertEquals(false, projected.isEmpty)
    }

    @Test
    fun `blueprint defaults are stored as defaults for compatible stable fields only`()
    {
        val fixture = SchemaAssignmentFieldsFixture()
        val unrelatedFieldDefinitionId = UUID.randomUUID()

        fixture.service.applyBlueprintFieldDefaults(
            BlueprintFieldDefaultsCommand(
                resource = fixture.resource,
                access = fixture.access,
                defaults = listOf(
                    BlueprintFieldDefaultEntry(
                        fieldDefinitionId = fixture.noteDefinitionId,
                        value = JsonPrimitive("Blueprint note"),
                    ),
                    BlueprintFieldDefaultEntry(
                        fieldDefinitionId = unrelatedFieldDefinitionId,
                        value = JsonPrimitive("Unrelated note"),
                    ),
                ),
            ),
        )

        val default = requireNotNull(fixture.rootAnswer(fixture.noteContractId))
        assertEquals("Blueprint note", default.textValue)
        assertEquals(FieldValueProvenance.BLUEPRINT_DEFAULT, default.provenance)
        assertEquals(fixture.access.principal.kind, default.updatedByPrincipalKind)
        assertEquals(fixture.access.principal.id, default.updatedByPrincipalId)
        assertNull(fixture.rootAnswer(fixture.secondNoteContractId))
    }

    // ── Read-only bindings ────────────────────────────────────────────────────

    @Test
    fun `a client value for a read-only binding is refused`()
    {
        val fixture = fixture()

        val error = assertThrows<FieldValidationException> {
            fixture.save(listOf(entry(readOnlyDefaultContractId, "Client supplied")),
            )
        }

        assertTrue(
            error.message?.contains("read-only") == true,
            "A read-only binding must be refused by name: ${error.message}",
        )
        verify(fixture.valueRepository, never()).save(any())
        verify(fixture.valueRepository, never()).update(any())
    }

    // ── Sparse updates ────────────────────────────────────────────────────────

    @Test
    fun `an omitted binding keeps the value it already held`()
    {
        val fixture = fixture(
            existingValues = mapOf(
                optionalContractId to "Kept",
                editableDefaultContractId to "Also kept",
            ),
        )

        fixture.save(listOf(entry(optionalContractId, "Replaced")))

        assertEquals("Replaced", fixture.stored[optionalContractId]?.textValue)
        assertEquals("Also kept", fixture.stored[editableDefaultContractId]?.textValue)
        assertEquals(
            listOf(optionalContractId),
            fixture.updated.map { it.fieldContractId },
        )
    }

    @Test
    fun `an omitted required binding does not block an update to another binding`()
    {
        val fixture = fixture(existingValues = mapOf(optionalContractId to "Kept"))

        val result = fixture.save(listOf(entry(optionalContractId, "Replaced")),
        )

        assertEquals("Replaced", fixture.stored[optionalContractId]?.textValue)
        assertTrue(result.fields.any { it.fieldContractId == requiredContractId })
    }

    @Test
    fun `an explicitly emptied required binding is refused`()
    {
        val fixture = fixture(existingValues = mapOf(requiredContractId to "Answered"))

        val error = assertThrows<FieldValidationException> {
            fixture.save(listOf(entry(requiredContractId, null)))
        }

        assertTrue(
            error.message?.contains("is required") == true,
            "Clearing a required binding must say so: ${error.message}",
        )
        assertEquals("Answered", fixture.stored[requiredContractId]?.textValue)
    }
}
