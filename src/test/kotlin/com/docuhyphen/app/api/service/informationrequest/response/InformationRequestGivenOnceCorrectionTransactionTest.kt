package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.auth.authz.Decision
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.evidence.ChangeInformationRequestEvidenceStateCommand
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.evidence.InformationRequestEvidenceArtifactRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.ResourceRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.model.GivenOnceCorrectionScenario
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewTestSupport
import com.docuhyphen.app.api.service.informationrequest.review.reviewAttestation
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestGivenOnceCorrectionTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var artifactRepository: InformationRequestEvidenceArtifactRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    private val reviews by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `a correction that returns only another answer does not reopen a file given once that a later submission hid`() {
        val scenario = scenario(documentMode = "PROVIDE_ONCE", hiddenDataPolicy = "RETAIN_SECURELY")
        answer(scenario, DOCUMENT_APPLIES, "show-document")
        withdraw(scenario, reviews.submitWhole(scenario.services, scenario.fixture))
        answer(scenario, "no longer applies", "hide-document")
        val hidingPackage = reviews.submitWhole(scenario.services, scenario.fixture)
        returnOnly(scenario, hidingPackage, scenario.answers.requirementId)
        answer(scenario, DOCUMENT_APPLIES, "show-document-again")

        val refusal = assertThrows(InformationRequestItemRefusalException::class.java) {
            withdrawFile(
                scenario,
                "withdraw-fixed-file"
            )
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertEquals(scenario.fixture.documentRequirementId, refusal.problems.single().requirementId)
        assertEquals("ACTIVE", artifactState(scenario))
    }

    @Test
    fun `under the central authorizer a correction that returns only another answer neither permits nor performs withdrawing a file given once`() {
        val scenario = scenario(documentMode = "PROVIDE_ONCE", hiddenDataPolicy = "RETAIN_SECURELY", central = true)
        answer(scenario, DOCUMENT_APPLIES, "show-document")
        withdraw(scenario, reviews.submitWhole(scenario.services, scenario.fixture))
        answer(scenario, "no longer applies", "hide-document")
        val hidingPackage = reviews.submitWhole(scenario.services, scenario.fixture)
        returnOnly(scenario, hidingPackage, scenario.answers.requirementId)
        answer(scenario, DOCUMENT_APPLIES, "show-document-again")
        val contributor = reviews.contributor(scenario.fixture)

        val refusal = assertThrows(InformationRequestItemRefusalException::class.java) {
            withdrawFile(
                scenario,
                "withdraw-fixed-file"
            )
        }
        val decision = QuarkusTransaction.requiringNew().call {
            authorizationService.authorize(
                contributor.principal,
                Action.INFORMATION_REQUEST_EVIDENCE_WITHDRAW,
                ResourceRef.informationRequestRequirement(scenario.fixture.documentRequirementId),
                contributor.authorization,
            )
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertTrue(decision is Decision.Deny, "$decision")
        assertEquals("ACTIVE", artifactState(scenario))
    }

    @Test
    fun `a file whose answer may change can be withdrawn under the same correction`() {
        val scenario = scenario(documentMode = "PROVIDE", hiddenDataPolicy = "RETAIN_SECURELY")
        answer(scenario, DOCUMENT_APPLIES, "show-document")
        withdraw(scenario, reviews.submitWhole(scenario.services, scenario.fixture))
        answer(scenario, "no longer applies", "hide-document")
        val hidingPackage = reviews.submitWhole(scenario.services, scenario.fixture)
        returnOnly(scenario, hidingPackage, scenario.answers.requirementId)
        answer(scenario, DOCUMENT_APPLIES, "show-document-again")

        withdrawFile(scenario, "withdraw-file")

        assertEquals("WITHDRAWN", artifactState(scenario))
    }

    @Test
    fun `a file of a document never submitted while shown stays open under a correction`() {
        val scenario = scenario(documentMode = "PROVIDE_ONCE", hiddenDataPolicy = "RETAIN_SECURELY")
        val hiddenPackage = reviews.submitWhole(scenario.services, scenario.fixture)
        returnOnly(scenario, hiddenPackage, scenario.answers.requirementId)
        answer(scenario, DOCUMENT_APPLIES, "show-document")

        withdrawFile(scenario, "withdraw-file")

        assertEquals("WITHDRAWN", artifactState(scenario))
    }

    @Test
    fun `a correction that returns only the controlling answer cannot clear a document given once`() {
        val scenario = scenario(documentMode = "PROVIDE_ONCE", hiddenDataPolicy = "CLEAR_WITH_CONFIRMATION")
        answer(scenario, DOCUMENT_APPLIES, "show-document")
        val submitted = reviews.submitWhole(scenario.services, scenario.fixture)
        returnOnly(scenario, submitted, scenario.answers.requirementId)

        val refusal = assertThrows(InformationRequestItemRefusalException::class.java) {
            answer(scenario, "no longer applies", "hide-document", setOf(scenario.fixture.documentRequirementId))
        }

        assertEquals(InformationRequestErrorCatalog.CORRECTION_SCOPE_DENIED, refusal.reasonCode)
        assertEquals(scenario.fixture.documentRequirementId, refusal.problems.single().requirementId)
        assertEquals("ACTIVE", artifactState(scenario))
        assertEquals(DOCUMENT_APPLIES, answerOf(scenario))
    }

    @Test
    fun `an answer given once that a correction returns can change`() {
        val scenario = scenario(fieldMode = "PROVIDE_ONCE")
        answer(scenario, "first answer", "first-answer")
        val submitted = reviews.submitWhole(scenario.services, scenario.fixture)
        returnOnly(scenario, submitted, scenario.answers.requirementId)

        answer(scenario, "corrected answer", "corrected-answer")

        assertEquals("corrected answer", answerOf(scenario))
    }

    @Test
    fun `a confirmation given once keeps its decision under a correction that does not return it`() {
        val scenario = scenario(attestationMode = "PROVIDE_ONCE")
        val submitted = reviews.submitWhole(scenario.services, scenario.fixture)
        returnOnly(scenario, submitted, scenario.fixture.documentRequirementId)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            attest(scenario, InformationRequestAttestationDecision.REFUSED, "refuse-under-correction")
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertEquals(
            InformationRequestAttestationDecision.ASSENTED,
            attest(
                scenario,
                InformationRequestAttestationDecision.ASSENTED,
                "assent-under-correction"
            ).attestation.decision,
        )
    }

    @Test
    fun `a confirmation given once that a correction returns can change its decision`() {
        val scenario = scenario(attestationMode = "PROVIDE_ONCE", reviewConfirmation = true)
        val submitted = reviews.submitWhole(scenario.services, scenario.fixture)
        returnOnly(scenario, submitted, scenario.fixture.attestationRequirementId)

        val refused = attest(scenario, InformationRequestAttestationDecision.REFUSED, "refuse-returned-confirmation")

        assertEquals(InformationRequestAttestationDecision.REFUSED, refused.attestation.decision)
    }

    private fun scenario(
        fieldMode: String = "PROVIDE",
        documentMode: String = "PROVIDE",
        attestationMode: String = "PROVIDE",
        hiddenDataPolicy: String? = null,
        reviewConfirmation: Boolean = false,
        central: Boolean = false,
    ): GivenOnceCorrectionScenario {
        lateinit var answers: FieldAnswerSqlFixture
        lateinit var setup: Connection
        val ownerPartyId = UUID.randomUUID()
        val fixture = reviews.fixture(
            beforePublish = { connection, configured ->
                setup = connection
                answers = FieldAnswerSqlFixture(connection, configured.template)
                setResponseModes(
                    connection,
                    mapOf(
                        answers.bindingId to fieldMode,
                        configured.documentBindingId to documentMode,
                        configured.attestationBindingId to attestationMode,
                    ),
                )
                execute(
                    connection,
                    "UPDATE information_request_template_requirement_binding SET review_policy = 'REQUIRED' WHERE id = ?",
                    answers.bindingId,
                )
                hiddenDataPolicy?.let { policy -> conditionDocumentOnAnswer(connection, configured, answers, policy) }
                if (reviewConfirmation) reviewAttestation(connection, configured)
            },
            prepare = { configured ->
                answers.materialize(configured)
                execute(
                    setup,
                    "UPDATE schema_field_binding SET visibility = 'PUBLIC' WHERE id = ?",
                    answers.schemaFieldBindingId
                )
                grantRespondentShares(setup, configured)
                if (central) configured.insertPartyForUser(ownerPartyId, "DECISION_MAKER", configured.template.userId)
            },
        )
        if (!central) return GivenOnceCorrectionScenario(
            fixture,
            answers,
            runtime.build(fixture.requestId, denies = reviews.roleDenials(fixture))
        )
        dataSource.connection.use { connection ->
            grantPartyShare(connection, fixture.requestId, fixture.reviewerPartyId, fixture.reviewerUserId, "REVIEWER")
            grantPartyShare(
                connection,
                fixture.requestId,
                ownerPartyId,
                fixture.runtime.template.userId,
                "DECISION_MAKER"
            )
        }
        return GivenOnceCorrectionScenario(
            fixture,
            answers,
            runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        )
    }

    private fun answer(
        scenario: GivenOnceCorrectionScenario,
        value: String,
        key: String,
        confirmedClears: Set<UUID> = emptySet(),
    ) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = scenario.fixture.requestId,
                    access = reviews.contributor(scenario.fixture),
                    precondition = CommandPrecondition.ExpectedRevision(reviews.responseETag(scenario.fixture)),
                    idempotencyKey = key,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = scenario.answers.requirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                            fieldValues = ResponseFieldValuesPatch(
                                entries = listOf(
                                    FieldValueEntry(
                                        scenario.answers.fieldContractId,
                                        JsonPrimitive(value)
                                    )
                                ),
                                precondition = FieldsPrecondition.Unconditioned,
                            ),
                        ),
                    ),
                    confirmedHiddenResponseClears = confirmedClears,
                ),
            )
        }

    private fun withdraw(scenario: GivenOnceCorrectionScenario, packageId: UUID) {
        QuarkusTransaction.requiringNew().run {
            scenario.services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = scenario.fixture.requestId,
                    packageId = packageId,
                    reasonCode = "answer-revisited",
                    access = reviews.contributor(scenario.fixture),
                    precondition = CommandPrecondition.ExpectedRevision(reviews.responseETag(scenario.fixture)),
                    idempotencyKey = "withdraw-${UUID.randomUUID()}",
                ),
            )
        }
    }

    private fun returnOnly(scenario: GivenOnceCorrectionScenario, packageId: UUID, requirementId: UUID) {
        val review = reviews.reviewsOf(scenario.fixture).single { it.packageId == packageId }
        val assigned = reviews.assign(scenario.services, scenario.fixture, review)
        val returned = reviews.item(scenario.fixture, packageId, requirementId)
        reviews.finding(
            scenario.services,
            scenario.fixture,
            review,
            returned,
            InformationRequestFindingCorrectionScope.RESPONSE
        )
        val covered = QuarkusTransaction.requiringNew().call {
            runtime.reviewLoader.snapshot(requireNotNull(reviewRepository.findById(review.id)))
                .coverage[requireNotNull(assigned.assignment).stageKey].orEmpty()
        }
        reviews.record(
            scenario.services,
            scenario.fixture,
            assigned,
            reviews.draft(
                scenario.services,
                scenario.fixture,
                assigned,
                outcomes = covered.associateWith {
                    if (it == returned) InformationRequestReviewOutcome.CHANGES_REQUIRED else InformationRequestReviewOutcome.SATISFIED
                },
            ),
            "return-${UUID.randomUUID()}",
        )
    }

    private fun withdrawFile(scenario: GivenOnceCorrectionScenario, key: String) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.evidence.withdraw(
                ChangeInformationRequestEvidenceStateCommand(
                    requestId = scenario.fixture.requestId,
                    requirementId = scenario.fixture.documentRequirementId,
                    artifactId = scenario.fixture.artifactId,
                    access = reviews.contributor(scenario.fixture),
                    precondition = CommandPrecondition.ExpectedRevision(
                        QuarkusTransaction.requiringNew().call {
                            InformationRequestETag.artifactOf(requireNotNull(artifactRepository.findById(scenario.fixture.artifactId)))
                        },
                    ),
                    idempotencyKey = key,
                    reason = null,
                ),
            )
        }

    private fun attest(
        scenario: GivenOnceCorrectionScenario,
        decision: InformationRequestAttestationDecision,
        key: String,
    ) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = scenario.fixture.requestId,
                    requirementId = scenario.fixture.attestationRequirementId,
                    decision = decision,
                    refusalReason = if (decision == InformationRequestAttestationDecision.REFUSED) "The record no longer matches" else null,
                    access = reviews.attestor(scenario.fixture),
                    precondition = CommandPrecondition.ExpectedRevision(reviews.submissionETag(scenario.fixture)),
                    idempotencyKey = key,
                ),
            )
        }

    private fun artifactState(scenario: GivenOnceCorrectionScenario): String? =
        dataSource.connection.use {
            queryString(
                it,
                "SELECT collection_state FROM information_request_evidence_artifact WHERE id = ?",
                scenario.fixture.artifactId
            )
        }

    private fun answerOf(scenario: GivenOnceCorrectionScenario): String? =
        dataSource.connection.use {
            queryString(it, "SELECT text_value FROM field_value WHERE id = ?", scenario.answers.fieldValueId)
        }
}
