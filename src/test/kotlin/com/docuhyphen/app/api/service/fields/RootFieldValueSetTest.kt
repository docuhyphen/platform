package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.model.RootValueSetFixture
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

class RootFieldValueSetTest
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
    private val occurrenceValueSetId = UUID.randomUUID()
    private val occurrencePath = "items[0]"

    private val noteContractId = UUID.randomUUID()
    private val noteDefinitionId = UUID.randomUUID()
    private val countContractId = UUID.randomUUID()
    private val countDefinitionId = UUID.randomUUID()

    private val rootAnswer = "Answer given as the resource"
    private val occurrenceAnswer = "Answer given for one repetition"

    private fun RootValueSetFixture.read() = service.getAssignment(FieldValueReadCommand(resource, access))

    private fun RootValueSetFixture.save(entries: List<FieldValueEntry>) =
        service.setValues(FieldValueWriteCommand(resource, access, entries))

    private fun RootValueSetFixture.assign() = requireNotNull(
        service.applySchemaAssignment(
            SchemaAssignmentCommand(
                resource, access, SchemaAssignmentOperation.ASSIGN, schemaDefinitionId,
            ),
        ),
    )

    private fun RootValueSetFixture.unassign()
    {
        service.applySchemaAssignment(
            SchemaAssignmentCommand(resource, access, SchemaAssignmentOperation.UNASSIGN),
        )
    }

    private fun fixture(
        assigned: Boolean = true,
        rootSetExists: Boolean = true,
        rootAnswers: Map<UUID, String> = emptyMap(),
        occurrenceAnswers: Map<UUID, String> = emptyMap(),
        configuredDefault: String? = null,
    ): RootValueSetFixture
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

        val assignment = SchemaAssignment().apply {
            id = assignmentId
            resourceType = this@RootFieldValueSetTest.resourceType
            resourceId = this@RootFieldValueSetTest.resourceId
            schemaVersionId = this@RootFieldValueSetTest.schemaVersionId
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
            schemaDefinitionId = this@RootFieldValueSetTest.schemaDefinitionId
            versionNumber = 1
            status = FieldLifecycleStatus.PUBLISHED
        }
        val versionRepository = mock<SchemaVersionRepository>()
        whenever(versionRepository.findById(schemaVersionId)).thenReturn(version)
        whenever(versionRepository.findLatestPublished(schemaDefinitionId)).thenReturn(version)

        val bindingRepository = mock<SchemaFieldBindingRepository>()
        whenever(bindingRepository.findByVersion(schemaVersionId)).thenReturn(
            listOf(
                binding(noteContractId, 0),
                binding(countContractId, 1, default = configuredDefault),
            ),
        )

        val contracts = listOf(
            contract(noteContractId, noteDefinitionId, "Recorded note"),
            contract(countContractId, countDefinitionId, "Recorded count"),
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
                    fieldKey = if (c.id == noteContractId) "recorded-note" else "recorded-count"
                    status = FieldLifecycleStatus.PUBLISHED
                },
            )
        }

        val storedByValueSet = mutableMapOf(
            rootValueSetId to rootAnswers
                .mapValues { (contractId, text) -> storedValue(contractId, rootValueSetId, text) }
                .toMutableMap(),
            occurrenceValueSetId to occurrenceAnswers
                .mapValues { (contractId, text) -> storedValue(contractId, occurrenceValueSetId, text) }
                .toMutableMap(),
        )
        val deletedValues = mutableListOf<FieldValue>()

        val valueRepository = mock<FieldValueRepository>()
        whenever(valueRepository.findByAssignment(assignmentId))
            .thenAnswer { storedByValueSet.values.flatMap { it.values } }
        whenever(valueRepository.findByValueSet(any())).thenAnswer { invocation ->
            storedByValueSet[invocation.getArgument<UUID>(0)]?.values?.toList() ?: emptyList<FieldValue>()
        }
        whenever(valueRepository.findByValueSetAndContract(any(), any())).thenAnswer { invocation ->
            storedByValueSet[invocation.getArgument<UUID>(0)]?.get(invocation.getArgument<UUID>(1))
        }
        whenever(valueRepository.save(any())).thenAnswer { invocation ->
            val value = invocation.getArgument<FieldValue>(0)
            storedByValueSet.getOrPut(value.fieldValueSetId) { mutableMapOf() }[value.fieldContractId] = value
            value
        }
        whenever(valueRepository.update(any())).thenAnswer { invocation ->
            val value = invocation.getArgument<FieldValue>(0)
            storedByValueSet.getOrPut(value.fieldValueSetId) { mutableMapOf() }[value.fieldContractId] = value
            value
        }
        whenever(valueRepository.delete(any())).thenAnswer { invocation ->
            deletedValues += invocation.getArgument<FieldValue>(0)
        }

        val rootSet = FieldValueSet().apply {
            id = rootValueSetId
            schemaAssignmentId = assignmentId
            setKind = FieldValueSetKind.ROOT
        }
        val occurrenceSet = FieldValueSet().apply {
            id = occurrenceValueSetId
            schemaAssignmentId = assignmentId
            setKind = FieldValueSetKind.OCCURRENCE
            occurrencePath = this@RootFieldValueSetTest.occurrencePath
        }
        val savedSets = mutableListOf<FieldValueSet>()
        val deletedSets = mutableListOf<FieldValueSet>()

        val valueSetRepository = mock<FieldValueSetRepository>()
        whenever(valueSetRepository.findRoot(assignmentId))
            .thenReturn(if (rootSetExists) rootSet else null)
        whenever(valueSetRepository.findRootForUpdate(assignmentId))
            .thenReturn(if (rootSetExists) rootSet else null)
        whenever(valueSetRepository.findOccurrence(assignmentId, occurrencePath)).thenReturn(occurrenceSet)
        whenever(valueSetRepository.findOccurrenceForUpdate(assignmentId, occurrencePath))
            .thenReturn(occurrenceSet)
        whenever(valueSetRepository.findByAssignment(assignmentId))
            .thenReturn(if (rootSetExists) listOf(rootSet, occurrenceSet) else emptyList())
        whenever(valueSetRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<FieldValueSet>(0).also { savedSets += it }
        }
        whenever(valueSetRepository.delete(any())).thenAnswer { invocation ->
            deletedSets += invocation.getArgument<FieldValueSet>(0)
        }

        val selectionRepository = mock<FieldValueSelectionRepository>()
        whenever(selectionRepository.findByValue(any())).thenReturn(emptyList())

        return RootValueSetFixture(
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
            ExchangeFieldQueryService(
                assignmentRepository = assignmentRepository,
                fieldValueSetRepository = valueSetRepository,
                projectionLoader = FieldsProjectionLoader(
                    bindingRepository, contractRepository, fieldDefinitionRepository,
                    valueRepository, selectionRepository,
                ),
            ),
            storedByValueSet,
            savedSets,
            deletedSets,
            deletedValues,
            valueSetRepository,
        )
    }

    private fun binding(contractId: UUID, order: Int, default: String? = null) = SchemaFieldBinding().apply {
        id = UUID.randomUUID()
        schemaVersionId = this@RootFieldValueSetTest.schemaVersionId
        fieldContractId = contractId
        displayOrder = order
        isRequired = false
        isReadOnly = false
        defaultValueJson = default
        visibility = FieldDataClassification.INTERNAL
    }

    private fun contract(contractId: UUID, definitionId: UUID, contractLabel: String) = FieldContract().apply {
        id = contractId
        fieldDefinitionId = definitionId
        valueType = FieldValueType.SHORT_TEXT
        label = contractLabel
    }

    private fun storedValue(contractId: UUID, valueSetId: UUID, text: String) = FieldValue().apply {
        id = UUID.randomUUID()
        fieldValueSetId = valueSetId
        schemaAssignmentId = assignmentId
        fieldContractId = contractId
        resourceType = this@RootFieldValueSetTest.resourceType
        resourceId = this@RootFieldValueSetTest.resourceId
        valueType = FieldValueType.SHORT_TEXT
        textValue = text
        provenance = FieldValueProvenance.USER
    }

    private fun entry(contractId: UUID, text: String) = FieldValueEntry(contractId, JsonPrimitive(text))

    @Test
    fun `assigning a schema gives the assignment its root value set`()
    {
        val fixture = fixture(assigned = false, rootSetExists = false)

        fixture.assign()

        val created = fixture.savedSets.single()
        assertEquals(assignmentId, created.schemaAssignmentId)
        assertEquals(FieldValueSetKind.ROOT, created.setKind)
        assertNull(created.occurrencePath, "The answers an assignment gives as itself have no path")
    }

    @Test
    fun `unassigning a schema removes the value sets along with the answers`()
    {
        val fixture = fixture(rootAnswers = mapOf(noteContractId to rootAnswer))

        fixture.unassign()

        assertEquals(
            setOf(rootValueSetId, occurrenceValueSetId),
            fixture.deletedSets.map { it.id }.toSet(),
        )
        assertTrue(
            fixture.deletedValues.isNotEmpty(),
            "The answers must be removed before the sets that hold them",
        )
    }

    @Test
    fun `an answer is stored in the root value set of its assignment`()
    {
        val fixture = fixture()

        fixture.save(listOf(entry(noteContractId, rootAnswer)))

        val stored = requireNotNull(fixture.storedByValueSet[rootValueSetId]?.get(noteContractId))
        assertEquals(rootValueSetId, stored.fieldValueSetId)
        assertEquals(rootAnswer, stored.textValue)
    }

    @Test
    fun `a configured default is stored in the root value set`()
    {
        val fixture = fixture(assigned = false, rootSetExists = false, configuredDefault = "\"Starting count\"")

        fixture.assign()

        val rootSet = fixture.savedSets.single()
        val stored = requireNotNull(fixture.storedByValueSet[rootSet.id]?.get(countContractId)) {
            "The configured default must be stored in the set the assignment answers with"
        }
        assertEquals(rootSet.id, stored.fieldValueSetId)
        assertEquals("Starting count", stored.textValue)
    }

    @Test
    fun `writing an answer that the root set already holds replaces it rather than adding another`()
    {
        val fixture = fixture(
            rootAnswers = mapOf(noteContractId to "Earlier answer"),
            occurrenceAnswers = mapOf(noteContractId to occurrenceAnswer),
        )

        fixture.save(listOf(entry(noteContractId, rootAnswer)))

        assertEquals(rootAnswer, fixture.storedByValueSet[rootValueSetId]?.get(noteContractId)?.textValue)
        assertEquals(1, fixture.storedByValueSet[rootValueSetId]?.size)
        assertEquals(
            occurrenceAnswer,
            fixture.storedByValueSet[occurrenceValueSetId]?.get(noteContractId)?.textValue,
        )
    }

    @Test
    fun `a save for an assignment that has lost its root value set is refused rather than rebuilding it`() {
        val fixture = fixture(rootSetExists = false)

        assertThrows<IllegalStateException> { fixture.save(listOf(entry(noteContractId, rootAnswer))) }

        assertTrue(fixture.savedSets.isEmpty(), "No set is created on the way to a save")
        assertTrue(fixture.storedByValueSet.values.all { it.isEmpty() }, "Nothing is stored")
    }

    @Test
    fun `the resource projection reads the answers of the root value set only`()
    {
        val fixture = fixture(
            rootAnswers = mapOf(noteContractId to rootAnswer),
            occurrenceAnswers = mapOf(countContractId to occurrenceAnswer),
        )

        val assignment = requireNotNull(fixture.read())

        assertEquals(
            rootAnswer,
            assignment.fields.single { it.fieldContractId == noteContractId }.value.toString().trim('"'),
        )
        assertTrue(
            assignment.fields.single { it.fieldContractId == countContractId }.isEmpty,
            "A repetition's answer must not be projected as the resource's own answer",
        )
    }

    @Test
    fun `the workflow snapshot reads the answers of the root value set only`()
    {
        val fixture = fixture(
            rootAnswers = mapOf(noteContractId to rootAnswer),
            occurrenceAnswers = mapOf(countContractId to occurrenceAnswer),
        )

        val snapshot = fixture.queryService.getCanonicalValues(resourceId)

        assertTrue(snapshot.hasAssignedSchema)
        assertEquals(rootAnswer, snapshot.valuesByFieldDefinitionId[noteDefinitionId]?.textValue)
        assertNull(
            snapshot.valuesByFieldDefinitionId[countDefinitionId],
            "A repetition's answer must not be visible to a condition about the resource",
        )
        verify(fixture.valueSetRepository).findRoot(assignmentId)
    }

    @Test
    fun `provisioning a repetition creates its value set when none exists yet`()
    {
        val fixture = fixture()
        val newOccurrencePath = "items[1]"

        fixture.service.createOccurrenceValueSet(resource, newOccurrencePath)

        val created = fixture.savedSets.single()
        assertEquals(assignmentId, created.schemaAssignmentId)
        assertEquals(FieldValueSetKind.OCCURRENCE, created.setKind)
        assertEquals(newOccurrencePath, created.occurrencePath)
    }

    @Test
    fun `provisioning a repetition that already has a value set does not create another`()
    {
        val fixture = fixture()

        fixture.service.createOccurrenceValueSet(resource, occurrencePath)

        assertTrue(fixture.savedSets.isEmpty(), "An existing repetition set must not be duplicated")
    }

    @Test
    fun `provisioning a repetition without a schema assignment refuses`()
    {
        val fixture = fixture(assigned = false)

        assertThrows<IllegalStateException> {
            fixture.service.createOccurrenceValueSet(resource, "items[0]")
        }
    }
}
