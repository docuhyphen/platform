package com.docuhyphen.app.api.service.informationrequest.response

import com.docuhyphen.app.api.migration.FieldAnswerSqlFixture
import com.docuhyphen.app.api.migration.SubmissionRuntimeSqlFixture
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryString
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.InformationRequestAttestationDecision
import com.docuhyphen.app.api.model.entity.InformationRequestFindingCorrectionScope
import com.docuhyphen.app.api.model.entity.InformationRequestResponseDisposition
import com.docuhyphen.app.api.model.entity.InformationRequestReviewOutcome
import com.docuhyphen.app.api.model.fields.FieldValueEntry
import com.docuhyphen.app.api.model.fields.FieldsPrecondition
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.response.InformationRequestResponsePatch
import com.docuhyphen.app.api.model.informationrequest.response.PatchInformationRequestResponsesCommand
import com.docuhyphen.app.api.model.informationrequest.response.ResponseFieldValuesPatch
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.model.informationrequest.submission.WithdrawInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestETag
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestItemRefusalException
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.response.model.ProvideOnceScenario
import com.docuhyphen.app.api.service.informationrequest.review.InformationRequestReviewTestSupport
import com.docuhyphen.app.api.service.informationrequest.submission.InformationRequestSubmissionLockService
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestProvideOnceTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var lockService: InformationRequestSubmissionLockService

    private val reviews by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `an answer given once can be edited until it is submitted and then stays fixed after the submission is withdrawn`() {
        val scenario = scenario(fieldMode = "PROVIDE_ONCE")
        answer(scenario, "edited before submission", "edit-before-submission")
        withdraw(scenario, submit(scenario), "withdraw")

        val refusal = assertThrows(InformationRequestItemRefusalException::class.java) {
            answer(scenario, "changed after withdrawal", "change-after-withdrawal")
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertEquals(scenario.answers.requirementId, refusal.problems.single().requirementId)
        assertEquals("edited before submission", answerOf(scenario))
    }

    @Test
    fun `a withdrawn submission names its answer given once, which the amendment guard keeps from changing meaning`() {
        val scenario = scenario(fieldMode = "PROVIDE_ONCE")
        answer(scenario, "edited before submission", "edit-before-submission")
        val beforeSubmission = QuarkusTransaction.requiringNew()
            .call { lockService.givenOnceRequirementIds(scenario.runtime.requestId) }
        withdraw(scenario, submit(scenario), "withdraw")

        val afterWithdrawal = QuarkusTransaction.requiringNew()
            .call { lockService.givenOnceRequirementIds(scenario.runtime.requestId) }

        assertEquals(emptySet<UUID>(), beforeSubmission)
        assertEquals(setOf(scenario.answers.requirementId), afterWithdrawal)
    }

    @Test
    fun `an answer the party may change can change again once its submission is withdrawn`() {
        val scenario = scenario(fieldMode = "PROVIDE")
        withdraw(scenario, submit(scenario), "withdraw")

        answer(scenario, "changed after withdrawal", "change-after-withdrawal")

        assertEquals("changed after withdrawal", answerOf(scenario))
    }

    @Test
    fun `a confirmation given once keeps its decision after its submission is withdrawn`() {
        val scenario = scenario(attestationMode = "PROVIDE_ONCE")
        withdraw(scenario, submit(scenario), "withdraw")

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            attest(scenario, InformationRequestAttestationDecision.REFUSED, "refuse-after-withdrawal")
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertEquals(
            InformationRequestAttestationDecision.ASSENTED,
            attest(scenario, InformationRequestAttestationDecision.ASSENTED, "assent-again").attestation.decision,
        )
    }

    @Test
    fun `a confirmation given once can change its decision until it is first submitted`() {
        val scenario = scenario(attestationMode = "PROVIDE_ONCE")

        attest(scenario, InformationRequestAttestationDecision.REFUSED, "refuse-before-submission")
        val assented = attest(scenario, InformationRequestAttestationDecision.ASSENTED, "assent-before-submission")

        assertEquals(InformationRequestAttestationDecision.ASSENTED, assented.attestation.decision)
    }

    @Test
    fun `a confirmation given once cannot change while its submission stands`() {
        val scenario = scenario(attestationMode = "PROVIDE_ONCE")
        submit(scenario)

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            attest(scenario, InformationRequestAttestationDecision.REFUSED, "refuse-while-submitted")
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
    }

    @Test
    fun `a confirmation given once can be given again for content returned for correction`() {
        val fixture = reviews.fixture(beforePublish = { connection, configured ->
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET response_mode = 'PROVIDE_ONCE' WHERE id = ?",
                configured.attestationBindingId,
            )
        })
        val services = runtime.build(fixture.requestId, denies = reviews.roleDenials(fixture))
        val packageId = reviews.submitWhole(services, fixture)
        val review = reviews.reviewsOf(fixture).single()
        val assigned = reviews.assign(services, fixture, review)
        reviews.finding(
            services, fixture, review, reviews.item(fixture, packageId, fixture.documentRequirementId),
            InformationRequestFindingCorrectionScope.RESPONSE,
        )
        reviews.record(
            services,
            fixture,
            assigned,
            reviews.draft(services, fixture, assigned, InformationRequestReviewOutcome.CHANGES_REQUIRED),
            "return-for-correction",
        )

        val reconfirmed = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = reviews.attestor(fixture),
                    precondition = CommandPrecondition.ExpectedRevision(reviews.submissionETag(fixture)),
                    idempotencyKey = "reconfirm-corrected-content",
                ),
            )
        }

        assertEquals(InformationRequestAttestationDecision.ASSENTED, reconfirmed.attestation.decision)
    }

    @Test
    fun `a confirmation the party may change can be refused once its submission is withdrawn`() {
        val scenario = scenario(attestationMode = "PROVIDE")
        withdraw(scenario, submit(scenario), "withdraw")

        val refused = attest(scenario, InformationRequestAttestationDecision.REFUSED, "refuse-after-withdrawal")

        assertEquals(InformationRequestAttestationDecision.REFUSED, refused.attestation.decision)
    }

    @Test
    fun `a save that would clear a document given once is refused after its submission is withdrawn`() {
        val scenario = scenario(documentMode = "PROVIDE_ONCE", hiddenDataPolicy = "CLEAR_WITH_CONFIRMATION")
        answer(scenario, DOCUMENT_APPLIES, "show-document")
        withdraw(scenario, submit(scenario), "withdraw")

        val refusal = assertThrows(InformationRequestItemRefusalException::class.java) {
            answer(scenario, "no longer applies", "hide-document", setOf(scenario.runtime.documentRequirementId))
        }

        assertEquals(InformationRequestErrorCatalog.SUBMISSION_LOCKED, refusal.reasonCode)
        assertEquals(scenario.runtime.documentRequirementId, refusal.problems.single().requirementId)
        assertEquals("ACTIVE", artifactState(scenario))
        assertEquals(DOCUMENT_APPLIES, answerOf(scenario))
    }

    @Test
    fun `a save may hide a document given once when its files are kept`() {
        val scenario = scenario(documentMode = "PROVIDE_ONCE", hiddenDataPolicy = "RETAIN_SECURELY")
        answer(scenario, DOCUMENT_APPLIES, "show-document")
        withdraw(scenario, submit(scenario), "withdraw")

        answer(scenario, "no longer applies", "hide-document")

        assertEquals("no longer applies", answerOf(scenario))
        assertEquals("ACTIVE", artifactState(scenario))
    }

    @Test
    fun `a document given once that was hidden when it was submitted can still be answered once it applies`() {
        val scenario = scenario(documentMode = "PROVIDE_ONCE", hiddenDataPolicy = "RETAIN_SECURELY")
        withdraw(scenario, submit(scenario), "withdraw")
        answer(scenario, DOCUMENT_APPLIES, "show-document")

        val provided = provideDocument(scenario, "provide-document")

        assertEquals(
            InformationRequestResponseDisposition.PROVIDED,
            provided.responses.single { it.informationRequestRequirementId == scenario.runtime.documentRequirementId }.disposition,
        )
    }

    private fun scenario(
        fieldMode: String = "PROVIDE",
        attestationMode: String = "PROVIDE",
        documentMode: String = "PROVIDE",
        hiddenDataPolicy: String? = null,
    ): ProvideOnceScenario {
        lateinit var answers: FieldAnswerSqlFixture
        val fixture = dataSource.connection.use { connection ->
            SubmissionRuntimeSqlFixture(connection, reviewed = true, beforePublish = { configured ->
                answers = FieldAnswerSqlFixture(connection, configured.template)
                setResponseModes(
                    connection,
                    mapOf(
                        answers.bindingId to fieldMode,
                        configured.attestationBindingId to attestationMode,
                        configured.documentBindingId to documentMode,
                    ),
                )
                hiddenDataPolicy?.let { policy -> conditionDocumentOnAnswer(connection, configured, answers, policy) }
            }).also { configured ->
                answers.materialize(configured)
                execute(
                    connection,
                    "UPDATE schema_field_binding SET visibility = 'PUBLIC' WHERE id = ?",
                    answers.schemaFieldBindingId
                )
                grantRespondentShares(connection, configured)
            }
        }
        return ProvideOnceScenario(
            fixture,
            answers,
            runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        )
    }

    private fun answer(
        scenario: ProvideOnceScenario,
        value: String,
        key: String,
        confirmedClears: Set<UUID> = emptySet()
    ) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = scenario.runtime.requestId,
                    access = access(scenario.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(scenario)),
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

    private fun provideDocument(scenario: ProvideOnceScenario, key: String) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.responses.patch(
                PatchInformationRequestResponsesCommand(
                    requestId = scenario.runtime.requestId,
                    access = access(scenario.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(scenario)),
                    idempotencyKey = key,
                    patches = listOf(
                        InformationRequestResponsePatch(
                            requirementId = scenario.runtime.documentRequirementId,
                            disposition = InformationRequestResponseDisposition.PROVIDED,
                        ),
                    ),
                ),
            )
        }

    private fun attest(scenario: ProvideOnceScenario, decision: InformationRequestAttestationDecision, key: String) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = scenario.runtime.requestId,
                    requirementId = scenario.runtime.attestationRequirementId,
                    decision = decision,
                    refusalReason = if (decision == InformationRequestAttestationDecision.REFUSED) "The record no longer matches" else null,
                    access = access(scenario.runtime.attestorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(submissionETag(scenario)),
                    idempotencyKey = key,
                ),
            )
        }

    private fun submit(scenario: ProvideOnceScenario): UUID {
        val attested =
            attest(scenario, InformationRequestAttestationDecision.ASSENTED, "provide-once-assent-${UUID.randomUUID()}")
        return QuarkusTransaction.requiringNew().call {
            scenario.services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = scenario.runtime.requestId,
                    access = access(scenario.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "provide-once-submit-${UUID.randomUUID()}",
                ),
            )
        }.submission.submissionPackage.id
    }

    private fun withdraw(scenario: ProvideOnceScenario, packageId: UUID, key: String) {
        QuarkusTransaction.requiringNew().run {
            scenario.services.submissions.withdraw(
                WithdrawInformationRequestPackageCommand(
                    requestId = scenario.runtime.requestId,
                    packageId = packageId,
                    reasonCode = "answer-revisited",
                    access = access(scenario.runtime.contributorUserId),
                    precondition = CommandPrecondition.ExpectedRevision(responseETag(scenario)),
                    idempotencyKey = key,
                ),
            )
        }
    }

    private fun responseETag(scenario: ProvideOnceScenario): String =
        QuarkusTransaction.requiringNew().call {
            InformationRequestETag.responsesOf(requireNotNull(requestRepository.findById(scenario.runtime.requestId)))
        }

    private fun submissionETag(scenario: ProvideOnceScenario): String =
        QuarkusTransaction.requiringNew().call {
            val request = requireNotNull(requestRepository.findById(scenario.runtime.requestId))
            InformationRequestETag.submissionOf(null, runtime.contentCollector.collect(request, null).contentHash)
        }

    private fun answerOf(scenario: ProvideOnceScenario): String? =
        dataSource.connection.use {
            queryString(it, "SELECT text_value FROM field_value WHERE id = ?", scenario.answers.fieldValueId)
        }

    private fun artifactState(scenario: ProvideOnceScenario): String? =
        dataSource.connection.use {
            queryString(
                it,
                "SELECT collection_state FROM information_request_evidence_artifact WHERE id = ?",
                scenario.runtime.artifactId
            )
        }

    private fun access(userId: UUID) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "provide-once-session", mfaSatisfied = true)
        )
}
