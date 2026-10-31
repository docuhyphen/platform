package com.docuhyphen.app.api.service.informationrequest.submission

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.*
import com.docuhyphen.app.api.model.informationrequest.submission.*
import com.docuhyphen.app.api.service.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.informationrequest.InformationRequestMutationGate
import com.docuhyphen.app.api.service.informationrequest.attestation.InformationRequestAttestationEvaluationService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestCompletenessProgressService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidationContextLoader
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidationService
import com.docuhyphen.app.api.service.informationrequest.response.InformationRequestStructuredResponseValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestSubmissionReadinessEvaluatorTest {
    @Test
    fun `a submission validation issue blocks the package as a problem on the Requirement it names`() {
        val fixture = Fixture(
            setOf(InformationRequestStructuredResponseValidationStage.SUBMISSION),
        ) { requirement ->
            listOf(
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.PERIOD_COVERAGE,
                    message = "The covered periods leave a gap",
                    requirementId = requirement.id,
                    occurrencePath = requirement.occurrencePath,
                ),
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.CROSS_ROW,
                    message = "An entry outside this package conflicts",
                    requirementId = UUID.randomUUID(),
                ),
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.CROSS_ROW,
                    message = "The entries do not add up",
                ),
            )
        }

        val readiness = fixture.assess().readiness

        assertEquals(
            listOf(InformationRequestSubmissionProblemCode.STRUCTURED_VALIDATION_FAILED),
            readiness.problems.map { it.code })
        assertEquals(fixture.requirement.id, readiness.problems.single().requirementId)
        assertEquals("entry-label", readiness.problems.single().requirementKey)
        assertEquals("entries[0]", readiness.problems.single().occurrencePath)
        assertEquals(1, readiness.undisclosedProblemCount)
        verify(fixture.loader).load(
            eq(fixture.request),
            eq(InformationRequestStructuredResponseValidationStage.SUBMISSION),
            any()
        )
    }

    @Test
    fun `no validation state is loaded when no validator checks submissions`() {
        val fixture = Fixture(setOf(InformationRequestStructuredResponseValidationStage.RESPONSE_SAVE)) { requirement ->
            listOf(
                InformationRequestStructuredResponseValidationIssue(
                    kind = InformationRequestStructuredResponseValidationKind.DUPLICATE,
                    message = "Saved entries repeat",
                    requirementId = requirement.id,
                ),
            )
        }

        val readiness = fixture.assess().readiness

        assertTrue(readiness.problems.isEmpty())
        assertEquals(0, readiness.undisclosedProblemCount)
        verify(fixture.loader, never()).load(any(), any(), any())
    }

    @Test
    fun `a group below its minimum blocks submission even when every existing item is complete`() {
        val shortfall = InformationRequestSubmissionGroupProblem(
            groupKey = "entries",
            parentOccurrencePath = null,
            minOccurrences = 2,
            maxOccurrences = 4,
            occurrenceCount = 1,
        )
        val fixture = Fixture(
            emptySet(),
            groupProblems = InformationRequestSubmissionGroupProblems(listOf(shortfall), undisclosedCount = 1),
        ) { emptyList() }

        val readiness = fixture.assess().readiness

        assertFalse(readiness.ready)
        assertTrue(readiness.problems.isEmpty())
        assertEquals(listOf(shortfall), readiness.groupProblems)
        assertEquals(1, readiness.undisclosedProblemCount)
    }

    @Test
    fun `an undisclosed group shortfall alone still blocks submission`() {
        val fixture = Fixture(
            emptySet(),
            groupProblems = InformationRequestSubmissionGroupProblems(undisclosedCount = 1),
        ) { emptyList() }

        val readiness = fixture.assess().readiness

        assertFalse(readiness.ready)
        assertTrue(readiness.groupProblems.isEmpty())
        assertEquals(1, readiness.undisclosedProblemCount)
    }

    private class Fixture(
        validatedStages: Set<InformationRequestStructuredResponseValidationStage>,
        groupProblems: InformationRequestSubmissionGroupProblems = InformationRequestSubmissionGroupProblems(),
        issues: (InformationRequestRequirement) -> List<InformationRequestStructuredResponseValidationIssue>,
    ) {
        val request = InformationRequest().apply {
            id = UUID.randomUUID()
            templateVersionId = UUID.randomUUID()
        }
        val requirement = InformationRequestRequirement().apply {
            id = UUID.randomUUID()
            informationRequestId = request.id
            sourceTemplateVersionId = request.templateVersionId
            sourceTemplateRequirementId = UUID.randomUUID()
            sourceTemplateBindingId = UUID.randomUUID()
            occurrencePath = "entries[0]"
        }
        val access = RequestAccessContext(PrincipalRef.participant(UUID.randomUUID()), AuthorizationContext())
        val loader = mock<InformationRequestStructuredResponseValidationContextLoader>()
        private val progress = mock<InformationRequestCompletenessProgressService>()
        private val attestations = mock<InformationRequestAttestationEvaluationService>()
        private val gate = mock<InformationRequestMutationGate>()
        private val groupCardinality = mock<InformationRequestSubmissionGroupCardinalityEvaluator> {
            on { assess(any(), any()) } doReturn groupProblems
        }
        private val validator = object : InformationRequestStructuredResponseValidator {
            override val stages = validatedStages

            override fun validate(context: InformationRequestStructuredResponseValidationContext) = issues(requirement)
        }
        private val evaluator = InformationRequestSubmissionReadinessEvaluator(
            progress,
            attestations,
            gate,
            InformationRequestStructuredResponseValidationService(listOf(validator)),
            loader,
            groupCardinality,
        )
        private val content = InformationRequestSubmissionContent(
            request = request,
            version = InformationRequestTemplateVersion().apply {
                id = request.templateVersionId
                templateDefinitionId = UUID.randomUUID()
            },
            stageKey = null,
            stageOrder = emptyList(),
            items = listOf(
                InformationRequestSubmissionContentItem(
                    requirement = requirement,
                    revision = InformationRequestRequirementRevision(),
                    binding = InformationRequestTemplateRequirementBinding(),
                    requirementKey = "entry-label",
                    requirementType = InformationRequestRequirementType.FIELD,
                    response = null,
                    fieldValueRevisionId = null,
                    evidence = null,
                ),
            ),
            links = emptyList(),
            contentHash = "content-hash",
        )

        init {
            whenever(progress.evaluate(request.id)).thenReturn(
                InformationRequestProgressProjection(
                    completedCount = 1,
                    totalCount = 1,
                    percentComplete = 100,
                    items = listOf(
                        InformationRequestCompletenessContribution(
                            itemKey = "structured:${request.id}:${requirement.id}",
                            requirementId = requirement.id,
                            occurrencePath = requirement.occurrencePath,
                            state = InformationRequestCompletenessItemState.COMPLETE,
                            contributesToDenominator = true,
                            contributesToNumerator = true,
                        ),
                    ),
                ),
            )
            whenever(attestations.evaluate(content)).thenReturn(emptyMap())
            whenever(gate.permitsRequirement(any(), any(), any())).thenReturn(true)
            whenever(loader.load(any(), any(), any())).thenAnswer {
                InformationRequestStructuredResponseValidationContext(
                    stage = InformationRequestStructuredResponseValidationStage.SUBMISSION,
                    request = request,
                    requirementsById = mapOf(requirement.id to requirement),
                )
            }
        }

        fun assess() = evaluator.assess(content, access)
    }
}
