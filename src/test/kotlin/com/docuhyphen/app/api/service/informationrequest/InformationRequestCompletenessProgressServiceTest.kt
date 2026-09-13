package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.FieldContract
import com.docuhyphen.app.api.model.entity.FieldValue
import com.docuhyphen.app.api.model.entity.FieldValueType
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestOwnerType
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestGroupOccurrence
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
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessProgressContext
import jakarta.enterprise.inject.Instance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestCompletenessProgressServiceTest
{
    @Test
    fun `required optional hidden waived and rejected structured responses have deterministic denominators`()
    {
        val fixture = Fixture()
        val answered = fixture.requirement("root")
        val optionalUnanswered = fixture.requirement("root")
        val conditionalAnswered = fixture.requirement("root")
        val conditionalHiddenFalse = fixture.requirement("root")
        val conditionalHiddenUnknown = fixture.requirement("root")
        val waived = fixture.requirement("root")
        val reviewedRejected = fixture.requirement("root")
        fixture.bind(answered, InformationRequestRequiredness.REQUIRED)
        fixture.bind(optionalUnanswered, InformationRequestRequiredness.OPTIONAL)
        fixture.bind(conditionalAnswered, InformationRequestRequiredness.CONDITIONAL, "when-answer-needed")
        fixture.bind(conditionalHiddenFalse, InformationRequestRequiredness.CONDITIONAL, "when-answer-not-needed")
        fixture.bind(conditionalHiddenUnknown, InformationRequestRequiredness.CONDITIONAL, "when-answer-unknown")
        fixture.bind(waived, InformationRequestRequiredness.REQUIRED)
        fixture.bind(reviewedRejected, InformationRequestRequiredness.REQUIRED)
        fixture.responses += fixture.response(answered, InformationRequestResponseDisposition.PROVIDED)
        fixture.responses += fixture.response(conditionalAnswered, InformationRequestResponseDisposition.NOT_ANSWERED)
        fixture.responses += fixture.response(waived, InformationRequestResponseDisposition.WAIVED)
        fixture.extensionContributions += InformationRequestCompletenessContribution(
            itemKey = "review:${reviewedRejected.id}",
            requirementId = reviewedRejected.id,
            occurrencePath = reviewedRejected.occurrencePath,
            state = InformationRequestCompletenessItemState.REJECTED,
            contributesToDenominator = true,
            contributesToNumerator = false,
        )
        whenever(fixture.conditionEvaluationService.evaluate(fixture.request.id)).thenReturn(
            listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-answer-needed",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.TRUE,
                    sourceRequirementKeys = emptySet(),
                    fieldDefinitionIds = emptySet(),
                ),
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-answer-not-needed",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.FALSE,
                    sourceRequirementKeys = emptySet(),
                    fieldDefinitionIds = emptySet(),
                ),
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-answer-unknown",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.UNKNOWN,
                    sourceRequirementKeys = emptySet(),
                    fieldDefinitionIds = emptySet(),
                ),
            ),
        )

        val projection = fixture.service.evaluate(fixture.request.id)

        assertEquals(2, projection.completedCount)
        assertEquals(4, projection.totalCount)
        assertEquals(50, projection.percentComplete)
        assertEquals(
            listOf(
                InformationRequestCompletenessItemState.COMPLETE,
                InformationRequestCompletenessItemState.OPTIONAL_UNANSWERED,
                InformationRequestCompletenessItemState.INCOMPLETE,
                InformationRequestCompletenessItemState.HIDDEN,
                InformationRequestCompletenessItemState.HIDDEN,
                InformationRequestCompletenessItemState.COMPLETE,
                InformationRequestCompletenessItemState.REJECTED,
            ),
            projection.items.map { it.state },
        )
    }

    @Test
    fun `each occurrence of a conditional requirement follows its own occurrence's rule state`()
    {
        val fixture = Fixture()
        val firstOccurrence = fixture.requirement("items[0]")
        val secondOccurrence = fixture.requirement("items[1]")
        fixture.bind(firstOccurrence, InformationRequestRequiredness.CONDITIONAL, "when-detail-needed")
        fixture.bind(secondOccurrence, InformationRequestRequiredness.CONDITIONAL, "when-detail-needed")
        whenever(fixture.conditionEvaluationService.evaluate(fixture.request.id)).thenReturn(
            listOf(
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-detail-needed",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.TRUE,
                    sourceRequirementKeys = emptySet(),
                    fieldDefinitionIds = emptySet(),
                    occurrencePath = "items[0]",
                ),
                InformationRequestConditionEvaluationProjection(
                    ruleKey = "when-detail-needed",
                    expressionVersion = 1,
                    state = InformationRequestConditionEvaluationState.FALSE,
                    sourceRequirementKeys = emptySet(),
                    fieldDefinitionIds = emptySet(),
                    occurrencePath = "items[1]",
                ),
            ),
        )

        val projection = fixture.service.evaluate(fixture.request.id)

        assertEquals(
            listOf(
                InformationRequestCompletenessItemState.INCOMPLETE,
                InformationRequestCompletenessItemState.HIDDEN,
            ),
            projection.items.map { it.state },
        )
        assertEquals(1, projection.totalCount)
        assertEquals(0, projection.completedCount)
    }

    @Test
    fun `required collected Fields are complete only when the exact Field has a canonical answer or allowed exception`()
    {
        val fixture = Fixture()
        val emptyFirstPatch = fixture.requirement("root")
        val clearedField = fixture.requirement("root")
        val answeredField = fixture.requirement("root")
        val exception = fixture.requirement("root")
        val emptyFieldDefinitionId = UUID.randomUUID()
        val clearedFieldDefinitionId = UUID.randomUUID()
        val answeredFieldDefinitionId = UUID.randomUUID()
        fixture.bind(
            emptyFirstPatch,
            InformationRequestRequiredness.REQUIRED,
            collectedFieldDefinitionId = emptyFieldDefinitionId,
        )
        fixture.bind(
            clearedField,
            InformationRequestRequiredness.REQUIRED,
            collectedFieldDefinitionId = clearedFieldDefinitionId,
        )
        fixture.bind(
            answeredField,
            InformationRequestRequiredness.REQUIRED,
            collectedFieldDefinitionId = answeredFieldDefinitionId,
        )
        fixture.bind(exception, InformationRequestRequiredness.REQUIRED, collectedFieldDefinitionId = UUID.randomUUID())
        val emptyValueSetId = UUID.randomUUID()
        val clearedValueSetId = UUID.randomUUID()
        val answeredValueSetId = UUID.randomUUID()
        fixture.responses += fixture.response(
            emptyFirstPatch,
            InformationRequestResponseDisposition.NOT_ANSWERED,
            fieldValueSetId = emptyValueSetId,
        )
        fixture.responses += fixture.response(
            clearedField,
            InformationRequestResponseDisposition.PROVIDED,
            fieldValueSetId = clearedValueSetId,
        )
        fixture.responses += fixture.response(
            answeredField,
            InformationRequestResponseDisposition.PROVIDED,
            fieldValueSetId = answeredValueSetId,
        )
        fixture.responses += fixture.response(exception, InformationRequestResponseDisposition.WAIVED)
        fixture.storeFieldValue(clearedValueSetId, clearedFieldDefinitionId, null)
        fixture.storeFieldValue(answeredValueSetId, answeredFieldDefinitionId, "Collected answer")

        val projection = fixture.service.evaluate(fixture.request.id)

        assertEquals(4, projection.totalCount)
        assertEquals(2, projection.completedCount)
        assertEquals(
            listOf(
                InformationRequestCompletenessItemState.INCOMPLETE,
                InformationRequestCompletenessItemState.INCOMPLETE,
                InformationRequestCompletenessItemState.COMPLETE,
                InformationRequestCompletenessItemState.COMPLETE,
            ),
            projection.items.map { it.state },
        )
    }

    @Test
    fun `a removed occurrence's requirement is excluded from completeness though its row still exists`()
    {
        val fixture = Fixture()
        val activeItem = fixture.requirement("items[0]")
        val removedItem = fixture.requirement("items[1]")
        fixture.bind(activeItem, InformationRequestRequiredness.REQUIRED)
        fixture.bind(removedItem, InformationRequestRequiredness.REQUIRED)
        fixture.responses += fixture.response(activeItem, InformationRequestResponseDisposition.PROVIDED)
        fixture.responses += fixture.response(removedItem, InformationRequestResponseDisposition.NOT_ANSWERED)
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(
            listOf(
                InformationRequestGroupOccurrence().apply {
                    informationRequestId = fixture.request.id
                    occurrencePath = "items[0]"
                },
            ),
        )

        val projection = fixture.service.evaluate(fixture.request.id)

        assertEquals(1, projection.totalCount)
        assertEquals(1, projection.completedCount)
        assertEquals(listOf(InformationRequestCompletenessItemState.COMPLETE), projection.items.map { it.state })
    }

    @Test
    fun `removing a parent occurrence also excludes its nested descendant subtree from completeness`()
    {
        val fixture = Fixture()
        val parent = fixture.requirement("items[0]")
        val child = fixture.requirement("items[0]/entries[0]")
        fixture.bind(parent, InformationRequestRequiredness.REQUIRED)
        fixture.bind(child, InformationRequestRequiredness.REQUIRED)
        fixture.responses += fixture.response(parent, InformationRequestResponseDisposition.NOT_ANSWERED)
        fixture.responses += fixture.response(child, InformationRequestResponseDisposition.NOT_ANSWERED)
        whenever(fixture.occurrenceRepository.findForRequest(fixture.request.id)).thenReturn(emptyList())

        val projection = fixture.service.evaluate(fixture.request.id)

        assertEquals(0, projection.totalCount)
        assertEquals(0, projection.completedCount)
        assertEquals(emptyList<InformationRequestCompletenessItemState>(), projection.items.map { it.state })
    }

    private class Fixture
    {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            exchangeId = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
            ownerType = InformationRequestOwnerType.ORGANIZATION
            ownerOrganizationId = UUID.randomUUID()
            state = InformationRequestState.IN_PROGRESS
        }
        val requirements = mutableListOf<InformationRequestRequirement>()
        val bindings = mutableListOf<InformationRequestTemplateRequirementBinding>()
        val responses = mutableListOf<InformationRequestResponse>()
        val extensionContributions = mutableListOf<InformationRequestCompletenessContribution>()
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
        val contributionEvaluators = mock<Instance<InformationRequestCompletenessContributionEvaluator>>()
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
            whenever(requestRepository.findById(request.id)).thenReturn(request)
            whenever(requirementRepository.findForRequest(request.id)).thenAnswer { requirements }
            whenever(occurrenceRepository.findForRequest(request.id)).thenAnswer {
                requirements.map { it.occurrencePath }
                    .filterNot { InformationRequestOccurrencePath.isRoot(it) }
                    .distinct()
                    .map { path ->
                        InformationRequestGroupOccurrence().apply {
                            informationRequestId = request.id
                            occurrencePath = path
                        }
                    }
            }
            whenever(bindingRepository.findOrdered(request.templateVersionId)).thenAnswer { bindings }
            whenever(fieldValueRepository.findByValueSet(any())).thenAnswer { invocation ->
                valuesBySet[invocation.getArgument(0)].orEmpty()
            }
            whenever(fieldValueSelectionRepository.findByValue(any())).thenReturn(emptyList())
            whenever(fieldContractRepository.findByIds(any())).thenAnswer { invocation ->
                invocation.getArgument<Collection<UUID>>(0).mapNotNull(contractsById::get)
            }
            whenever(responseStore.findCurrentForRequest(request.id)).thenAnswer { responses }
            whenever(conditionEvaluationService.evaluate(request.id)).thenReturn(emptyList())
            whenever(contributionEvaluators.iterator()).thenAnswer {
                listOf(
                    object : InformationRequestCompletenessContributionEvaluator
                    {
                        override fun evaluate(context: InformationRequestCompletenessProgressContext) =
                            extensionContributions
                    },
                ).iterator()
            }
        }

        fun requirement(occurrencePath: String): InformationRequestRequirement =
            InformationRequestRequirement().apply {
                id = UUID.randomUUID()
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = UUID.randomUUID()
                sourceTemplateBindingId = UUID.randomUUID()
                this.occurrencePath = occurrencePath
            }.also { requirements += it }

        fun bind(
            requirement: InformationRequestRequirement,
            requiredness: InformationRequestRequiredness,
            ruleKey: String? = null,
            collectedFieldDefinitionId: UUID? = null,
        )
        {
            bindings += InformationRequestTemplateRequirementBinding().apply {
                id = requirement.sourceTemplateBindingId
                templateVersionId = request.templateVersionId
                templateDefinitionId = UUID.randomUUID()
                templateRequirementId = requirement.sourceTemplateRequirementId
                templateSectionId = UUID.randomUUID()
                prompt = "Provide process information"
                this.requiredness = requiredness
                conditionalRuleKey = ruleKey
                this.collectedFieldDefinitionId = collectedFieldDefinitionId
            }
        }

        fun response(
            requirement: InformationRequestRequirement,
            disposition: InformationRequestResponseDisposition,
            fieldValueSetId: UUID? = null,
        ): InformationRequestResponse =
            InformationRequestResponse().apply {
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

        fun storeFieldValue(
            valueSetId: UUID,
            fieldDefinitionId: UUID,
            textValue: String?,
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
