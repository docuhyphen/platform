package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldDataClassification
import com.docuhyphen.app.api.model.entity.FieldDefinition
import com.docuhyphen.app.api.model.entity.FieldLifecycleStatus
import com.docuhyphen.app.api.model.entity.FieldScopeKind
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueProvenance
import com.docuhyphen.app.api.model.entity.FieldValueRevision
import com.docuhyphen.app.api.model.entity.FieldValueRevisionSelection
import com.docuhyphen.app.api.model.entity.FieldValueSelection
import com.docuhyphen.app.api.model.entity.FieldValueSet
import com.docuhyphen.app.api.model.entity.FieldValueSetKind
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.SchemaAssignment
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.model.entity.SchemaDefinition
import com.docuhyphen.app.api.model.entity.SchemaFieldBinding
import com.docuhyphen.app.api.model.entity.SchemaVersion
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldDefinitionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRevisionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRevisionSelectionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSetRepository
import com.docuhyphen.app.api.repository.fields.SchemaAssignmentRepository
import com.docuhyphen.app.api.repository.fields.SchemaDefinitionRepository
import com.docuhyphen.app.api.repository.fields.SchemaFieldBindingRepository
import com.docuhyphen.app.api.repository.fields.SchemaVersionRepository
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ScopeReference
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * In-memory wiring of [SchemaAssignmentService] and its collaborators for tests about the Fields
 * engine itself rather than about any one resource domain. The resource adapter is stubbed for the
 * same reason: existence, authorization, and lifecycle belong to the owning domain.
 *
 * Repositories are recorded rather than merely stubbed, so a test can assert what was stored, what
 * was appended, and what was left alone.
 *
 * The schema holds three questions: a short-text note, a second short-text note, and a multi-select
 * set of options.
 */
internal class SchemaAssignmentFieldsFixture(
    val principal: PrincipalRef = PrincipalRef.user(UUID.randomUUID()),
    private val sessionRef: String? = null,
    private val assigned: Boolean = true,
    private val rootSetExists: Boolean = true,
    private val rootAnswers: Map<Question, StoredAnswer> = emptyMap(),
    private val configuredDefault: String? = null,
    private val ownerScope: ScopeReference? = null,
    private val schemaScope: ScopeReference? = null,
    private val ownerlessResource: Boolean = false,
    private val mutationEntitlementFrozen: Boolean = false,
    rootSetRevision: Long = 1,
    occurrenceSetRevision: Long = 1,
    externalCaller: Boolean = false,
    bindingPolicy: FieldBindingPolicy? = null,
    val resourceType: String = "EXCHANGE",
    val resourceId: UUID = UUID.randomUUID(),
    private val requiredFields: Boolean = false,
)
{
    val organizationId: UUID = UUID.randomUUID()
    val schemaDefinitionId: UUID = UUID.randomUUID()
    val schemaVersionId: UUID = UUID.randomUUID()
    val schemaDisplayName = "Process data"
    val assignmentId: UUID = UUID.randomUUID()
    val rootValueSetId: UUID = UUID.randomUUID()
    val occurrenceValueSetId: UUID = UUID.randomUUID()

    val noteContractId: UUID = UUID.randomUUID()
    val noteDefinitionId: UUID = UUID.randomUUID()
    val secondNoteContractId: UUID = UUID.randomUUID()
    val secondNoteDefinitionId: UUID = UUID.randomUUID()
    val optionsContractId: UUID = UUID.randomUUID()
    val optionsDefinitionId: UUID = UUID.randomUUID()

    /** Answers currently stored, keyed by Value Set and then by field contract. */
    val storedByValueSet: MutableMap<UUID, MutableMap<UUID, FieldValue>> = mutableMapOf(
        rootValueSetId to mutableMapOf(),
        occurrenceValueSetId to mutableMapOf(),
    )
    val storedSelections: MutableMap<UUID, MutableList<FieldValueSelection>> = mutableMapOf()
    val savedAssignments: MutableList<SchemaAssignment> = mutableListOf()
    val savedSets: MutableList<FieldValueSet> = mutableListOf()
    val updatedSets: MutableList<FieldValueSet> = mutableListOf()

    /** The revision each Value Set update wrote, in the order the updates happened. */
    val updatedSetRevisions: MutableList<Long> = mutableListOf()

    /** Every set a caller resolved through the locking read, in the order they were locked. */
    val lockedSets: MutableList<FieldValueSetRef> = mutableListOf()

    /** Every caller the resource was asked to authorize for a read, in the order they were asked. */
    val viewAuthorizations: MutableList<Pair<PrincipalRef, AuthorizationContext>> = mutableListOf()
    val deletedSets: MutableList<FieldValueSet> = mutableListOf()
    val deletedValues: MutableList<FieldValue> = mutableListOf()
    val savedRevisions: MutableList<FieldValueRevision> = mutableListOf()
    val savedRevisionSelections: MutableList<FieldValueRevisionSelection> = mutableListOf()
    val auditDrafts: MutableList<AuditEventDraft> = mutableListOf()

    val valueSetRepository: FieldValueSetRepository = mock()
    val subscriptionGuard: BusinessFieldsSubscriptionGuard = mock()
    val service: SchemaAssignmentService

    /** The caller every command built by this fixture acts for. */
    val access: FieldsAccessContext = FieldsAccessContext(principal, AuthorizationContext(sessionRef = sessionRef))

    val resource: FieldsResourceRef = FieldsResourceRef(resourceType, resourceId)

    private var currentRootSet: FieldValueSet? = null

    /** The set the resource answers with as itself, as the repository currently holds it. */
    val rootSet = FieldValueSet().apply {
        id = rootValueSetId
        schemaAssignmentId = assignmentId
        setKind = FieldValueSetKind.ROOT
        revision = rootSetRevision
    }

    /** One repetition of a repeatable group, addressed by its own path within the assignment. */
    val occurrenceSet = FieldValueSet().apply {
        id = occurrenceValueSetId
        schemaAssignmentId = assignmentId
        setKind = FieldValueSetKind.OCCURRENCE
        occurrencePath = OCCURRENCE_PATH
        revision = occurrenceSetRevision
    }

    /**
     * One of the schema's questions, so a test can seed an answer before the fixture has generated
     * the field contract identifiers.
     */
    enum class Question
    {
        NOTE,
        SECOND_NOTE,
        OPTIONS,
    }

    /** A stored answer's canonical text and the authorship recorded against it. */
    data class StoredAnswer(
        val text: String,
        val principal: PrincipalRef,
        val sessionRef: String? = null,
        val provenance: FieldValueProvenance = FieldValueProvenance.USER,
    )

    fun contractOf(question: Question): UUID = when (question)
    {
        Question.NOTE -> noteContractId
        Question.SECOND_NOTE -> secondNoteContractId
        Question.OPTIONS -> optionsContractId
    }

    init
    {
        rootAnswers.forEach { (question, answer) ->
            val contractId = contractOf(question)
            storedByValueSet.getValue(rootValueSetId)[contractId] = storedValue(contractId, rootValueSetId, answer)
        }

        val adapter = mock<FieldResourceAdapter>()
        whenever(adapter.resourceType).thenReturn(resourceType)
        whenever(adapter.exists(resourceId)).thenReturn(true)
        whenever(adapter.schemaAssignmentMutable(resourceId)).thenReturn(true)
        whenever(adapter.valuesEditable(resourceId)).thenReturn(true)
        whenever(adapter.bindingPolicy).thenReturn(bindingPolicy ?: AudienceBindingPolicy(externalCaller))
        whenever(adapter.authorizeViewFields(any(), any(), any())).thenAnswer { invocation ->
            viewAuthorizations += invocation.getArgument<PrincipalRef>(1) to
                invocation.getArgument<AuthorizationContext>(2)
            Unit
        }
        whenever(adapter.ownerScope(resourceId)).thenReturn(
            if (ownerlessResource) null else ownerScope ?: ScopeReference.Organization(organizationId),
        )
        whenever(adapter.subscriptionContext(resourceId)).thenReturn(null)
        whenever(adapter.mutationEntitlementFrozen(resourceId)).thenReturn(mutationEntitlementFrozen)

        val registry = mock<FieldResourceAdapterRegistry>()
        whenever(registry.adapterFor(resourceType)).thenReturn(adapter)

        // The Schema's own owner, which an assignment copies rather than deciding for itself.
        val schemaOwner = schemaScope ?: ScopeReference.Organization(organizationId)
        val assignment = SchemaAssignment().apply {
            id = assignmentId
            resourceType = this@SchemaAssignmentFieldsFixture.resourceType
            resourceId = this@SchemaAssignmentFieldsFixture.resourceId
            schemaVersionId = this@SchemaAssignmentFieldsFixture.schemaVersionId
            scopeKind = scopeKindOf(schemaOwner)
            scopeOrgId = (schemaOwner as? ScopeReference.Organization)?.organizationId
            scopeUserId = (schemaOwner as? ScopeReference.Personal)?.userId
        }
        val assignmentRepository = mock<SchemaAssignmentRepository>()
        whenever(assignmentRepository.findByResource(resourceType, resourceId))
            .thenReturn(if (assigned) assignment else null)
        whenever(assignmentRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<SchemaAssignment>(0).also {
                it.id = assignmentId
                savedAssignments += it
            }
        }

        val definition = SchemaDefinition().apply {
            id = schemaDefinitionId
            scopeKind = scopeKindOf(schemaOwner)
            scopeOrgId = (schemaOwner as? ScopeReference.Organization)?.organizationId
            scopeUserId = (schemaOwner as? ScopeReference.Personal)?.userId
            namespace = "process"
            schemaKey = "process-data"
            displayName = schemaDisplayName
            targetResourceType = resourceType
            status = FieldLifecycleStatus.PUBLISHED
        }
        val definitionRepository = mock<SchemaDefinitionRepository>()
        whenever(definitionRepository.findById(schemaDefinitionId)).thenReturn(definition)

        val version = SchemaVersion().apply {
            id = schemaVersionId
            schemaDefinitionId = this@SchemaAssignmentFieldsFixture.schemaDefinitionId
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
                binding(secondNoteContractId, 1, default = configuredDefault),
                binding(optionsContractId, 2),
            ),
        )

        val contracts = listOf(
            contract(noteContractId, noteDefinitionId, "Recorded note"),
            contract(secondNoteContractId, secondNoteDefinitionId, "Second recorded note"),
            contract(
                optionsContractId, optionsDefinitionId, "Recorded options", FieldValueType.MULTI_SELECT,
                options = OPTION_CODES,
            ),
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
                    fieldKey = fieldKeyOf(c.id)
                    status = FieldLifecycleStatus.PUBLISHED
                },
            )
        }

        val valueRepository = mock<FieldValueRepository>()
        whenever(valueRepository.findByAssignment(assignmentId))
            .thenAnswer { storedByValueSet.values.flatMap { it.values } }
        whenever(valueRepository.findByValueSet(any())).thenAnswer { invocation ->
            storedByValueSet[invocation.getArgument<UUID>(0)]?.values?.toList() ?: emptyList<FieldValue>()
        }
        whenever(valueRepository.findByValueSetAndContract(any(), any())).thenAnswer { invocation ->
            storedByValueSet[invocation.getArgument<UUID>(0)]?.get(invocation.getArgument<UUID>(1))
        }
        whenever(valueRepository.save(any())).thenAnswer { invocation -> store(invocation.getArgument(0)) }
        whenever(valueRepository.update(any())).thenAnswer { invocation -> store(invocation.getArgument(0)) }
        whenever(valueRepository.delete(any())).thenAnswer { invocation ->
            deletedValues += invocation.getArgument<FieldValue>(0)
            Unit
        }

        // The root set an assignment currently owns, so a set created during the call is the set the
        // rest of the same call finds rather than a stale seeded one.
        currentRootSet = if (rootSetExists) rootSet else null
        whenever(valueSetRepository.findRoot(assignmentId)).thenAnswer { currentRootSet }
        whenever(valueSetRepository.findRootForUpdate(assignmentId)).thenAnswer {
            lockedSets += FieldValueSetRef.Root
            currentRootSet
        }
        whenever(valueSetRepository.findOccurrence(assignmentId, OCCURRENCE_PATH)).thenReturn(occurrenceSet)
        whenever(valueSetRepository.findOccurrenceForUpdate(assignmentId, OCCURRENCE_PATH)).thenAnswer {
            lockedSets += FieldValueSetRef.Occurrence(OCCURRENCE_PATH)
            occurrenceSet
        }
        whenever(valueSetRepository.findByAssignment(assignmentId))
            .thenReturn(if (rootSetExists) listOf(rootSet, occurrenceSet) else emptyList())
        whenever(valueSetRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<FieldValueSet>(0).also {
                savedSets += it
                if (it.setKind == FieldValueSetKind.ROOT) currentRootSet = it
            }
        }
        whenever(valueSetRepository.update(any())).thenAnswer { invocation ->
            invocation.getArgument<FieldValueSet>(0).also {
                updatedSets += it
                // Snapshotted, because the same set object is handed back on every later update.
                updatedSetRevisions += it.revision
            }
        }
        whenever(valueSetRepository.delete(any())).thenAnswer { invocation ->
            deletedSets += invocation.getArgument<FieldValueSet>(0)
            Unit
        }

        val selectionRepository = mock<FieldValueSelectionRepository>()
        whenever(selectionRepository.findByValue(any())).thenAnswer { invocation ->
            storedSelections[invocation.getArgument<UUID>(0)]?.toList() ?: emptyList<FieldValueSelection>()
        }
        whenever(selectionRepository.save(any())).thenAnswer { invocation ->
            val selection = invocation.getArgument<FieldValueSelection>(0)
            storedSelections.getOrPut(selection.fieldValueId) { mutableListOf() } += selection
            selection
        }
        whenever(selectionRepository.deleteByValue(any())).thenAnswer { invocation ->
            storedSelections.remove(invocation.getArgument<UUID>(0))?.size ?: 0
        }

        val revisionRepository = mock<FieldValueRevisionRepository>()
        whenever(revisionRepository.findLatest(any(), any())).thenAnswer { invocation ->
            val setId = invocation.getArgument<UUID>(0)
            val contractId = invocation.getArgument<UUID>(1)
            savedRevisions
                .filter { it.fieldValueSetId == setId && it.fieldContractId == contractId }
                .maxByOrNull { it.revisionNumber }
        }
        whenever(revisionRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<FieldValueRevision>(0).also { savedRevisions += it }
        }

        val revisionSelectionRepository = mock<FieldValueRevisionSelectionRepository>()
        whenever(revisionSelectionRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<FieldValueRevisionSelection>(0).also { savedRevisionSelections += it }
        }
        whenever(revisionSelectionRepository.findByRevision(any())).thenAnswer { invocation ->
            val revisionId = invocation.getArgument<UUID>(0)
            savedRevisionSelections.filter { it.fieldValueRevisionId == revisionId }
        }

        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenAnswer { invocation ->
            auditDrafts += invocation.getArgument<AuditEventDraft>(0)
            null
        }

        service = SchemaAssignmentService(
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
                bindingRepository, contractRepository, fieldDefinitionRepository, valueRepository, selectionRepository,
            ),
            validator = FieldValueValidator(FieldTypeRegistry()),
            subscriptionGuard = subscriptionGuard,
            revisionRecorder = FieldValueRevisionRecorder(revisionRepository, revisionSelectionRepository),
            auditTrail = SchemaAssignmentAuditTrail(auditRecorder),
        )
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /** Reads the answers of one set, the resource's own by default. */
    fun read(valueSet: FieldValueSetRef = FieldValueSetRef.Root, access: FieldsAccessContext = this.access) =
        service.getAssignment(FieldValueReadCommand(resource, access, valueSet))

    /** Saves answers into one set, the resource's own by default. */
    fun save(
        entries: List<FieldValueEntry>,
        precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
        valueSet: FieldValueSetRef = FieldValueSetRef.Root,
        access: FieldsAccessContext = this.access,
    ) = service.setValues(FieldValueWriteCommand(resource, access, entries, valueSet, precondition))

    /** Chooses the fixture's Schema for the resource. */
    fun assign(
        schemaDefinitionId: UUID = this.schemaDefinitionId,
        source: SchemaAssignmentSource = SchemaAssignmentSource.MANUAL,
        precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
        access: FieldsAccessContext = this.access,
    ) = requireNotNull(
        service.applySchemaAssignment(
            SchemaAssignmentCommand(
                resource = resource,
                access = access,
                operation = SchemaAssignmentOperation.ASSIGN,
                schemaDefinitionId = schemaDefinitionId,
                source = source,
                precondition = precondition,
            ),
        ),
    ) { "Choosing a Schema always answers with the assignment it created" }

    /** Removes whichever Schema currently governs the resource. */
    fun unassign(
        precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
        access: FieldsAccessContext = this.access,
    )
    {
        service.applySchemaAssignment(
            SchemaAssignmentCommand(
                resource = resource,
                access = access,
                operation = SchemaAssignmentOperation.UNASSIGN,
                precondition = precondition,
            ),
        )
    }

    /** The answer currently stored in the root set for [contractId], or null. */
    fun rootAnswer(contractId: UUID): FieldValue? = storedByValueSet[rootValueSetId]?.get(contractId)

    /** Every revision recorded for [contractId] in the root set, oldest first. */
    fun rootRevisions(contractId: UUID): List<FieldValueRevision> = savedRevisions
        .filter { it.fieldValueSetId == rootValueSetId && it.fieldContractId == contractId }
        .sortedBy { it.revisionNumber }

    fun revisionOptionCodes(revision: FieldValueRevision): List<String> = savedRevisionSelections
        .filter { it.fieldValueRevisionId == revision.id }
        .sortedBy { it.displayOrder }
        .map { it.optionCode }

    fun entry(contractId: UUID, text: String) = FieldValueEntry(contractId, JsonPrimitive(text))

    fun options(vararg codes: String) = FieldValueEntry(
        optionsContractId,
        kotlinx.serialization.json.JsonArray(codes.map { JsonPrimitive(it) }),
    )

    private fun store(value: FieldValue): FieldValue
    {
        storedByValueSet.getOrPut(value.fieldValueSetId) { mutableMapOf() }[value.fieldContractId] = value
        return value
    }

    private fun scopeKindOf(scope: ScopeReference) = when (scope)
    {
        ScopeReference.Platform -> FieldScopeKind.PLATFORM
        is ScopeReference.Organization -> FieldScopeKind.ORGANIZATION
        is ScopeReference.Personal -> FieldScopeKind.PERSONAL
    }

    private fun fieldKeyOf(contractId: UUID) = when (contractId)
    {
        noteContractId -> "recorded-note"
        secondNoteContractId -> "recorded-second-note"
        else -> "recorded-options"
    }

    private fun binding(contractId: UUID, order: Int, default: String? = null) = SchemaFieldBinding().apply {
        id = UUID.randomUUID()
        schemaVersionId = this@SchemaAssignmentFieldsFixture.schemaVersionId
        fieldContractId = contractId
        fieldDefinitionId = when (contractId)
        {
            noteContractId -> noteDefinitionId
            secondNoteContractId -> secondNoteDefinitionId
            else -> optionsDefinitionId
        }
        displayOrder = order
        isRequired = requiredFields
        isReadOnly = false
        defaultValueJson = default
        visibility = FieldDataClassification.INTERNAL
    }

    private fun contract(
        contractId: UUID,
        definitionId: UUID,
        contractLabel: String,
        type: FieldValueType = FieldValueType.SHORT_TEXT,
        options: List<String> = emptyList(),
    ) = FieldContract().apply {
        id = contractId
        fieldDefinitionId = definitionId
        valueType = type
        label = contractLabel
        optionsJson = options.mapIndexed { index, code ->
            """{"code":"$code","label":"$code","order":$index,"active":true}"""
        }.joinToString(prefix = "[", postfix = "]")
    }

    companion object
    {
        /** The option codes the multi-select question offers, in the order it offers them. */
        val OPTION_CODES = listOf("first-option", "second-option", "third-option")

        /** The path of the one repetition the fixture's assignment already holds. */
        const val OCCURRENCE_PATH = "items[0]"
    }

    private fun storedValue(contractId: UUID, valueSetId: UUID, answer: StoredAnswer) = FieldValue().apply {
        id = UUID.randomUUID()
        fieldValueSetId = valueSetId
        schemaAssignmentId = assignmentId
        fieldContractId = contractId
        resourceType = this@SchemaAssignmentFieldsFixture.resourceType
        resourceId = this@SchemaAssignmentFieldsFixture.resourceId
        valueType = FieldValueType.SHORT_TEXT
        textValue = answer.text
        provenance = answer.provenance
        updatedByPrincipalKind = answer.principal.kind
        updatedByPrincipalId = answer.principal.id
        updatedBySessionRef = answer.sessionRef
        updatedByAppUserId = FieldPrincipalProvenance(answer.principal, answer.sessionRef).legacyAppUserId
    }
}
