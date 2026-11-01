package com.docuhyphen.app.api.service.fields

import com.docuhyphen.app.api.model.auth.authz.ScopeReference
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.fields.*
import com.docuhyphen.app.api.repository.fields.*
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.model.StoredAnswer
import io.quarkus.security.ForbiddenException
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

internal class SchemaAssignmentFieldsFixture(
    val principal: PrincipalRef = PrincipalRef.user(UUID.randomUUID()),
    private val sessionRef: String? = null,
    private val assigned: Boolean = true,
    private val rootSetExists: Boolean = true,
    private val rootAnswers: Map<Question, StoredAnswer> = emptyMap(),
    private val configuredDefault: String? = null,
    private val ownerScope: ScopeReference? = null,
    private val schemaScope: ScopeReference? = null,
    private val schemaStatus: FieldLifecycleStatus = FieldLifecycleStatus.PUBLISHED,
    private val ownerlessResource: Boolean = false,
    private val mutationEntitlementFrozen: Boolean = false,
    rootSetRevision: Long = 1,
    occurrenceSetRevision: Long = 1,
    externalCaller: Boolean = false,
    bindingPolicy: FieldBindingPolicy? = null,
    val resourceType: String = "EXCHANGE",
    val resourceId: UUID = UUID.randomUUID(),
    private val requiredFields: Boolean = false,
    private val manageFieldsAllowed: Boolean = true,
    private val manageSchemaAllowed: Boolean = true,
    private val editable: Boolean = true,
    private val readOnlyQuestions: Set<Question> = emptySet(),
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

    val storedByValueSet: MutableMap<UUID, MutableMap<UUID, FieldValue>> = mutableMapOf(
        rootValueSetId to mutableMapOf(),
        occurrenceValueSetId to mutableMapOf(),
    )
    val storedSelections: MutableMap<UUID, MutableList<FieldValueSelection>> = mutableMapOf()
    val savedAssignments: MutableList<SchemaAssignment> = mutableListOf()
    val savedSets: MutableList<FieldValueSet> = mutableListOf()
    val updatedSets: MutableList<FieldValueSet> = mutableListOf()

    val updatedSetRevisions: MutableList<Long> = mutableListOf()

    val lockedSets: MutableList<FieldValueSetRef> = mutableListOf()

    val viewAuthorizations: MutableList<Pair<PrincipalRef, AuthorizationContext>> = mutableListOf()
    val deletedSets: MutableList<FieldValueSet> = mutableListOf()
    val deletedValues: MutableList<FieldValue> = mutableListOf()
    val savedRevisions: MutableList<FieldValueRevision> = mutableListOf()
    val savedRevisionSelections: MutableList<FieldValueRevisionSelection> = mutableListOf()
    val auditDrafts: MutableList<AuditEventDraft> = mutableListOf()

    val valueSetRepository: FieldValueSetRepository = mock()
    val assignmentRepository: SchemaAssignmentRepository = mock()
    val subscriptionGuard: BusinessFieldsSubscriptionGuard = mock()
    val service: SchemaAssignmentService

    val access: FieldsAccessContext = FieldsAccessContext(principal, AuthorizationContext(sessionRef = sessionRef))

    val resource: FieldsResourceRef = FieldsResourceRef(resourceType, resourceId)

    private var currentRootSet: FieldValueSet? = null

    val rootSet = FieldValueSet().apply {
        id = rootValueSetId
        schemaAssignmentId = assignmentId
        setKind = FieldValueSetKind.ROOT
        revision = rootSetRevision
    }

    val occurrenceSet = FieldValueSet().apply {
        id = occurrenceValueSetId
        schemaAssignmentId = assignmentId
        setKind = FieldValueSetKind.OCCURRENCE
        occurrencePath = OCCURRENCE_PATH
        revision = occurrenceSetRevision
    }

    enum class Question
    {
        NOTE,
        SECOND_NOTE,
        OPTIONS,
    }

    fun contractOf(question: Question): UUID = when (question)
    {
        Question.NOTE -> noteContractId
        Question.SECOND_NOTE -> secondNoteContractId
        Question.OPTIONS -> optionsContractId
    }

    private val bindings: List<SchemaFieldBinding> = listOf(
        binding(noteContractId, 0),
        binding(secondNoteContractId, 1, default = configuredDefault),
        binding(optionsContractId, 2),
    )

    init
    {
        rootAnswers.forEach { (question, answer) ->
            val contractId = contractOf(question)
            storedByValueSet.getValue(rootValueSetId)[contractId] = storedValue(contractId, rootValueSetId, answer)
        }

        val adapter = mock<FieldResourceAdapter>()
        whenever(adapter.resourceType).thenReturn(resourceType)
        whenever(adapter.exists(resourceId)).thenReturn(true)
        whenever(adapter.schemaAssignmentMutable(resourceId)).thenReturn(editable)
        whenever(adapter.valuesEditable(resourceId)).thenReturn(editable)
        whenever(adapter.bindingPolicy).thenReturn(bindingPolicy ?: AudienceBindingPolicy(externalCaller))
        whenever(adapter.authorizeViewFields(any(), any(), any())).thenAnswer { invocation ->
            viewAuthorizations += invocation.getArgument<PrincipalRef>(1) to
                    invocation.getArgument<AuthorizationContext>(2)
        }
        whenever(adapter.ownerScope(resourceId)).thenReturn(
            if (ownerlessResource) null else ownerScope ?: ScopeReference.Organization(organizationId),
        )
        whenever(adapter.subscriptionContext(resourceId)).thenReturn(null)
        whenever(adapter.mutationEntitlementFrozen(resourceId)).thenReturn(mutationEntitlementFrozen)
        if (!manageFieldsAllowed) {
            whenever(adapter.authorizeManageFields(any(), any(), any()))
                .thenThrow(ForbiddenException("Access denied to edit request fields"))
        }
        if (!manageSchemaAllowed) {
            whenever(adapter.authorizeManageSchema(any(), any(), any()))
                .thenThrow(ForbiddenException("Access denied to manage the schema"))
        }

        val registry = mock<FieldResourceAdapterRegistry>()
        whenever(registry.adapterFor(resourceType)).thenReturn(adapter)

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
            status = schemaStatus
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
        whenever(bindingRepository.findByVersion(schemaVersionId)).thenReturn(bindings)

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
        }

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
                updatedSetRevisions += it.revision
            }
        }
        whenever(valueSetRepository.delete(any())).thenAnswer { invocation ->
            deletedSets += invocation.getArgument<FieldValueSet>(0)
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

    fun read(valueSet: FieldValueSetRef = FieldValueSetRef.Root, access: FieldsAccessContext = this.access) =
        service.getAssignment(FieldValueReadCommand(resource, access, valueSet))

    fun save(
        entries: List<FieldValueEntry>,
        precondition: FieldsPrecondition = FieldsPrecondition.Unconditioned,
        valueSet: FieldValueSetRef = FieldValueSetRef.Root,
        access: FieldsAccessContext = this.access,
    ) = service.setValues(FieldValueWriteCommand(resource, access, entries, valueSet, precondition))

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

    fun readState(access: FieldsAccessContext = this.access) =
        service.getAssignmentState(FieldValueReadCommand(resource, access))

    fun applyBlueprintDefaults(defaults: List<BlueprintFieldDefaultEntry>) =
        service.applyBlueprintFieldDefaultsForCreation(BlueprintFieldDefaultsCommand(resource, access, defaults))

    fun assignForCreation() = service.assignSchemaVersionForCreation(
        PublishedSchemaAssignmentCommand(resource, access, schemaVersionId),
    )

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

    fun rootAnswer(contractId: UUID): FieldValue? = storedByValueSet[rootValueSetId]?.get(contractId)

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
        isReadOnly = Question.entries.any { contractOf(it) == contractId && it in readOnlyQuestions }
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
        val OPTION_CODES = listOf("first-option", "second-option", "third-option")

        const val OCCURRENCE_PATH = "items[0]"
    }

    private fun storedValue(contractId: UUID, valueSetId: UUID, answer: StoredAnswer) = FieldValue().apply {
        id = UUID.randomUUID()
        fieldValueSetId = valueSetId
        schemaAssignmentId = assignmentId
        schemaFieldBindingId = bindings.single { it.fieldContractId == contractId }.id
        fieldContractId = contractId
        resourceType = this@SchemaAssignmentFieldsFixture.resourceType
        resourceId = this@SchemaAssignmentFieldsFixture.resourceId
        valueType = FieldValueType.SHORT_TEXT
        textValue = answer.text
        provenance = answer.provenance
        updatedByPrincipalKind = answer.principal.kind
        updatedByPrincipalId = answer.principal.id
        updatedBySessionRef = answer.sessionRef
    }
}
