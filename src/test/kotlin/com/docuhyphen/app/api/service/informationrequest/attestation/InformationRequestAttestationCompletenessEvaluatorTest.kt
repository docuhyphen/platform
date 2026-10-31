package com.docuhyphen.app.api.service.informationrequest.attestation

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationEvaluation
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationPolicy
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationRequirementEvaluation
import com.docuhyphen.app.api.model.informationrequest.attestation.InformationRequestAttestationState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessItemState
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestCompletenessProgressContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InformationRequestAttestationCompletenessEvaluatorTest {
    @Test
    fun `a partial response never substitutes for an assent`() {
        assertEquals(
            InformationRequestCompletenessItemState.INCOMPLETE,
            state(InformationRequestResponseDisposition.PARTIALLY_PROVIDED, "Only part is confirmed"),
        )
    }

    @Test
    fun `an exception needs an explanation and a plain provided response still needs assent`() {
        assertEquals(
            InformationRequestCompletenessItemState.INCOMPLETE,
            state(InformationRequestResponseDisposition.NOT_APPLICABLE, null)
        )
        assertEquals(
            InformationRequestCompletenessItemState.INCOMPLETE,
            state(InformationRequestResponseDisposition.UNAVAILABLE, "   ")
        )
        assertEquals(
            InformationRequestCompletenessItemState.INCOMPLETE,
            state(InformationRequestResponseDisposition.PROVIDED, "Ready")
        )
        assertEquals(
            InformationRequestCompletenessItemState.COMPLETE,
            state(InformationRequestResponseDisposition.NOT_APPLICABLE, "No matching item")
        )
        assertEquals(
            InformationRequestCompletenessItemState.COMPLETE,
            state(InformationRequestResponseDisposition.EXCEPTION_REQUESTED, "An exception is requested")
        )
    }

    private fun state(
        disposition: InformationRequestResponseDisposition,
        narrative: String?
    ): InformationRequestCompletenessItemState {
        val request = InformationRequest()
        val binding = InformationRequestTemplateRequirementBinding().apply {
            requiredness = InformationRequestRequiredness.REQUIRED
        }
        val requirement = InformationRequestRequirement().apply {
            informationRequestId = request.id
            sourceTemplateBindingId = binding.id
            occurrencePath = "root"
        }
        val response = InformationRequestResponse().apply {
            informationRequestRequirementId = requirement.id
            this.disposition = disposition
            this.narrative = narrative
        }
        val policy = InformationRequestAttestationPolicy(
            binding.id,
            listOf(InformationRequestContributorRole.ATTESTOR),
            InformationRequestAttestationOrdering.ANY_ORDER,
            1,
            InformationRequestAuthenticationStrength.VERIFIED_CONTACT,
            null,
            InformationRequestExternalSignatureReferencePolicy.NOT_ACCEPTED,
            "a".repeat(64),
        )
        val evaluations = mock<InformationRequestAttestationEvaluationService>()
        whenever(evaluations.evaluate(request)).thenReturn(
            mapOf(
                requirement.id to InformationRequestAttestationRequirementEvaluation(
                    requirement.id, binding.id, null, "b".repeat(64), policy,
                    InformationRequestAttestationEvaluation(
                        InformationRequestAttestationState.PENDING, emptyList(), emptyList(),
                        listOf(InformationRequestContributorRole.ATTESTOR), 0, 1, 0,
                    ),
                )
            ),
        )
        val context = InformationRequestCompletenessProgressContext(
            request, listOf(requirement), listOf(binding), listOf(response), emptyList(),
        )
        return InformationRequestAttestationCompletenessEvaluator(evaluations).evaluate(context).single().state
    }
}
