package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.model.dto.InformationRequestTemplateVersionDto
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactConfidence
import com.docuhyphen.app.api.model.entity.InformationRequestAcceptedFactVisibility
import com.docuhyphen.app.api.model.entity.InformationRequestBusinessDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAggregation
import com.docuhyphen.app.api.model.entity.InformationRequestReviewAssignmentState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewDecisionKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewKind
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.entity.InformationRequestReviewStageOrdering
import com.docuhyphen.app.api.model.entity.InformationRequestReviewState
import com.docuhyphen.app.api.model.entity.InformationRequestReviewTieResolution
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewAssignmentFact
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewDecisionFact
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewItemStanding
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewStageInput
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewStagePlan
import com.docuhyphen.app.api.model.informationrequest.InformationRequestReviewStageState
import com.docuhyphen.app.api.model.informationrequest.PromoteInformationRequestAcceptedFactCommand
import com.docuhyphen.app.api.model.informationrequest.RecordInformationRequestBusinessDecisionCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionItemRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestCorrectionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestReviewRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateDefinitionRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionCapabilityRepository
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestTemplateVersionRepository
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestTemplateWalkingSkeletonPhase8Test
{
    @Inject lateinit var dataSource: DataSource
    @Inject lateinit var definitionRepository: InformationRequestTemplateDefinitionRepository
    @Inject lateinit var versionRepository: InformationRequestTemplateVersionRepository
    @Inject lateinit var capabilityRepository: InformationRequestTemplateVersionCapabilityRepository
    @Inject lateinit var configurationWriter: InformationRequestTemplateConfigurationWriter
    @Inject lateinit var projectionLoader: InformationRequestTemplateProjectionLoader
    @Inject lateinit var capabilityGate: InformationRequestTemplateCapabilityGate
    @Inject lateinit var runtime: InformationRequestRuntimeTestServices
    @Inject lateinit var requestRepository: InformationRequestRepository
    @Inject lateinit var reviewRepository: InformationRequestReviewRepository
    @Inject lateinit var correctionRepository: InformationRequestCorrectionRepository
    @Inject lateinit var correctionItemRepository: InformationRequestCorrectionItemRepository

    private val store by lazy {
        InformationRequestTemplateWalkingSkeletonStore(dataSource, definitionRepository, versionRepository, capabilityRepository, configurationWriter)
    }
    private val support by lazy { InformationRequestReviewTestSupport(dataSource, runtime, requestRepository, reviewRepository) }

    @Test
    fun `the staged stress fixture reviews its content by quorum before its confirmations by consensus and is issuable`()
    {
        val stress = publishStress()
        val projected = projection(stress.versionId)

        assertEquals(InformationRequestReviewStageOrdering.SEQUENTIAL, projected.reviewStageOrdering)
        assertEquals(listOf("content-review", "confirmation-review"), projected.reviewStages.map { it.stageKey })
        val content = projected.reviewStages.first()
        assertEquals(InformationRequestReviewAggregation.QUORUM, content.aggregation)
        assertEquals(2, content.quorumCount)
        assertEquals(3, content.minimumReviewerCount)
        assertTrue(content.excludesResponseParties)
        assertEquals(listOf("participant-responses", "evidence-package"), content.sectionKeys)
        val confirmation = projected.reviewStages.last()
        assertEquals(InformationRequestReviewAggregation.CONSENSUS, confirmation.aggregation)
        assertEquals(InformationRequestReviewTieResolution.REQUIRE_OVERRIDE, confirmation.tieResolution)
        assertTrue(confirmation.overridePermitted)
        assertTrue(confirmation.excludesPriorReviewers)
        assertEquals(listOf("response-confirmations"), confirmation.sectionKeys)
        assertEquals("subject-status.reuse", projected.factReusePurposeKey)
        assertEquals(emptyList<InformationRequestCapabilityRequirement>(), capabilityGate.unservedRequirements(stress.versionId))
    }

    @Test
    fun `the stress fixture returns only the item its content quorum rejects and a tied confirmation waits for an override`()
    {
        val plan = stagePlans(projection(publishStress().versionId))
        val subject = UUID.randomUUID()
        val evidence = UUID.randomUUID()
        val confirmation = UUID.randomUUID()
        val inputs = listOf(
            InformationRequestReviewStageInput(plan.getValue("content-review"), listOf(subject, evidence)),
            InformationRequestReviewStageInput(plan.getValue("confirmation-review"), listOf(confirmation)),
        )
        val items = listOf(subject, evidence, confirmation)
        val content = List(3) { assignment("content-review") }
        val confirmations = List(2) { assignment("confirmation-review") }

        val understaffed = aggregate(inputs, items, content.take(2), emptyList())
        assertEquals(InformationRequestReviewItemStanding.UNDERSTAFFED, understaffed.stage("content-review")!!.items.first().standing)
        assertNull(understaffed.settledState)

        val returned = aggregate(
            inputs,
            items,
            content + confirmations,
            listOf(
                decision(subject, content[0], InformationRequestReviewOutcome.SATISFIED, 1),
                decision(subject, content[1], InformationRequestReviewOutcome.SATISFIED, 2),
                decision(evidence, content[0], InformationRequestReviewOutcome.CHANGES_REQUIRED, 3),
                decision(evidence, content[1], InformationRequestReviewOutcome.CHANGES_REQUIRED, 4),
            ),
        )
        assertEquals(InformationRequestReviewStageState.SETTLED, returned.stage("content-review")!!.state)
        assertEquals(InformationRequestReviewStageState.WAITING, returned.stage("confirmation-review")!!.state)
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.settledState)
        assertEquals(
            listOf(evidence),
            returned.itemOutcomes.filterValues { it == InformationRequestReviewOutcome.CHANGES_REQUIRED }.keys.toList(),
        )

        val resubmitted = listOf(
            InformationRequestReviewDecisionFact(subject, "content-review", null, InformationRequestReviewDecisionKind.CARRIED, InformationRequestReviewOutcome.SATISFIED, 1),
            decision(evidence, content[0], InformationRequestReviewOutcome.SATISFIED, 2),
            decision(evidence, content[2], InformationRequestReviewOutcome.SATISFIED, 3),
            decision(confirmation, confirmations[0], InformationRequestReviewOutcome.SATISFIED, 4),
            decision(confirmation, confirmations[1], InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION, 5),
        )
        val tied = aggregate(inputs, items, content + confirmations, resubmitted)
        assertEquals(InformationRequestReviewStageState.SETTLED, tied.stage("content-review")!!.state)
        assertEquals(InformationRequestReviewItemStanding.TIED, tied.stage("confirmation-review")!!.items.single().standing)
        assertNull(tied.settledState)

        val overridden = aggregate(
            inputs,
            items,
            content + confirmations,
            resubmitted + InformationRequestReviewDecisionFact(
                confirmation,
                "confirmation-review",
                null,
                InformationRequestReviewDecisionKind.OVERRIDE,
                InformationRequestReviewOutcome.SATISFIED_WITH_EXCEPTION,
                6,
            ),
        )
        assertEquals(InformationRequestReviewState.SATISFIED_WITH_EXCEPTION, overridden.settledState)
    }

    @Test
    fun `the basic fixture routes nothing to a reviewer and names the purpose its answers are reused for`()
    {
        val basic = publishBasic()
        val projected = projection(basic.versionId)

        assertEquals(emptyList<String>(), projected.reviewStages.map { it.stageKey })
        assertEquals("recorded-summary.reuse", projected.factReusePurposeKey)
        assertEquals(emptyList<InformationRequestCapabilityRequirement>(), capabilityGate.unservedRequirements(basic.versionId))
        assertTrue(
            QuarkusTransaction.requiringNew().call { capabilityRepository.findForVersion(basic.versionId) }
                .none { it.capabilityKey == InformationRequestCapability.RESPONSE_REVIEW },
        )
    }

    @Test
    fun `a basic-shaped request corrects only its returned answer, closes once satisfied, and keeps its reviewed fact apart from the Business Decision`()
    {
        lateinit var answers: FieldAnswerSqlFixture
        val fixture = support.fixture(
            beforePublish = { connection, runtime ->
                answers = FieldAnswerSqlFixture(connection, runtime.template, reviewed = true, reusePurpose = BASIC_REUSE_PURPOSE)
            },
            prepare = { runtime ->
                answers.materialize(runtime)
                runtime.insertSubject(UUID.randomUUID(), UUID.randomUUID())
            },
        )
        val services = runtime.build(fixture.requestId, denies = support.roleDenials(fixture))

        val firstPackage = support.submitWhole(services, fixture)
        val review = support.reviewsOf(fixture).single()
        val assigned = support.assign(services, fixture, review)
        val answerItem = support.item(fixture, firstPackage, answers.requirementId)
        support.finding(services, fixture, review, answerItem, InformationRequestFindingCorrectionScope.RESPONSE)
        val returned = support.record(
            services,
            fixture,
            assigned,
            support.draft(
                services,
                fixture,
                assigned,
                outcomes = mapOf(
                    answerItem to InformationRequestReviewOutcome.CHANGES_REQUIRED,
                    support.item(fixture, firstPackage, fixture.documentRequirementId) to InformationRequestReviewOutcome.SATISFIED,
                ),
            ),
            "return-answer",
        )
        assertEquals(InformationRequestReviewState.CHANGES_REQUESTED, returned.review.state)
        QuarkusTransaction.requiringNew().run {
            val correction = correctionRepository.findForRequest(fixture.requestId).single()
            assertEquals(listOf(answers.requirementId), correctionItemRepository.findForCorrections(listOf(correction.id)).map { it.requirementId })
        }
        val excluded = assertThrows(InformationRequestLifecycleException::class.java) {
            support.patchNarrative(services, fixture, fixture.documentRequirementId, "Not returned")
        }
        assertEquals(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, excluded.reasonCode)
        support.patchNarrative(services, fixture, answers.requirementId, "The corrected summary")

        val secondPackage = support.submitWhole(services, fixture)
        val retest = support.reviewsOf(fixture).last()
        assertEquals(InformationRequestReviewKind.RESUBMISSION, retest.kind)
        val reassigned = support.assign(services, fixture, retest)
        val satisfied = support.record(
            services,
            fixture,
            reassigned,
            support.draft(services, fixture, reassigned, InformationRequestReviewOutcome.SATISFIED),
            "accept-answer",
        )
        assertEquals(InformationRequestReviewState.SATISFIED, satisfied.review.state)
        assertEquals(InformationRequestState.CLOSED, satisfied.request.state)
        assertTrue(QuarkusTransaction.requiringNew().call { services.businessDecisions.decisions(fixture.requestId, support.owner(fixture)) }.isEmpty())

        val fact = QuarkusTransaction.requiringNew().call {
            services.acceptedFacts.promote(
                PromoteInformationRequestAcceptedFactCommand(
                    requestId = fixture.requestId,
                    packageId = secondPackage,
                    submissionItemId = support.item(fixture, secondPackage, answers.requirementId),
                    purposeKey = BASIC_REUSE_PURPOSE,
                    policyBasisKey = "policy.reuse",
                    visibility = InformationRequestAcceptedFactVisibility.RESPONDING_PARTIES,
                    access = support.owner(fixture),
                    idempotencyKey = "promote-reviewed-answer",
                ),
            )
        }.fact
        assertEquals(InformationRequestAcceptedFactConfidence.REVIEWED, fact.confidence)
        assertEquals(retest.id, fact.sourceReviewId)
        assertEquals(secondPackage, fact.sourcePackageId)

        val closed = QuarkusTransaction.requiringNew().call { requireNotNull(requestRepository.findById(fixture.requestId)) }
        val decision = QuarkusTransaction.requiringNew().call {
            services.businessDecisions.record(
                RecordInformationRequestBusinessDecisionCommand(
                    requestId = fixture.requestId,
                    owningProcessKey = "process.outcome",
                    outcomeCode = "outcome.accepted",
                    kind = InformationRequestBusinessDecisionKind.ORIGINAL,
                    decidedAt = Instant.now().minusSeconds(60),
                    access = support.owner(fixture),
                    idempotencyKey = "decide-after-satisfaction",
                ),
            )
        }.decision
        assertEquals(1, decision.decisionRevision)
        QuarkusTransaction.requiringNew().run {
            val after = requireNotNull(requestRepository.findById(fixture.requestId))
            assertEquals(InformationRequestState.CLOSED, after.state)
            assertEquals(closed.satisfiedAt, after.satisfiedAt)
            assertEquals(secondPackage, after.satisfiedByPackageId)
        }
    }

    private fun publishBasic(): WalkingSkeletonDraft
    {
        val field = store.insertTextField("basic-recorded-summary")
        val actorId = store.insertOwner()
        val fixture = InformationRequestTemplateWalkingSkeletonFixtures.basicFieldDocumentResponseAttestationRequest(
            store.insertSchemaVersion(actorId, listOf(field)),
            field.fieldDefinitionId,
        )
        return store.insertDraft(actorId, fixture.templateKey).also { store.publish(it, fixture.configuration) }
    }

    private fun publishStress(): WalkingSkeletonDraft
    {
        val subjectField = store.insertTextField("stress-subject-status")
        val delegateField = store.insertTextField("stress-delegate-note")
        val actorId = store.insertOwner()
        val fixture = InformationRequestTemplateWalkingSkeletonFixtures.multiPartyStagedEvidenceRequest(
            store.insertSchemaVersion(actorId, listOf(subjectField, delegateField)),
            subjectField.fieldDefinitionId,
            delegateField.fieldDefinitionId,
        )
        return store.insertDraft(actorId, fixture.templateKey).also { store.publish(it, fixture.configuration) }
    }

    private fun projection(versionId: UUID): InformationRequestTemplateVersionDto =
        QuarkusTransaction.requiringNew().call { projectionLoader.loadVersion(versionRepository.findById(versionId)!!) }

    private fun stagePlans(projected: InformationRequestTemplateVersionDto): Map<String, InformationRequestReviewStagePlan>
    {
        val sectionIds = projected.sections.associate { it.sectionKey to it.id }
        return projected.reviewStages.mapIndexed { index, stage ->
            stage.stageKey to InformationRequestReviewStagePlan(
                id = stage.id,
                stageKey = stage.stageKey,
                position = index + 1,
                title = stage.title,
                aggregation = stage.aggregation,
                quorumCount = stage.quorumCount,
                minimumReviewerCount = stage.minimumReviewerCount,
                tieResolution = stage.tieResolution,
                overridePermitted = stage.overridePermitted,
                excludesResponseParties = stage.excludesResponseParties,
                excludesPriorReviewers = stage.excludesPriorReviewers,
                coveredSectionIds = stage.sectionKeys.map(sectionIds::getValue).toSet(),
            )
        }.toMap()
    }

    private fun aggregate(
        inputs: List<InformationRequestReviewStageInput>,
        items: List<UUID>,
        assignments: List<InformationRequestReviewAssignmentFact>,
        decisions: List<InformationRequestReviewDecisionFact>,
    ) = InformationRequestReviewAggregator.evaluate(InformationRequestReviewStageOrdering.SEQUENTIAL, inputs, items, assignments, decisions)

    private fun assignment(stageKey: String) =
        InformationRequestReviewAssignmentFact(UUID.randomUUID(), stageKey, InformationRequestReviewAssignmentState.ACTIVE)

    private fun decision(
        itemId: UUID,
        assignment: InformationRequestReviewAssignmentFact,
        outcome: InformationRequestReviewOutcome,
        sequence: Int,
    ) = InformationRequestReviewDecisionFact(itemId, assignment.stageKey, assignment.id, InformationRequestReviewDecisionKind.REVIEWER, outcome, sequence)

    private companion object
    {
        const val BASIC_REUSE_PURPOSE = "recorded-summary.reuse"
    }
}
