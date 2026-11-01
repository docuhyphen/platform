package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.model.DefaultAndSparseValueFixture
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class SchemaAssignmentDefaultAndSparseValueTest
{
    private val resourceType = "EXCHANGE"
    private val resourceId = UUID.randomUUID()

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

    private fun DefaultAndSparseValueFixture.read() = service.getAssignment(FieldValueReadCommand(resource, access))

    private fun DefaultAndSparseValueFixture.save(entries: List<FieldValueEntry>) =
        service.setValues(FieldValueWriteCommand(resource, access, entries))

    private fun DefaultAndSparseValueFixture.assign() = requireNotNull(
        service.applySchemaAssignment(
            SchemaAssignmentCommand(
                resource, access, SchemaAssignmentOperation.ASSIGN, schemaDefinitionId,
            ),
        ),
    )

    private fun DefaultAndSparseValueFixture.unassign()
    {
        service.applySchemaAssignment(
            SchemaAssignmentCommand(resource, access, SchemaAssignmentOperation.UNASSIGN),
        )
    }

    private fun fixture(
        assigned: Boolean = true,
        existingValues: Map<UUID, String> = emptyMap(),
        malformedDefault: String? = null,
    ): DefaultAndSparseValueFixture
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
        whenever(valueSetRepository.findRootForUpdate(assignmentId)).thenReturn(rootValueSet)
        whenever(valueSetRepository.findByAssignment(assignmentId)).thenReturn(listOf(rootValueSet))
        whenever(valueSetRepository.save(any())).thenAnswer { it.getArgument<FieldValueSet>(0) }

        val selectionRepository = mock<FieldValueSelectionRepository>()
        whenever(selectionRepository.findByValue(any())).thenReturn(emptyList())

        return DefaultAndSparseValueFixture(
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

        fixture.service.applyBlueprintFieldDefaultsForCreation(
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

    @Test
    fun `a blueprint default its Field cannot hold, or one naming a read-only Field, is skipped and the others are stored`() {
        val fixture =
            SchemaAssignmentFieldsFixture(readOnlyQuestions = setOf(SchemaAssignmentFieldsFixture.Question.SECOND_NOTE))

        fixture.service.applyBlueprintFieldDefaultsForCreation(
            BlueprintFieldDefaultsCommand(
                resource = fixture.resource,
                access = fixture.access,
                defaults = listOf(
                    BlueprintFieldDefaultEntry(
                        fieldDefinitionId = fixture.optionsDefinitionId,
                        value = JsonArray(listOf(JsonPrimitive("withdrawn-option"))),
                    ),
                    BlueprintFieldDefaultEntry(
                        fieldDefinitionId = fixture.secondNoteDefinitionId,
                        value = JsonPrimitive("Blueprint second note"),
                    ),
                    BlueprintFieldDefaultEntry(
                        fieldDefinitionId = fixture.noteDefinitionId,
                        value = JsonPrimitive("Blueprint note"),
                    ),
                ),
            ),
        )

        assertEquals("Blueprint note", requireNotNull(fixture.rootAnswer(fixture.noteContractId)).textValue)
        assertNull(fixture.rootAnswer(fixture.secondNoteContractId))
        assertNull(fixture.rootAnswer(fixture.optionsContractId))
    }

    @Test
    fun `blueprint defaults copied while a resource is created need no permission to edit its fields`() {
        val fixture = SchemaAssignmentFieldsFixture(manageFieldsAllowed = false)

        fixture.service.applyBlueprintFieldDefaultsForCreation(
            BlueprintFieldDefaultsCommand(
                resource = fixture.resource,
                access = fixture.access,
                defaults = listOf(
                    BlueprintFieldDefaultEntry(
                        fieldDefinitionId = fixture.noteDefinitionId,
                        value = JsonPrimitive("Blueprint note"),
                    ),
                ),
            ),
        )

        assertEquals("Blueprint note", requireNotNull(fixture.rootAnswer(fixture.noteContractId)).textValue)
    }

    @Test
    fun `a client value for a read-only binding is refused`()
    {
        val fixture = fixture()

        val error = assertThrows<FieldValidationException> {
            fixture.save(
                listOf(entry(readOnlyDefaultContractId, "Client supplied")),
            )
        }

        assertTrue(
            error.message.contains("read-only") == true,
            "A read-only binding must be refused by name: ${error.message}",
        )
        verify(fixture.valueRepository, never()).save(any())
        verify(fixture.valueRepository, never()).update(any())
    }

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

        val result = fixture.save(
            listOf(entry(optionalContractId, "Replaced")),
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
            error.message.contains("is required") == true,
            "Clearing a required binding must say so: ${error.message}",
        )
        assertEquals("Answered", fixture.stored[requiredContractId]?.textValue)
    }
}
