package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestRequirementType
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.fields.FieldContractRepository
import com.docuhyphen.app.api.repository.fields.FieldValueRepository
import com.docuhyphen.app.api.repository.fields.FieldValueSelectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestGroupOccurrenceRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRequirementRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateRequirementBindingRepository
import jakarta.enterprise.inject.Instance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestTemplateWalkingSkeletonPhase5Test
{
    @Test
    fun `basic fixture covers sparse draft response and structured completeness`()
    {
        val fieldId = UUID.randomUUID()
        val fixture = InformationRequestTemplateWalkingSkeletonFixtures
            .basicFieldDocumentResponseAttestationRequest(UUID.randomUUID(), fieldId)
        val progress = ProgressFixture(fixture)
        progress.respondByKey(
            "recorded-summary",
            InformationRequestResponseDisposition.NOT_ANSWERED,
            fieldValueSetId = UUID.randomUUID(),
            fieldTextValue = "Recorded summary",
        )
        progress.respondByKey("supporting-record", InformationRequestResponseDisposition.WAIVED)
        progress.respondByKey("response-confirmation", InformationRequestResponseDisposition.PROVIDED)

        val projection = progress.service.evaluate(progress.request.id)

        assertEquals(listOf("recorded-summary"), fieldRequirementKeys(fixture))
        assertEquals(emptyList<String>(), fixture.configuration.groups.map { it.groupKey })
        assertEquals(emptyList<String>(), fixture.configuration.conditionRules.map { it.ruleKey })
        assertEquals(3, projection.completedCount)
        assertEquals(3, projection.totalCount)
        assertEquals(100, projection.percentComplete)
    }

    @Test
    fun `stress fixture covers occurrence creation conditions sparse drafts and partial completeness`()
    {
        val subjectStatusFieldId = UUID.randomUUID()
        val delegateNoteFieldId = UUID.randomUUID()
        val fixture = InformationRequestTemplateWalkingSkeletonFixtures.multiPartyStagedEvidenceRequest(
            UUID.randomUUID(),
            subjectStatusFieldId,
            delegateNoteFieldId,
        )
        val progress = ProgressFixture(
            fixture = fixture,
            occurrencePathsByRequirementKey = mapOf(
                "subject-status" to "reported-item[0]",
                "delegate-note" to "reported-item[0]",
            ),
        )
        progress.respondByKey(
            "delegate-note",
            InformationRequestResponseDisposition.NOT_ANSWERED,
            fieldValueSetId = UUID.randomUUID(),
            fieldTextValue = "Delegate note",
        )
        progress.respondByKey("primary-evidence-record", InformationRequestResponseDisposition.WAIVED)
        progress.respondByKey("submitter-attestation", InformationRequestResponseDisposition.EXCEPTION_REQUESTED)
        whenever(progress.conditionEvaluationService.evaluate(progress.request.id)).thenReturn(
            listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-subject-is-active",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.TRUE,
                    sourceRequirementKeys = setOf("delegate-note"),
                    fieldDefinitionIds = emptySet(),
                ),
            ),
        )

        val projection = progress.service.evaluate(progress.request.id)

        val group = fixture.configuration.groups.single()
        val conditionRule = fixture.configuration.conditionRules.single()
        assertEquals("reported-item", group.groupKey)
        assertEquals(3, group.maxOccurrences)
        assertEquals("when-subject-is-active", conditionRule.ruleKey)
        assertEquals("delegate-note", conditionRule.predicates.single().sourceRequirementKey)
        assertEquals(
            setOf(subjectStatusFieldId, delegateNoteFieldId),
            fieldRequirementKeys(fixture)
                .map { key -> requirementByKey(fixture)[key]!!.collectedFieldDefinitionId }
                .toSet(),
        )
        assertEquals(3, projection.completedCount)
        assertEquals(5, projection.totalCount)
        assertEquals(60, projection.percentComplete)
    }

    private fun fieldRequirementKeys(
        fixture: InformationRequestTemplateWalkingSkeletonFixture,
    ): List<String> = requirementByKey(fixture)
        .values
        .filter { it.requirementType == InformationRequestRequirementType.FIELD }
        .map { it.requirementKey }

    private fun requirementByKey(
        fixture: InformationRequestTemplateWalkingSkeletonFixture,
    ) = fixture.configuration.sections
        .flatMap { it.requirements }
        .associateBy { it.requirementKey }

    private class ProgressFixture(
        fixture: InformationRequestTemplateWalkingSkeletonFixture,
        occurrencePathsByRequirementKey: Map<String, String> = emptyMap(),
    )
    {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.IN_PROGRESS
        }
        val responses = mutableListOf<InformationRequestResponse>()
        val responseByKey = mutableMapOf<String, InformationRequestRequirement>()
        val collectedFieldDefinitionByKey = mutableMapOf<String, UUID>()
        val valuesBySet = mutableMapOf<UUID, MutableList<FieldValue>>()
        val contractsById = mutableMapOf<UUID, FieldContract>()
        val requestRepository = mock<InformationRequestRepository>()
        val requirementRepository = mock<InformationRequestRequirementRepository>()
        val occurrenceRepository = mock<InformationRequestGroupOccurrenceRepository>()
        val bindingRepository = mock<InformationRequestTemplateRequirementBindingRepository>()
        val fieldValueRepository = mock<FieldValueRepository>()
        val fieldValueSelectionRepository = mock<FieldValueSelectionRepository>()
        val fieldContractRepository = mock<FieldContractRepository>()
        val responseStore = mock<InformationRequestResponseStore>()
        val conditionEvaluationService = mock<InformationRequestConditionEvaluationService>()
        private val contributionEvaluators = mock<Instance<InformationRequestCompletenessContributionEvaluator>>()
        val service = InformationRequestCompletenessProgressService(
            requestRepository,
            requirementRepository,
            occurrenceRepository,
            bindingRepository,
            fieldValueRepository,
            fieldValueSelectionRepository,
            fieldContractRepository,
            responseStore,
            conditionEvaluationService,
            contributionEvaluators,
        )

        init
        {
            val templateDefinitionId = UUID.randomUUID()
            val bindings = mutableListOf<InformationRequestTemplateRequirementBinding>()
            val requirements = fixture.configuration.sections
                .flatMap { section -> section.requirements.map { section to it } }
                .map { (_, authored) ->
                    val templateRequirementId = UUID.randomUUID()
                    val bindingId = UUID.randomUUID()
                    val occurrencePath = occurrencePathsByRequirementKey[authored.requirementKey] ?: "root"
                    val requirement = InformationRequestRequirement().apply {
                        id = UUID.randomUUID()
                        informationRequestId = request.id
                        sourceTemplateVersionId = request.templateVersionId
                        sourceTemplateRequirementId = templateRequirementId
                        sourceTemplateBindingId = bindingId
                        this.occurrencePath = occurrencePath
                    }
                    bindings += InformationRequestTemplateRequirementBinding().apply {
                        id = bindingId
                        templateVersionId = request.templateVersionId
                        this.templateDefinitionId = templateDefinitionId
                        this.templateRequirementId = templateRequirementId
                        templateSectionId = UUID.randomUUID()
                        prompt = authored.prompt
                        requiredness = authored.requiredness
                        conditionalRuleKey = authored.conditionalRuleKey
                        collectedFieldDefinitionId = authored.collectedFieldDefinitionId
                    }
                    responseByKey[authored.requirementKey] = requirement
                    authored.collectedFieldDefinitionId?.let {
                        collectedFieldDefinitionByKey[authored.requirementKey] = it
                    }
                    requirement
                }
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requirementRepository.findForRequest(request.id)).thenReturn(requirements)
            whenever(occurrenceRepository.findForRequest(request.id)).thenReturn(
                requirements.map { it.occurrencePath }
                    .filterNot { InformationRequestOccurrencePath.isRoot(it) }
                    .distinct()
                    .map { path ->
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            occurrencePath = path
                        }
                    },
            )
            whenever(bindingRepository.findOrdered(request.templateVersionId)).thenReturn(bindings)
            whenever(fieldValueRepository.findByValueSet(any())).thenAnswer { invocation ->
                valuesBySet[invocation.getArgument(0)].orEmpty()
            }
            whenever(fieldValueSelectionRepository.findByValue(any())).thenReturn(emptyList())
            whenever(fieldContractRepository.findByIds(any())).thenAnswer { invocation ->
                invocation.getArgument<Collection<UUID>>(0).mapNotNull(contractsById::get)
            }
            whenever(responseStore.findCurrentForRequest(request.id)).thenAnswer { responses }
            whenever(conditionEvaluationService.evaluate(request.id)).thenReturn(emptyList())
            whenever(contributionEvaluators.iterator()).thenReturn(
                mutableListOf<InformationRequestCompletenessContributionEvaluator>().iterator(),
            )
        }

        fun respondByKey(
            requirementKey: String,
            disposition: InformationRequestResponseDisposition,
            fieldValueSetId: UUID? = null,
            fieldTextValue: String? = null,
        )
        {
            val requirement = responseByKey.getValue(requirementKey)
            responses += InformationRequestResponse().apply {
                informationRequestId = request.id
                informationRequestRequirementId = requirement.id
                requirementRevisionId = UUID.randomUUID()
                occurrencePath = requirement.occurrencePath
                this.disposition = disposition
                this.fieldValueSetId = fieldValueSetId
                responseRevision = request.responseRevision
                recordedByPrincipalKind = PrincipalKind.PARTICIPANT
                recordedByPrincipalId = UUID.randomUUID()
            }
            val fieldDefinitionId = collectedFieldDefinitionByKey[requirementKey]
            if (fieldValueSetId != null && fieldDefinitionId != null && fieldTextValue != null)
            {
                storeFieldValue(fieldValueSetId, fieldDefinitionId, fieldTextValue)
            }
        }

        private fun storeFieldValue(
            valueSetId: UUID,
            fieldDefinitionId: UUID,
            textValue: String,
        )
        {
            val contractId = UUID.randomUUID()
            contractsById[contractId] = FieldContract().apply {
                id = contractId
                this.fieldDefinitionId = fieldDefinitionId
                valueType = FieldValueType.SHORT_TEXT
                label = "Collected information"
            }
            valuesBySet.getOrPut(valueSetId) { mutableListOf() } += FieldValue().apply {
                fieldValueSetId = valueSetId
                schemaAssignmentId = UUID.randomUUID()
                fieldContractId = contractId
                resourceType = "INFORMATION_REQUEST"
                resourceId = request.id
                valueType = FieldValueType.SHORT_TEXT
                this.textValue = textValue
            }
        }
    }
}
