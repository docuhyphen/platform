package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestRequiredness
import com.docuhyphen.app.api.model.entity.InformationRequestRequirement
import com.docuhyphen.app.api.model.entity.InformationRequestResponse
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestTemplateRequirementBinding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.InformationRequestEvidenceRequirementState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestCompletenessProgressContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class InformationRequestEvidenceCompletenessEvaluatorTest
{
    private val evaluationService: InformationRequestEvidenceEvaluationService = mock()
    private val evaluator = InformationRequestEvidenceCompletenessEvaluator(evaluationService)
    private val request = InformationRequest().apply { id = UUID.randomUUID() }

    @Test
    fun `a Document Requirement counts as complete only once its evidence completes the work`()
    {
        val required = binding(InformationRequestRequiredness.REQUIRED)
        val requirement = requirement(required)

        mapOf(
            InformationRequestEvidenceRequirementState.NOT_PROVIDED to InformationRequestCompletenessItemState.INCOMPLETE,
            InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT to InformationRequestCompletenessItemState.INCOMPLETE,
            InformationRequestEvidenceRequirementState.DEFICIENT to InformationRequestCompletenessItemState.INCOMPLETE,
            InformationRequestEvidenceRequirementState.REVIEWABLE to InformationRequestCompletenessItemState.COMPLETE,
            InformationRequestEvidenceRequirementState.SATISFIED to InformationRequestCompletenessItemState.COMPLETE,
        ).forEach { (evidenceState, expected) ->
            evaluates(requirement, evidenceState)

            val contribution = evaluator.evaluate(context(listOf(requirement), listOf(required))).single()

            assertEquals(expected, contribution.state, "for $evidenceState")
            assertTrue(contribution.contributesToDenominator)
            assertEquals(expected == InformationRequestCompletenessItemState.COMPLETE, contribution.contributesToNumerator)
            assertEquals(requirement.id, contribution.requirementId)
        }
    }

    @Test
    fun `a disposition that needs no evidence completes the Requirement, and a bare claim of provision or waiver does not`()
    {
        val required = binding(InformationRequestRequiredness.REQUIRED)
        val requirement = requirement(required)
        evaluates(requirement, InformationRequestEvidenceRequirementState.NOT_PROVIDED)

        listOf(
            InformationRequestResponseDisposition.NOT_APPLICABLE,
            InformationRequestResponseDisposition.UNAVAILABLE,
            InformationRequestResponseDisposition.EXCEPTION_REQUESTED,
            InformationRequestResponseDisposition.SATISFIED_BY_REFERENCE,
        ).forEach { disposition ->
            assertEquals(InformationRequestCompletenessItemState.COMPLETE, stateWith(requirement, required, disposition), "for $disposition")
        }
        listOf(
            InformationRequestResponseDisposition.PROVIDED,
            InformationRequestResponseDisposition.PARTIALLY_PROVIDED,
            InformationRequestResponseDisposition.WAIVED,
            InformationRequestResponseDisposition.NOT_ANSWERED,
        ).forEach { disposition ->
            assertEquals(InformationRequestCompletenessItemState.INCOMPLETE, stateWith(requirement, required, disposition), "for $disposition")
        }
    }

    @Test
    fun `a partial provision completes the Requirement only once some of its evidence conforms`()
    {
        val required = binding(InformationRequestRequiredness.REQUIRED)
        val requirement = requirement(required)

        evaluates(requirement, InformationRequestEvidenceRequirementState.INCOMPLETE)
        assertEquals(
            InformationRequestCompletenessItemState.COMPLETE,
            stateWith(requirement, required, InformationRequestResponseDisposition.PARTIALLY_PROVIDED),
        )
        assertEquals(
            InformationRequestCompletenessItemState.INCOMPLETE,
            stateWith(requirement, required, InformationRequestResponseDisposition.PROVIDED),
        )

        evaluates(requirement, InformationRequestEvidenceRequirementState.PENDING_ASSESSMENT)
        assertEquals(
            InformationRequestCompletenessItemState.INCOMPLETE,
            stateWith(requirement, required, InformationRequestResponseDisposition.PARTIALLY_PROVIDED),
        )
    }

    @Test
    fun `a waiver completes the Requirement only when the evidence policy accepts it`()
    {
        val required = binding(InformationRequestRequiredness.REQUIRED)
        val requirement = requirement(required)
        evaluates(requirement, InformationRequestEvidenceRequirementState.WAIVED)

        assertEquals(
            InformationRequestCompletenessItemState.COMPLETE,
            stateWith(requirement, required, InformationRequestResponseDisposition.WAIVED),
        )
    }

    @Test
    fun `an optional Requirement without evidence is optional and unanswered, and a hidden conditional one is hidden`()
    {
        val optional = binding(InformationRequestRequiredness.OPTIONAL)
        val optionalRequirement = requirement(optional)
        evaluates(optionalRequirement, InformationRequestEvidenceRequirementState.NOT_PROVIDED)
        val conditional = binding(InformationRequestRequiredness.CONDITIONAL).apply { conditionalRuleKey = "when-record-is-held" }
        val conditionalRequirement = requirement(conditional)
        evaluates(conditionalRequirement, InformationRequestEvidenceRequirementState.NOT_PROVIDED)

        val contributions = evaluator.evaluate(context(listOf(optionalRequirement, conditionalRequirement), listOf(optional, conditional)))
            .associateBy { it.requirementId }

        assertEquals(InformationRequestCompletenessItemState.OPTIONAL_UNANSWERED, contributions.getValue(optionalRequirement.id).state)
        assertEquals(InformationRequestCompletenessItemState.HIDDEN, contributions.getValue(conditionalRequirement.id).state)
        assertFalse(contributions.values.any { it.contributesToDenominator })
    }

    @Test
    fun `a Requirement without an evidence policy is left to the structured contribution`()
    {
        val required = binding(InformationRequestRequiredness.REQUIRED)
        val requirement = requirement(required)
        whenever(evaluationService.evaluate(any(), anyOrNull())).thenReturn(null)

        assertEquals(emptyList<InformationRequestCompletenessContribution>(), evaluator.evaluate(context(listOf(requirement), listOf(required))))
    }

    private fun stateWith(
        requirement: InformationRequestRequirement,
        binding: InformationRequestTemplateRequirementBinding,
        disposition: InformationRequestResponseDisposition,
    ): InformationRequestCompletenessItemState
    {
        val response = InformationRequestResponse().apply {
            informationRequestId = request.id
            informationRequestRequirementId = requirement.id
            this.disposition = disposition
        }
        return evaluator.evaluate(context(listOf(requirement), listOf(binding), listOf(response))).single().state
    }

    private fun evaluates(requirement: InformationRequestRequirement, state: InformationRequestEvidenceRequirementState)
    {
        whenever(evaluationService.evaluate(org.mockito.kotlin.eq(requirement), anyOrNull())).thenReturn(
            InformationRequestEvidenceRequirementEvaluation(state, emptyList(), emptyList(), satisfiedBySubstitute = false),
        )
    }

    private fun context(
        requirements: List<InformationRequestRequirement>,
        bindings: List<InformationRequestTemplateRequirementBinding>,
        responses: List<InformationRequestResponse> = emptyList(),
    ) = InformationRequestCompletenessProgressContext(
        request = request,
        requirements = requirements,
        bindings = bindings,
        activeResponses = responses,
        conditionEvaluations = emptyList(),
    )

    private fun binding(requiredness: InformationRequestRequiredness) = InformationRequestTemplateRequirementBinding().apply {
        templateVersionId = UUID.randomUUID()
        templateDefinitionId = UUID.randomUUID()
        templateRequirementId = UUID.randomUUID()
        templateSectionId = UUID.randomUUID()
        prompt = "Provide the supporting record"
        this.requiredness = requiredness
    }

    private fun requirement(binding: InformationRequestTemplateRequirementBinding) = InformationRequestRequirement().apply {
        informationRequestId = request.id
        sourceTemplateVersionId = binding.templateVersionId
        sourceTemplateRequirementId = binding.templateRequirementId
        sourceTemplateBindingId = binding.id
        occurrencePath = InformationRequestOccurrencePath.ROOT
    }
}
