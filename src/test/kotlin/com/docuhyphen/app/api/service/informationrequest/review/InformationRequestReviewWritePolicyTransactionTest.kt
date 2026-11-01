package com.docuhyphen.app.api.service.informationrequest.review

import com.docuhyphen.app.api.exception.StepUpRequiredException
import com.docuhyphen.app.api.migration.execute
import com.docuhyphen.app.api.migration.queryInt
import com.docuhyphen.app.api.model.command.CommandPrecondition
import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.informationrequest.RequestAccessContext
import com.docuhyphen.app.api.model.informationrequest.review.OverrideInformationRequestReviewItemCommand
import com.docuhyphen.app.api.model.informationrequest.review.RecordInformationRequestReviewCommentCommand
import com.docuhyphen.app.api.model.informationrequest.submission.RecordInformationRequestSubmissionAttestationCommand
import com.docuhyphen.app.api.model.informationrequest.submission.SubmitInformationRequestPackageCommand
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestRepository
import com.docuhyphen.app.api.repository.informationrequest.review.InformationRequestReviewRepository
import com.docuhyphen.app.api.model.auth.authz.AuthorizationContext
import com.docuhyphen.app.api.service.auth.authz.AuthorizationService
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.DocumentVersionStoragePostgreSQLResource
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.InformationRequestRuntimeTestServices
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import com.docuhyphen.app.api.service.informationrequest.model.InformationRequestRuntimeServices
import com.docuhyphen.app.api.service.informationrequest.review.model.ReviewFixture
import com.docuhyphen.app.api.service.informationrequest.review.model.WritePolicyScenario
import io.quarkus.narayana.jta.QuarkusTransaction
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

@QuarkusTest
@QuarkusTestResource(DocumentVersionStoragePostgreSQLResource::class)
class InformationRequestReviewWritePolicyTransactionTest {
    @Inject
    lateinit var dataSource: DataSource

    @Inject
    lateinit var runtime: InformationRequestRuntimeTestServices

    @Inject
    lateinit var requestRepository: InformationRequestRepository

    @Inject
    lateinit var reviewRepository: InformationRequestReviewRepository

    @Inject
    lateinit var authorizationService: AuthorizationService

    private val support by lazy {
        InformationRequestReviewTestSupport(
            dataSource,
            runtime,
            requestRepository,
            reviewRepository
        )
    }

    @Test
    fun `an administrator overrides a protected item only after verifying again`() {
        val scenario = scenario()

        assertThrows(StepUpRequiredException::class.java) {
            override(
                scenario,
                administrator(scenario, verified = false),
                "unverified-override"
            )
        }
        assertEquals(0, overrides(scenario))
        val overridden = override(scenario, administrator(scenario, verified = true), "verified-override")

        assertEquals(InformationRequestReviewState.SATISFIED, overridden.review.state)
    }

    @Test
    fun `an administrator records a finding on a protected item only after verifying again`() {
        val scenario = scenario()

        assertThrows(StepUpRequiredException::class.java) {
            finding(scenario, scenario.protectedItem, administrator(scenario, verified = false))
        }
        assertEquals(0, findings(scenario))
        finding(scenario, scenario.protectedItem, administrator(scenario, verified = true))

        assertEquals(1, findings(scenario))
    }

    @Test
    fun `an administrator cannot record a finding on an item no stage reviews`() {
        val scenario = scenario()

        val refusal = assertThrows(InformationRequestLifecycleException::class.java) {
            finding(scenario, scenario.uncoveredItem, administrator(scenario, verified = true))
        }

        assertEquals(InformationRequestErrorCatalog.REVIEW_ITEM_NOT_COVERED, refusal.reasonCode)
        assertEquals(0, findings(scenario))
    }

    @Test
    fun `administrators and reviewers comment on a protected item only after verifying again`() {
        val scenario = scenario()
        support.assign(scenario.services, scenario.fixture, scenario.review, stageKey = OVERRIDE_STAGE)

        assertThrows(StepUpRequiredException::class.java) {
            comment(
                scenario,
                administrator(scenario, verified = false),
                "unverified-administrator"
            )
        }
        assertThrows(StepUpRequiredException::class.java) {
            comment(
                scenario,
                reviewer(scenario, verified = false),
                "unverified-reviewer"
            )
        }
        assertEquals(0, comments(scenario))
        comment(scenario, administrator(scenario, verified = true), "verified-administrator")
        comment(scenario, reviewer(scenario, verified = true), "verified-reviewer")

        assertEquals(2, comments(scenario))
    }

    private fun scenario(): WritePolicyScenario {
        val fixture = support.fixture(beforePublish = { connection, configured ->
            insertReviewStage(
                connection,
                configured.template.versionId,
                OVERRIDE_STAGE,
                1,
                aggregation = "ALL",
                minimumReviewers = 1,
                overridePermitted = true
            )
            execute(
                connection,
                "UPDATE information_request_template_requirement_binding SET confidentiality_compartment_key = 'restricted-record' WHERE id = ?",
                configured.documentBindingId,
            )
        })
        dataSource.connection.use { connection -> grantShares(connection, fixture) }
        val services = runtime.build(fixture.requestId, centralAuthorization = authorizationService)
        val packageId = submit(services, fixture)
        val review = support.reviewsOf(fixture).single()
        return WritePolicyScenario(
            fixture = fixture,
            services = services,
            review = review,
            protectedItem = support.item(fixture, packageId, fixture.documentRequirementId),
            uncoveredItem = support.item(fixture, packageId, fixture.attestationRequirementId),
        )
    }

    private fun override(scenario: WritePolicyScenario, access: RequestAccessContext, key: String) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.reviewDecisions.override(
                OverrideInformationRequestReviewItemCommand(
                    requestId = scenario.fixture.requestId,
                    reviewId = scenario.review.id,
                    stageKey = OVERRIDE_STAGE,
                    submissionItemId = scenario.protectedItem,
                    outcome = InformationRequestReviewOutcome.SATISFIED,
                    narrative = "Accepted by the administrator",
                    access = access,
                    precondition = CommandPrecondition.ExpectedRevision(support.reviewETag(scenario.review.id)),
                    idempotencyKey = key,
                ),
            )
        }

    private fun finding(scenario: WritePolicyScenario, itemId: UUID, access: RequestAccessContext) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.reviewFindings.record(
                support.findingCommand(
                    scenario.fixture,
                    scenario.review,
                    itemId,
                    InformationRequestFindingCorrectionScope.NONE,
                    reviewerAccess = access
                ),
            )
        }

    private fun comment(scenario: WritePolicyScenario, access: RequestAccessContext, key: String) =
        QuarkusTransaction.requiringNew().call {
            scenario.services.reviewComments.record(
                RecordInformationRequestReviewCommentCommand(
                    requestId = scenario.fixture.requestId,
                    reviewId = scenario.review.id,
                    submissionItemId = scenario.protectedItem,
                    visibility = InformationRequestReviewVisibility.REVIEWERS_ONLY,
                    body = "Note about the restricted record",
                    access = access,
                    idempotencyKey = key,
                ),
            )
        }

    private fun submit(services: InformationRequestRuntimeServices, fixture: ReviewFixture): UUID {
        val attested = QuarkusTransaction.requiringNew().call {
            services.attestations.record(
                RecordInformationRequestSubmissionAttestationCommand(
                    requestId = fixture.requestId,
                    requirementId = fixture.attestationRequirementId,
                    decision = InformationRequestAttestationDecision.ASSENTED,
                    access = access(fixture.runtime.attestorUserId, verified = true),
                    precondition = CommandPrecondition.ExpectedRevision(support.submissionETag(fixture)),
                    idempotencyKey = "assent-${UUID.randomUUID()}",
                ),
            )
        }
        return QuarkusTransaction.requiringNew().call {
            services.submissions.submit(
                SubmitInformationRequestPackageCommand(
                    requestId = fixture.requestId,
                    access = access(fixture.runtime.contributorUserId, verified = true),
                    precondition = CommandPrecondition.ExpectedRevision(attested.submissionETag),
                    idempotencyKey = "submit-${UUID.randomUUID()}",
                ),
            )
        }.submission.submissionPackage.id
    }

    private fun grantShares(connection: Connection, fixture: ReviewFixture) {
        val runtimeFixture = fixture.runtime
        val ownerPartyId = UUID.randomUUID()
        execute(
            connection,
            """
            INSERT INTO information_request_party
                (id, information_request_id, role_key, principal_kind, principal_id, active, party_revision,
                 assigned_at, created_at, updated_at)
            VALUES (?, ?, 'DECISION_MAKER', 'USER', ?, TRUE, 1, now(), now(), now())
            """.trimIndent(),
            ownerPartyId,
            fixture.requestId,
            runtimeFixture.template.userId,
        )
        listOf(
            Triple(runtimeFixture.contributorPartyId, runtimeFixture.contributorUserId, "CONTRIBUTOR"),
            Triple(runtimeFixture.attestorPartyId, runtimeFixture.attestorUserId, "ATTESTOR"),
            Triple(fixture.reviewerPartyId, fixture.reviewerUserId, "REVIEWER"),
            Triple(ownerPartyId, runtimeFixture.template.userId, "DECISION_MAKER"),
        ).forEach { (partyId, userId, role) ->
            val shareId = UUID.randomUUID()
            execute(
                connection,
                """
                INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
                VALUES (?, 'INFORMATION_REQUEST', ?, 'USER', ?, ?, 'DIRECT', 'ACTIVE', now())
                """.trimIndent(),
                shareId,
                fixture.requestId,
                userId,
                role,
            )
            execute(connection, "UPDATE information_request_party SET share_id = ? WHERE id = ?", shareId, partyId)
        }
        execute(
            connection,
            """
            INSERT INTO share (id, resource_type, resource_id, principal_kind, principal_id, role_name, source, status, granted_at)
            VALUES (?, 'EXCHANGE', ?, 'USER', ?, 'OWNER', 'DIRECT', 'ACTIVE', now())
            """.trimIndent(),
            UUID.randomUUID(),
            runtimeFixture.exchangeId,
            runtimeFixture.template.userId,
        )
    }

    private fun overrides(scenario: WritePolicyScenario): Int = count(
        "SELECT COUNT(*) FROM information_request_review_decision WHERE review_id = ? AND kind = 'OVERRIDE'",
        scenario
    )

    private fun findings(scenario: WritePolicyScenario): Int =
        count("SELECT COUNT(*) FROM information_request_review_finding WHERE review_id = ?", scenario)

    private fun comments(scenario: WritePolicyScenario): Int =
        count("SELECT COUNT(*) FROM information_request_review_comment WHERE review_id = ?", scenario)

    private fun count(sql: String, scenario: WritePolicyScenario): Int =
        dataSource.connection.use { queryInt(it, sql, scenario.review.id) }

    private fun administrator(scenario: WritePolicyScenario, verified: Boolean) =
        access(scenario.fixture.runtime.template.userId, verified)

    private fun reviewer(scenario: WritePolicyScenario, verified: Boolean) =
        access(scenario.fixture.reviewerUserId, verified)

    private fun access(userId: UUID, verified: Boolean) =
        RequestAccessContext(
            PrincipalRef.user(userId),
            AuthorizationContext(sessionRef = "write-policy-session", mfaSatisfied = verified)
        )

    private companion object {
        const val OVERRIDE_STAGE = "override-check"
    }
}
