package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateBindingDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateDefinition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementGroup
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateScopeKind
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateStatus
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersion
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateVersionCapability
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.SchemaAssignmentSource
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementCurrentRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRevisionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingDispositionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingEvidenceLinkRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateBindingSubstituteRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidenceAcceptedValueRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateEvidencePolicyRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementGroupRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.fields.FieldsAccessContext
import com.docuhyphen.app.api.service.fields.FieldsResourceRef
import com.docuhyphen.app.api.service.fields.PublishedSchemaAssignmentCommand
import com.docuhyphen.app.api.service.fields.SchemaAssignmentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestTemplateMaterializerTest
{
    private val requestId = UUID.randomUUID()
    private val exchangeId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val templateDefinitionId = UUID.randomUUID()
    private val templateVersionId = UUID.randomUUID()
    private val schemaVersionId = UUID.randomUUID()
    private val fieldRequirementId = UUID.randomUUID()
    private val documentRequirementId = UUID.randomUUID()
    private val fieldBindingId = UUID.randomUUID()
    private val documentBindingId = UUID.randomUUID()
    private val collectedFieldId = UUID.randomUUID()
    private val access = FieldsAccessContext(PrincipalRef.user(actorId), AuthorizationContext())

    @Test
    fun `materializing a template creates root Requirement revisions and assigns the exact Schema Version`()
    {
        val fixture = fixture()

        val result = fixture.service.materialize(request(), access)

        assertEquals(2, result.requirementCount)
        val requirements = fixture.savedRequirements
        assertEquals(
            listOf(fieldBindingId, documentBindingId),
            requirements.map { it.sourceTemplateBindingId },
        )
        requirements.forEach { requirement ->
            assertEquals(requestId, requirement.informationRequestId)
            assertEquals(templateVersionId, requirement.sourceTemplateVersionId)
            assertEquals("root", requirement.occurrencePath)
        }

        val revisions = fixture.savedRevisions
        assertEquals(requirements.map { it.id }, revisions.map { it.informationRequestRequirementId })
        revisions.forEach { revision ->
            assertEquals(1, revision.revisionNumber)
            assertEquals(1, revision.optimisticVersion)
            assertEquals("root", revision.occurrencePath)
            assertTrue(
                revision.configurationHashSha256.matches(Regex("[0-9a-f]{64}")),
                "Each revision must carry a stable SHA-256 configuration hash",
            )
        }
        assertNotEquals(
            revisions[0].configurationHashSha256,
            revisions[1].configurationHashSha256,
            "Different frozen policies should not collapse to one hash",
        )
        assertEquals(requirements.map { it.id }, fixture.savedCurrents.map { it.informationRequestRequirementId })
        assertEquals(revisions.map { it.id }, fixture.savedCurrents.map { it.currentRevisionId })

        val schemaCommand = argumentCaptor<PublishedSchemaAssignmentCommand>()
        verify(fixture.schemaAssignmentService).assignPublishedSchemaVersion(schemaCommand.capture())
        assertEquals(ResourceType.INFORMATION_REQUEST.name, schemaCommand.firstValue.resource.resourceType)
        assertEquals(requestId, schemaCommand.firstValue.resource.resourceId)
        assertEquals(schemaVersionId, schemaCommand.firstValue.schemaVersionId)
        assertEquals(SchemaAssignmentSource.API, schemaCommand.firstValue.source)
    }

    @Test
    fun `unserved runtime capability prevents partial materialization`()
    {
        val fixture = fixture(
            unserved = listOf(
                InformationRequestCapabilityRequirement(
                    InformationRequestCapability.STRUCTURED_RESPONSE,
                    1,
                ),
            ),
        )

        val refusal = assertThrows<InformationRequestCapabilityNotInstalledException> {
            fixture.service.materialize(request(), access)
        }

        assertEquals(InformationRequestCapability.STRUCTURED_RESPONSE, refusal.unserved.single().capability)
        verify(fixture.requirementRepository, never()).save(any())
        verify(fixture.schemaAssignmentService, never()).assignPublishedSchemaVersion(any())
    }

    @Test
    fun `a request without Field requirements does not receive a Schema assignment`()
    {
        val fixture = fixture(fieldBearing = false)

        val result = fixture.service.materialize(request(), access)

        assertEquals(1, result.requirementCount)
        assertEquals(listOf(documentBindingId), fixture.savedRequirements.map { it.sourceTemplateBindingId })
        verify(fixture.schemaAssignmentService, never()).assignPublishedSchemaVersion(any())
    }

    private data class Fixture(
        val service: InformationRequestTemplateMaterializer,
        val schemaAssignmentService: SchemaAssignmentService,
        val requirementRepository: InformationRequestRequirementRepository,
        val savedRequirements: MutableList<InformationRequestRequirement>,
        val savedRevisions: MutableList<com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision>,
        val savedCurrents: MutableList<com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent>,
        val savedGroupOccurrences: MutableList<InformationRequestGroupOccurrence>,
    )

    private fun fixture(
        unserved: List<InformationRequestCapabilityRequirement> = emptyList(),
        fieldBearing: Boolean = true,
        groups: List<InformationRequestTemplateRequirementGroup> = emptyList(),
        anchoredBindings: List<Pair<InformationRequestTemplateRequirement, InformationRequestTemplateRequirementBinding>> =
            emptyList(),
    ): Fixture
    {
        val version = InformationRequestTemplateVersion().apply {
            id = templateVersionId
            this.templateDefinitionId = this@InformationRequestTemplateMaterializerTest.templateDefinitionId
            versionNumber = 1
            status = InformationRequestTemplateStatus.PUBLISHED
            this.schemaVersionId = this@InformationRequestTemplateMaterializerTest.schemaVersionId
        }
        val definition = InformationRequestTemplateDefinition().apply {
            id = templateDefinitionId
            scopeKind = InformationRequestTemplateScopeKind.ORGANIZATION
            scopeOrgId = organizationId
            namespace = "process"
            templateKey = "request-pattern"
            displayName = "Request pattern"
        }
        val fieldRequirement = requirement(fieldRequirementId, "requested-data", InformationRequestRequirementType.FIELD)
        val documentRequirement = requirement(documentRequirementId, "supporting-document", InformationRequestRequirementType.DOCUMENT)
        val fieldBinding = binding(fieldBindingId, fieldRequirementId, "Provide the requested data").apply {
            collectedFieldDefinitionId = collectedFieldId
        }
        val documentBinding = binding(documentBindingId, documentRequirementId, "Provide the supporting document")
        val templateRequirements = (if (fieldBearing)
            listOf(fieldRequirement, documentRequirement) else listOf(documentRequirement)) +
            anchoredBindings.map { it.first }
        val templateBindings = (if (fieldBearing)
            listOf(fieldBinding, documentBinding) else listOf(documentBinding)) +
            anchoredBindings.map { it.second }
        val capability = InformationRequestTemplateVersionCapability().apply {
            templateVersionId = this@InformationRequestTemplateMaterializerTest.templateVersionId
            capabilityKey = InformationRequestCapability.STRUCTURED_RESPONSE
            requiredContractVersion = 1
        }

        val versionRepository = mock<InformationRequestTemplateVersionRepository>()
        whenever(versionRepository.findById(templateVersionId)).thenReturn(version)
        val definitionRepository = mock<InformationRequestTemplateDefinitionRepository>()
        whenever(definitionRepository.findById(templateDefinitionId)).thenReturn(definition)
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        whenever(bindingRepository.findOrdered(templateVersionId)).thenReturn(templateBindings)
        val requirementTemplateRepository = mock<InformationRequestTemplateRequirementRepository>()
        whenever(requirementTemplateRepository.findAllByDefinition(templateDefinitionId))
            .thenReturn(templateRequirements)
        val groupRepository = mock<InformationRequestTemplateRequirementGroupRepository>()
        whenever(groupRepository.findForVersion(templateVersionId)).thenReturn(groups)
        val dispositionRepository = mock<InformationRequestTemplateBindingDispositionRepository>()
        whenever(dispositionRepository.findForVersion(templateVersionId)).thenReturn(
            listOf(disposition(fieldBindingId, InformationRequestResponseDisposition.PROVIDED)),
        )
        val evidencePolicyRepository = mock<InformationRequestTemplateEvidencePolicyRepository>()
        whenever(evidencePolicyRepository.findForVersion(templateVersionId)).thenReturn(emptyList())
        val acceptedValueRepository = mock<InformationRequestTemplateEvidenceAcceptedValueRepository>()
        whenever(acceptedValueRepository.findForVersion(templateVersionId)).thenReturn(emptyList())
        val substituteRepository = mock<InformationRequestTemplateBindingSubstituteRepository>()
        whenever(substituteRepository.findForVersion(templateVersionId)).thenReturn(emptyList())
        val evidenceLinkRepository = mock<InformationRequestTemplateBindingEvidenceLinkRepository>()
        whenever(evidenceLinkRepository.findForVersion(templateVersionId)).thenReturn(emptyList())
        val capabilityRepository = mock<InformationRequestTemplateVersionCapabilityRepository>()
        whenever(capabilityRepository.findForVersion(templateVersionId)).thenReturn(listOf(capability))
        val capabilityRegistry = mock<InformationRequestCapabilityExecutorRegistry>()
        whenever(capabilityRegistry.unserved(any<Iterable<InformationRequestCapabilityRequirement>>()))
            .thenReturn(unserved)

        val savedRequirements = mutableListOf<InformationRequestRequirement>()
        val requestRequirementRepository = mock<InformationRequestRequirementRepository>()
        whenever(requestRequirementRepository.findForRequest(requestId)).thenReturn(emptyList())
        whenever(requestRequirementRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestRequirement>(0).also { savedRequirements += it }
        }

        val savedRevisions =
            mutableListOf<com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision>()
        val revisionRepository = mock<InformationRequestRequirementRevisionRepository>()
        whenever(revisionRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<com.docuhyphen.app.api.model.entity.InformationRequestRequirementRevision>(0)
                .also { savedRevisions += it }
        }

        val savedCurrents =
            mutableListOf<com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent>()
        val currentRepository = mock<InformationRequestRequirementCurrentRepository>()
        whenever(currentRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<com.docuhyphen.app.api.model.entity.InformationRequestRequirementCurrent>(0)
                .also { savedCurrents += it }
        }

        val schemaAssignmentService = mock<SchemaAssignmentService>()

        val savedGroupOccurrences = mutableListOf<InformationRequestGroupOccurrence>()
        val groupOccurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        whenever(groupOccurrenceRepository.save(any())).thenAnswer { invocation ->
            invocation.getArgument<InformationRequestGroupOccurrence>(0).also { savedGroupOccurrences += it }
        }

        return Fixture(
            service = InformationRequestTemplateMaterializer(
                templateVersionRepository = versionRepository,
                templateDefinitionRepository = definitionRepository,
                templateBindingRepository = bindingRepository,
                templateRequirementRepository = requirementTemplateRepository,
                groupRepository = groupRepository,
                groupOccurrenceRepository = groupOccurrenceRepository,
                dispositionRepository = dispositionRepository,
                evidencePolicyRepository = evidencePolicyRepository,
                acceptedValueRepository = acceptedValueRepository,
                substituteRepository = substituteRepository,
                evidenceLinkRepository = evidenceLinkRepository,
                capabilityRepository = capabilityRepository,
                capabilityRegistry = capabilityRegistry,
                requirementRepository = requestRequirementRepository,
                revisionRepository = revisionRepository,
                currentRepository = currentRepository,
                schemaAssignmentService = schemaAssignmentService,
            ),
            schemaAssignmentService = schemaAssignmentService,
            requirementRepository = requestRequirementRepository,
            savedRequirements = savedRequirements,
            savedRevisions = savedRevisions,
            savedCurrents = savedCurrents,
            savedGroupOccurrences = savedGroupOccurrences,
        )
    }

    private fun request() = InformationRequest().apply {
        id = requestId
        this.exchangeId = this@InformationRequestTemplateMaterializerTest.exchangeId
        this.templateVersionId = this@InformationRequestTemplateMaterializerTest.templateVersionId
        ownerType = InformationRequestOwnerType.ORGANIZATION
        ownerOrganizationId = organizationId
        createdByAppUserId = actorId
    }

    private fun requirement(
        id: UUID,
        key: String,
        type: InformationRequestRequirementType,
    ) = InformationRequestTemplateRequirement().apply {
        this.id = id
        templateDefinitionId = this@InformationRequestTemplateMaterializerTest.templateDefinitionId
        requirementKey = key
        requirementType = type
    }

    private fun binding(
        id: UUID,
        requirementId: UUID,
        prompt: String,
    ) = InformationRequestTemplateRequirementBinding().apply {
        this.id = id
        templateVersionId = this@InformationRequestTemplateMaterializerTest.templateVersionId
        templateDefinitionId = this@InformationRequestTemplateMaterializerTest.templateDefinitionId
        templateRequirementId = requirementId
        templateSectionId = UUID.randomUUID()
        displayOrder = 1
        this.prompt = prompt
    }

    private fun disposition(
        bindingId: UUID,
        disposition: InformationRequestResponseDisposition,
    ) = InformationRequestTemplateBindingDisposition().apply {
        templateBindingId = bindingId
        templateVersionId = this@InformationRequestTemplateMaterializerTest.templateVersionId
        this.disposition = disposition
    }

    private fun group(
        id: UUID,
        key: String,
        minOccurrences: Int,
        parentGroupId: UUID? = null,
    ) = InformationRequestTemplateRequirementGroup().apply {
        this.id = id
        templateVersionId = this@InformationRequestTemplateMaterializerTest.templateVersionId
        groupKey = key
        this.parentGroupId = parentGroupId
        this.minOccurrences = minOccurrences
    }

    private fun anchoredBinding(
        id: UUID,
        prompt: String,
        anchorKey: String,
        type: InformationRequestRequirementType = InformationRequestRequirementType.DOCUMENT,
    ): Pair<InformationRequestTemplateRequirement, InformationRequestTemplateRequirementBinding>
    {
        val requirementId = UUID.randomUUID()
        return requirement(requirementId, "anchored-$anchorKey-$id", type) to
            binding(id, requirementId, prompt).apply { occurrenceAnchorKey = anchorKey }
    }

    // ── Repeatable group occurrences ────────────────────────────────────────────

    @Test
    fun `an anchored binding materializes one Requirement per group occurrence`()
    {
        val groupId = UUID.randomUUID()
        val (invoiceRequirement, invoiceBinding) =
            anchoredBinding(UUID.randomUUID(), "Provide the item", "items")
        val fixture = fixture(
            groups = listOf(group(groupId, "items", minOccurrences = 2)),
            anchoredBindings = listOf(invoiceRequirement to invoiceBinding),
        )

        val result = fixture.service.materialize(request(), access)

        assertEquals(4, result.requirementCount, "Two root bindings plus one Requirement per each of two occurrences")
        val anchoredRequirements = fixture.savedRequirements.filter { it.sourceTemplateBindingId == invoiceBinding.id }
        assertEquals(listOf("items[0]", "items[1]"), anchoredRequirements.map { it.occurrencePath })

        assertEquals(2, fixture.savedGroupOccurrences.size)
        assertEquals(listOf("items[0]", "items[1]"), fixture.savedGroupOccurrences.map { it.occurrencePath })
        fixture.savedGroupOccurrences.forEach { occurrence ->
            assertEquals(requestId, occurrence.informationRequestId)
            assertEquals(groupId, occurrence.sourceTemplateGroupId)
            assertEquals(null, occurrence.parentOccurrenceId)
        }
        assertEquals(listOf(0, 1), fixture.savedGroupOccurrences.map { it.occurrenceIndex })
    }

    @Test
    fun `a nested group's occurrences are materialized under each of its parent's occurrences`()
    {
        val parentGroupId = UUID.randomUUID()
        val childGroupId = UUID.randomUUID()
        val (lineItemRequirement, lineItemBinding) =
            anchoredBinding(UUID.randomUUID(), "Provide the entry", "entries")
        val fixture = fixture(
            groups = listOf(
                group(parentGroupId, "items", minOccurrences = 2),
                group(childGroupId, "entries", minOccurrences = 2, parentGroupId = parentGroupId),
            ),
            anchoredBindings = listOf(lineItemRequirement to lineItemBinding),
        )

        fixture.service.materialize(request(), access)

        val childOccurrences = fixture.savedGroupOccurrences.filter { it.sourceTemplateGroupId == childGroupId }
        assertEquals(
            listOf(
                "items[0]/entries[0]", "items[0]/entries[1]",
                "items[1]/entries[0]", "items[1]/entries[1]",
            ),
            childOccurrences.map { it.occurrencePath },
        )
        val parentOccurrencesById = fixture.savedGroupOccurrences
            .filter { it.sourceTemplateGroupId == parentGroupId }
            .associateBy { it.id }
        childOccurrences.forEach { child ->
            val parent = requireNotNull(parentOccurrencesById[child.parentOccurrenceId])
            assertTrue(
                child.occurrencePath.startsWith(parent.occurrencePath),
                "A nested occurrence's path must be rooted at its parent occurrence's own path",
            )
        }
        val anchoredRequirements = fixture.savedRequirements.filter { it.sourceTemplateBindingId == lineItemBinding.id }
        assertEquals(4, anchoredRequirements.size)
    }

    @Test
    fun `a group with no minimum occurrences starts with none, so its anchored binding materializes nothing`()
    {
        val groupId = UUID.randomUUID()
        val (formRequirement, formBinding) =
            anchoredBinding(UUID.randomUUID(), "Provide the attachment", "attachments")
        val fixture = fixture(
            groups = listOf(group(groupId, "attachments", minOccurrences = 0)),
            anchoredBindings = listOf(formRequirement to formBinding),
        )

        val result = fixture.service.materialize(request(), access)

        assertEquals(2, result.requirementCount, "Only the two root bindings materialize")
        assertTrue(fixture.savedGroupOccurrences.isEmpty())
        assertTrue(fixture.savedRequirements.none { it.sourceTemplateBindingId == formBinding.id })
    }

    @Test
    fun `provisioning a group occurrence also provisions its Field Value Set when the request bears Fields`()
    {
        val groupId = UUID.randomUUID()
        val (invoiceRequirement, invoiceBinding) =
            anchoredBinding(UUID.randomUUID(), "Provide the item", "items")
        val fixture = fixture(
            groups = listOf(group(groupId, "items", minOccurrences = 1)),
            anchoredBindings = listOf(invoiceRequirement to invoiceBinding),
        )

        fixture.service.materialize(request(), access)

        verify(fixture.schemaAssignmentService).createOccurrenceValueSet(
            FieldsResourceRef(ResourceType.INFORMATION_REQUEST.name, requestId),
            "items[0]",
        )
    }

    @Test
    fun `a group occurrence does not provision a Field Value Set when the request has no Field requirements`()
    {
        val groupId = UUID.randomUUID()
        val (formRequirement, formBinding) =
            anchoredBinding(UUID.randomUUID(), "Provide the attachment", "attachments")
        val fixture = fixture(
            fieldBearing = false,
            groups = listOf(group(groupId, "attachments", minOccurrences = 1)),
            anchoredBindings = listOf(formRequirement to formBinding),
        )

        fixture.service.materialize(request(), access)

        verify(fixture.schemaAssignmentService, never()).createOccurrenceValueSet(any(), any())
    }
}
