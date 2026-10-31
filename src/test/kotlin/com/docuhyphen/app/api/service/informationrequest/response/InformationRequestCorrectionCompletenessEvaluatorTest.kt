package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessContribution
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessProgressContext
import com.docuhyphen.app.api.model.informationrequest.submission.InformationRequestReturnedItem
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestCorrectionCompletenessEvaluatorTest {
    @Test
    fun `a returned answer not revised since it was submitted is rejected and counts in the denominator only`() {
        val fixture = Fixture()
        val returned = fixture.fieldRequirement(responseRevision = 3)
        fixture.returned(returned, InformationRequestRequirementType.FIELD, submittedRevision = 3)

        val contributions = fixture.evaluate()

        assertEquals(
            listOf(
                InformationRequestCompletenessContribution(
                    itemKey = "correction:${fixture.request.id}:${returned.id}",
                    requirementId = returned.id,
                    occurrencePath = returned.occurrencePath,
                    state = InformationRequestCompletenessItemState.REJECTED,
                    contributesToDenominator = true,
                    contributesToNumerator = false,
                ),
            ),
            contributions,
        )
    }

    @Test
    fun `a returned answer revised after its submission is no longer rejected`() {
        val fixture = Fixture()
        val revised = fixture.fieldRequirement(responseRevision = 4)
        fixture.returned(revised, InformationRequestRequirementType.FIELD, submittedRevision = 3)

        assertTrue(fixture.evaluate().isEmpty())
    }

    @Test
    fun `a returned Requirement that had no answer at submission stays rejected until one is given`() {
        val fixture = Fixture()
        val unanswered = fixture.fieldRequirement(responseRevision = null)
        fixture.returned(unanswered, InformationRequestRequirementType.FIELD, submittedRevision = null)

        assertEquals(
            listOf(InformationRequestCompletenessItemState.REJECTED),
            fixture.evaluate().map { it.state },
        )
    }

    @Test
    fun `a returned document or attestation is judged by its own evidence rather than by the response revision`() {
        val fixture = Fixture()
        val document = fixture.fieldRequirement(responseRevision = 2)
        val attestation = fixture.fieldRequirement(responseRevision = 2)
        fixture.returned(document, InformationRequestRequirementType.DOCUMENT, submittedRevision = 2)
        fixture.returned(attestation, InformationRequestRequirementType.RESPONSE_ATTESTATION, submittedRevision = 2)

        assertTrue(fixture.evaluate().isEmpty())
    }

    @Test
    fun `a returned Requirement its condition now hides is not rejected`() {
        val fixture = Fixture()
        val hidden = fixture.fieldRequirement(
            responseRevision = 3,
            requiredness = InformationRequestRequiredness.CONDITIONAL,
            ruleKey = "when-detail-applies",
        )
        fixture.returned(hidden, InformationRequestRequirementType.FIELD, submittedRevision = 3)
        fixture.conditions += InformationRequestConditionEvaluationProjection(
            ruleKey = "when-detail-applies",
            expressionVersion = 1,
            state = InformationRequestConditionEvaluationState.FALSE,
            sourceRequirementKeys = emptySet(),
            fieldDefinitionIds = emptySet(),
        )

        assertTrue(fixture.evaluate().isEmpty())
    }

    private class Fixture {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
        }
        val requirements = mutableListOf<InformationRequestRequirement>()
        val bindings = mutableListOf<InformationRequestTemplateRequirementBinding>()
        val responses = mutableListOf<InformationRequestResponse>()
        val conditions = mutableListOf<InformationRequestConditionEvaluationProjection>()
        private val returnedItems = mutableListOf<InformationRequestReturnedItem>()
        private val lockService = mock<InformationRequestSubmissionLockService>()
        private val evaluator = InformationRequestCorrectionCompletenessEvaluator(lockService)

        init {
            whenever(lockService.returnedItems(request.id)).thenAnswer { returnedItems.toList() }
        }

        fun fieldRequirement(
            responseRevision: Long?,
            requiredness: InformationRequestRequiredness = InformationRequestRequiredness.REQUIRED,
            ruleKey: String? = null,
        ): InformationRequestRequirement {
            val requirement = InformationRequestRequirement().apply {
                id = UUID.randomUUID()
                informationRequestId = request.id
                sourceTemplateVersionId = request.templateVersionId
                sourceTemplateRequirementId = UUID.randomUUID()
                sourceTemplateBindingId = UUID.randomUUID()
                occurrencePath = "root"
            }
            requirements += requirement
            bindings += InformationRequestTemplateRequirementBinding().apply {
                id = requirement.sourceTemplateBindingId
                templateVersionId = request.templateVersionId
                templateDefinitionId = UUID.randomUUID()
                templateRequirementId = requirement.sourceTemplateRequirementId
                templateSectionId = UUID.randomUUID()
                prompt = "Provide process information"
                this.requiredness = requiredness
                conditionalRuleKey = ruleKey
                collectedFieldDefinitionId = UUID.randomUUID()
            }
            responseRevision?.let { revision ->
                responses += InformationRequestResponse().apply {
                    informationRequestId = request.id
                    informationRequestRequirementId = requirement.id
                    requirementRevisionId = UUID.randomUUID()
                    occurrencePath = requirement.occurrencePath
                    disposition = InformationRequestResponseDisposition.PROVIDED
                    this.responseRevision = revision
                    recordedByPrincipalKind = PrincipalKind.PARTICIPANT
                    recordedByPrincipalId = UUID.randomUUID()
                }
            }
            return requirement
        }

        fun returned(
            requirement: InformationRequestRequirement,
            type: InformationRequestRequirementType,
            submittedRevision: Long?,
        ) {
            returnedItems += InformationRequestReturnedItem(requirement.id, type, submittedRevision)
        }

        fun evaluate(): List<InformationRequestCompletenessContribution> =
            evaluator.evaluate(
                InformationRequestCompletenessProgressContext(
                    request = request,
                    requirements = requirements,
                    bindings = bindings,
                    activeResponses = responses,
                    conditionEvaluations = conditions,
                ),
            )
    }
}
