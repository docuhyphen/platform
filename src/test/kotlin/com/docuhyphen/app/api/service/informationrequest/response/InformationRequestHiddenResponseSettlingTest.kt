package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationProjection
import com.docuhyphen.app.api.model.informationrequest.condition.InformationRequestConditionEvaluationState
import com.docuhyphen.app.api.repository.informationrequest.template.InformationRequestTemplateRequirementBindingRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.condition.InformationRequestConditionEvaluationService
import com.docuhyphen.app.api.service.informationrequest.evidence.InformationRequestEvidenceCollectionService
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestHiddenResponseSettlingTest {
    private val conditions = mock<InformationRequestConditionEvaluationService>()
    private val bindings = mock<InformationRequestTemplateRequirementBindingRepository>()
    private val responses = mock<InformationRequestResponseStore>()
    private val authorization = mock<AuthorizationService>()
    private val locks = mock<InformationRequestSubmissionLockService>()
    private val hiddenFiles = mock<InformationRequestHiddenResponseFiles>()
    private val evidenceCollection = mock<InformationRequestEvidenceCollectionService>()
    private val enforcer = InformationRequestHiddenResponsePolicyEnforcer(
        conditions,
        bindings,
        mock(),
        responses,
        mock(),
        authorization,
        mock(),
        mock(),
        hiddenFiles,
        evidenceCollection,
        locks,
        mock(),
    )
    private val request = InformationRequest().apply {
        id = UUID.randomUUID()
        templateVersionId = UUID.randomUUID()
    }
    private val now = Timestamp.from(Instant.now())
    private val access = RequestAccessContext(PrincipalRef.user(UUID.randomUUID()), AuthorizationContext())

    init {
        whenever(responses.update(any())).thenAnswer { it.getArgument<InformationRequestResponse>(0) }
        whenever(authorization.authorize(any(), any(), any(), any())).thenReturn(Decision.Allow())
        whenever(locks.lockedRequirementIds(any(), any())).thenReturn(emptySet())
        whenever(locks.evidenceArtifactOpen(any(), any(), any())).thenReturn(true)
        whenever(hiddenFiles.active(any())).thenReturn(emptyList())
    }

    @Test
    fun `a clearing condition withdraws only the files the acting party may change`() {
        val (requirement, binding) = conditional("needs-detail")
        val answered = InformationRequestResponse().apply {
            informationRequestId = request.id
            informationRequestRequirementId = requirement.id
            occurrencePath = requirement.occurrencePath
            disposition = InformationRequestResponseDisposition.PROVIDED
            activeInResponse = true
            responseRevision = 4
        }
        val own = artifact(requirement, access.principal)
        val another = artifact(requirement, PrincipalRef.user(UUID.randomUUID()))
        whenever(responses.findAllForRequest(request.id)).thenReturn(listOf(answered))
        whenever(bindings.findOrdered(request.templateVersionId)).thenReturn(listOf(binding))
        whenever(conditions.evaluate(request.id)).thenReturn(
            listOf(evaluation("needs-detail", InformationRequestConditionEvaluationState.FALSE))
        )
        whenever(hiddenFiles.active(requirement.id)).thenReturn(listOf(own, another))
        whenever(hiddenFiles.activeChangeableBy(request, requirement.id, access)).thenReturn(listOf(own))

        val enforcement = enforcer.enforce(
            request,
            mapOf(requirement.id to requirement),
            setOf(requirement.id),
            access,
            9,
            now,
        )

        assertEquals(listOf(InformationRequestConditionEffectKind.CLEARED), enforcement.effects.map { it.effect })
        verify(evidenceCollection).withdrawForCondition(
            eq(request),
            eq(requirement),
            eq(listOf(own)),
            any(),
            eq(access),
            eq(now)
        )
        verify(evidenceCollection, never())
            .withdrawForCondition(any(), any(), argThat { another in this }, any(), any(), any())
    }

    private fun artifact(requirement: InformationRequestRequirement, provider: PrincipalRef) =
        InformationRequestEvidenceArtifact().apply {
            informationRequestId = request.id
            informationRequestRequirementId = requirement.id
            artifactKey = "file-${UUID.randomUUID()}"
            createdByPrincipalKind = provider.kind
            createdByPrincipalId = provider.id
        }

    @Test
    fun `an amendment reactivates a hidden answer whose rule keeps its key but now holds`() {
        val (requirement, binding) = conditional("needs-detail")
        val hidden = hiddenResponse(requirement, "needs-detail")
        whenever(responses.findAllForRequest(request.id)).thenReturn(listOf(hidden))
        whenever(bindings.findOrdered(request.templateVersionId)).thenReturn(listOf(binding))
        whenever(conditions.evaluate(request.id)).thenReturn(
            listOf(
                evaluation(
                    "needs-detail",
                    InformationRequestConditionEvaluationState.TRUE
                )
            )
        )

        val reconciled = enforcer.reconcileAmendedConditions(request, listOf(requirement), 9, now)

        assertEquals(listOf(hidden), reconciled)
        assertTrue(hidden.activeInResponse)
    }

    @Test
    fun `answers a reactivation brings back into play settle before any hidden answer is cleared`() {
        val (source, sourceBinding) = conditional("source-applies")
        val (dependent, dependentBinding) = conditional("dependent-applies")
        val hiddenSource = hiddenResponse(source, "source-applies")
        val hiddenDependent = hiddenResponse(dependent, "dependent-applies")
        whenever(responses.findAllForRequest(request.id)).thenReturn(listOf(hiddenSource, hiddenDependent))
        whenever(bindings.findOrdered(request.templateVersionId)).thenReturn(listOf(sourceBinding, dependentBinding))
        whenever(conditions.evaluate(request.id)).thenReturn(
            listOf(
                evaluation("source-applies", InformationRequestConditionEvaluationState.TRUE),
                evaluation("dependent-applies", InformationRequestConditionEvaluationState.FALSE),
            ),
            listOf(
                evaluation("source-applies", InformationRequestConditionEvaluationState.TRUE),
                evaluation("dependent-applies", InformationRequestConditionEvaluationState.TRUE),
            ),
        )

        val enforcement = enforcer.enforce(
            request,
            mapOf(source.id to source, dependent.id to dependent),
            emptySet(),
            access,
            9,
            now,
        )

        assertEquals(
            listOf(
                InformationRequestConditionEffectKind.REACTIVATED,
                InformationRequestConditionEffectKind.REACTIVATED
            ),
            enforcement.effects.map { it.effect },
        )
        assertTrue(hiddenDependent.activeInResponse)
        assertEquals(InformationRequestResponseDisposition.PROVIDED, hiddenDependent.disposition)
    }

    private fun conditional(ruleKey: String): Pair<InformationRequestRequirement, InformationRequestTemplateRequirementBinding> {
        val requirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = request.id
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "root"
        }
        val binding = InformationRequestTemplateRequirementBinding().apply {
            id = requirement.sourceTemplateBindingId
            templateVersionId = request.templateVersionId
            requiredness = InformationRequestRequiredness.CONDITIONAL
            conditionalRuleKey = ruleKey
        }
        return requirement to binding
    }

    private fun hiddenResponse(requirement: InformationRequestRequirement, ruleKey: String) =
        InformationRequestResponse().apply {
            informationRequestId = request.id
            informationRequestRequirementId = requirement.id
            occurrencePath = requirement.occurrencePath
            disposition = InformationRequestResponseDisposition.PROVIDED
            narrative = "Kept while hidden"
            activeInResponse = false
            hiddenByConditionRuleKey = ruleKey
            hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.RETAIN_SECURELY
            hiddenAt = now
            responseRevision = 4
        }

    private fun evaluation(ruleKey: String, state: InformationRequestConditionEvaluationState) =
        InformationRequestConditionEvaluationProjection(
            ruleKey = ruleKey,
            expressionVersion = 1,
            state = state,
            hiddenDataPolicy = InformationRequestConditionHiddenDataPolicy.CLEAR_WITH_CONFIRMATION,
            sourceRequirementKeys = emptySet(),
            fieldDefinitionIds = emptySet(),
        )
}
