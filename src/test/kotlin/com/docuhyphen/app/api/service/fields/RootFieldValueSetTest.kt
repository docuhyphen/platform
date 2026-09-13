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
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Answers belong to a Value Set rather than to the assignment itself. A resource that is assigned a
 * schema answers as itself through one root set, which is what the existing Exchange surfaces and
 * the workflow evaluator must keep reading, while a set that holds a repetition of a group belongs
 * to whichever process created it and must stay out of those projections.
 *
 * The resource adapter is stubbed because these rules belong to the Fields engine rather than to any
 * one resource domain.
 */
class RootFieldValueSetTest
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
    private val occurrenceValueSetId = UUID.randomUUID()
    private val occurrencePath = "items[0]"

    private val noteContractId = UUID.randomUUID()
    private val noteDefinitionId = UUID.randomUUID()
    private val countContractId = UUID.randomUUID()
    private val countDefinitionId = UUID.randomUUID()

    private val rootAnswer = "Answer given as the resource"
    private val occurrenceAnswer = "Answer given for one repetition"

    private data class Fixture(
        val service: SchemaAssignmentService,
        val queryService: ExchangeFieldQueryService,
        val storedByValueSet: MutableMap<UUID, MutableMap<UUID, FieldValue>>,
        val savedSets: MutableList<FieldValueSet>,
        val deletedSets: MutableList<FieldValueSet>,
        val deletedValues: MutableList<FieldValue>,
        val valueSetRepository: FieldValueSetRepository,
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
     * @param rootSetExists whether that assignment already owns its root Value Set.
     * @param rootAnswers answers already stored in the root set, keyed by field contract.
     * @param occurrenceAnswers answers already stored in a repetition set of the same assignment.
     * @param configuredDefault a default configured on the count binding of the published version.
     */
    private fun fixture(
        assigned: Boolean = true,
        rootSetExists: Boolean = true,
        rootAnswers: Map<UUID, String> = emptyMap(),
        occurrenceAnswers: Map<UUID, String> = emptyMap(),
        configuredDefault: String? = null,
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
            Unit
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
            Unit
        }

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
            ExchangeFieldQueryService(
                assignmentRepository = assignmentRepository,
                fieldValueRepository = valueRepository,
                fieldValueSetRepository = valueSetRepository,
                fieldContractRepository = contractRepository,
                selectionRepository = selectionRepository,
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

    // ── The root set exists ────────────────────────────────────────────────────

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

    // ── Answers are written into the root set ──────────────────────────────────

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
        // A repetition answers the same question separately and is not touched by a root write.
        assertEquals(
            occurrenceAnswer,
            fixture.storedByValueSet[occurrenceValueSetId]?.get(noteContractId)?.textValue,
        )
    }

    // ── Existing resource projections keep reading the root set ────────────────

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

    // ── Provisioning a repetition's value set ───────────────────────────────────

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
